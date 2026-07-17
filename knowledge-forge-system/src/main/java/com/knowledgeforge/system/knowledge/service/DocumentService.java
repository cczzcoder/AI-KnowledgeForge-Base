package com.knowledgeforge.system.knowledge.service;

import com.knowledgeforge.core.entity.Document;
import com.knowledgeforge.core.entity.DocumentChunk;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.exception.DocumentProcessException;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.document.dto.ParsedDocument;
import com.knowledgeforge.system.document.service.ChunkDescriptor;
import com.knowledgeforge.system.document.service.ChunkingStrategyFactory;
import com.knowledgeforge.system.document.service.DocumentParserFactory;
import com.knowledgeforge.system.graph.service.GraphService;
import com.knowledgeforge.system.utils.FileTypeUtil;
import com.knowledgeforge.system.knowledge.dto.DocumentChunkDTO;
import com.knowledgeforge.system.knowledge.dto.DocumentDTO;
import com.knowledgeforge.system.knowledge.repository.DocumentChunkRepository;
import com.knowledgeforge.system.knowledge.repository.DocumentRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParserFactory parserFactory;
    private final ChunkingStrategyFactory chunkingFactory;
    private final GraphService graphService;
    private final PgVectorStore vectorStore;
    private final MinioClient minioClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentDTO uploadDocument(UUID kbId, MultipartFile file) {
        knowledgeBaseService.getById(kbId);

        if (file.getSize() > SystemConstants.MAX_FILE_SIZE_MB * 1024L * 1024L) {
            throw new DocumentProcessException("文件大小超过限制（最大 " + SystemConstants.MAX_FILE_SIZE_MB + "MB）");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new DocumentProcessException("文件名为空");
        }

        Document doc = Document.builder()
                .kbId(kbId)
                .title(fileName)
                .fileType(detectFileType(fileName))
                .fileSize(file.getSize())
                .status("PROCESSING")
                .build();
        doc = documentRepository.save(doc);

        try {
            byte[] fileBytes = file.getBytes();
            if (fileBytes.length == 0) {
                throw new DocumentProcessException("文件内容为空");
            }

            // 上传到 MinIO（同步，流式写入避免二次拷贝）
            String filePath = kbId + "/" + doc.getId() + "/" + fileName;
            try (InputStream minioStream = new java.io.ByteArrayInputStream(fileBytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(filePath)
                        .stream(minioStream, fileBytes.length, -1)
                        .contentType(file.getContentType())
                        .build());
            }
            doc.setFilePath(filePath);
            doc.setUpdatedAt(java.time.LocalDateTime.now());
            documentRepository.save(doc);

            // 异步处理：直接复用 fileBytes，避免 clone() 二次拷贝
            processDocumentAsync(doc, fileBytes, fileName);

            return toDTO(doc);
        } catch (DocumentProcessException e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            return toDTO(doc);
        } catch (Exception e) {
            log.error("文档上传失败: {}", fileName, e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            return toDTO(doc);
        }
    }

    /**
     * 异步处理文档：解析、分块、向量化、图谱构建。
     * 在独立线程中执行，不阻塞上传响应。
     */
    @Async("docProcessExecutor")
    @Transactional
    public void processDocumentAsync(Document doc, byte[] fileBytes, String fileName) {
        try {
            try (InputStream parseStream = new java.io.ByteArrayInputStream(fileBytes)) {
                processAndIndexDocument(doc, parseStream, fileName);
            }

            doc.setStatus("READY");
            doc.setUpdatedAt(java.time.LocalDateTime.now());
            documentRepository.save(doc);
            log.info("文档 [{}] 异步处理完成", fileName);
        } catch (DocumentProcessException e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            log.warn("文档 [{}] 异步处理失败: {}", fileName, e.getMessage());
        } catch (Exception e) {
            log.error("文档 [{}] 异步处理异常", fileName, e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
        }
    }

    public void processAndIndexDocument(Document doc, InputStream inputStream, String fileName) {
        ParsedDocument parsed = parserFactory.parse(inputStream, fileName);
        if (parsed.getContent() == null || parsed.getContent().isBlank()) {
            throw new DocumentProcessException("文档内容为空");
        }
        if (parsed.getTitle() != null && !parsed.getTitle().isBlank()) {
            doc.setTitle(parsed.getTitle());
        }

        List<ChunkDescriptor> chunks = chunkingFactory.chunk(parsed.getContent(), doc.getFileType());
        log.info("文档 [{}] 自适应分块完成，共 {} 个块", fileName, chunks.size());

        List<DocumentChunk> chunkEntities = new ArrayList<>();
        List<Integer> parentRefs = new ArrayList<>();
        for (ChunkDescriptor descriptor : chunks) {
            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(doc.getId())
                    .content(descriptor.getContent())
                    .chunkIndex(descriptor.getChunkIndex())
                    .tokenCount(descriptor.getTokenCount() != null ? descriptor.getTokenCount() : estimateTokens(descriptor.getContent()))
                    .chunkType(descriptor.getChunkType())
                    .sectionTitle(descriptor.getSectionTitle())
                    .sectionPath(descriptor.getSectionPath())
                    .startOffset(descriptor.getStartOffset())
                    .endOffset(descriptor.getEndOffset())
                    .strategyVersion(descriptor.getStrategyVersion())
                    .embeddingReady(false)
                    .build();
            chunkEntities.add(chunk);
            parentRefs.add(descriptor.getParentRef());
        }
        chunkEntities = chunkRepository.saveAll(chunkEntities);
        for (int i = 0; i < chunkEntities.size(); i++) {
            Integer parentRef = parentRefs.get(i);
            if (parentRef != null && parentRef >= 0 && parentRef < chunkEntities.size()) {
                chunkEntities.get(i).setParentChunkId(chunkEntities.get(parentRef).getId());
            }
        }
        chunkEntities = chunkRepository.saveAll(chunkEntities);
        doc.setChunkCount(chunkEntities.size());

        try {
            List<org.springframework.ai.document.Document> springDocs = new ArrayList<>();
            for (DocumentChunk chunk : chunkEntities) {
                org.springframework.ai.document.Document springDoc =
                        new org.springframework.ai.document.Document(chunk.getContent());
                springDoc.getMetadata().put("chunk_id", chunk.getId().toString());
                springDoc.getMetadata().put("document_id", doc.getId().toString());
                springDoc.getMetadata().put("kb_id", doc.getKbId().toString());
                springDoc.getMetadata().put("chunk_index", chunk.getChunkIndex());
                if (chunk.getParentChunkId() != null) {
                    springDoc.getMetadata().put("parent_chunk_id", chunk.getParentChunkId().toString());
                }
                if (chunk.getSectionTitle() != null) {
                    springDoc.getMetadata().put("section_title", chunk.getSectionTitle());
                }
                if (chunk.getSectionPath() != null) {
                    springDoc.getMetadata().put("section_path", chunk.getSectionPath());
                }
                if (chunk.getChunkType() != null) {
                    springDoc.getMetadata().put("chunk_type", chunk.getChunkType());
                }
                if (chunk.getStrategyVersion() != null) {
                    springDoc.getMetadata().put("strategy_version", chunk.getStrategyVersion());
                }
                if (chunk.getStartOffset() != null) {
                    springDoc.getMetadata().put("start_offset", chunk.getStartOffset());
                }
                if (chunk.getEndOffset() != null) {
                    springDoc.getMetadata().put("end_offset", chunk.getEndOffset());
                }
                springDocs.add(springDoc);
            }
            vectorStore.add(springDocs);

            for (DocumentChunk chunk : chunkEntities) {
                chunk.setEmbeddingReady(true);
            }
            chunkRepository.saveAll(chunkEntities);
            log.info("文档 [{}] 向量化完成，已写入 {} 条向量", fileName, chunkEntities.size());

            try {
                graphService.buildGraphFromChunks(doc.getKbId(), doc.getId(),
                        chunks.stream()
                                .map(descriptor -> descriptor.getContent() == null ? "" : descriptor.getContent())
                                .toList());
                log.info("文档 [{}] 知识图谱构建完成", fileName);
            } catch (Exception e) {
                log.warn("文档 [{}] 知识图谱构建失败（不影响正常流程）: {}", fileName, e.getMessage());
            }
        } catch (Exception e) {
            log.error("向量化失败: {}", fileName, e);
            for (DocumentChunk chunk : chunkEntities) {
                chunk.setStatus("SYNC_FAILED");
                chunk.setErrorMessage("向量化失败: " + e.getMessage());
            }
            chunkRepository.saveAll(chunkEntities);
            throw new DocumentProcessException("向量化失败: " + e.getMessage(), e);
        }
    }

    public List<String> splitText(String text) {
        int maxLen = 512;
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxLen) {
            chunks.add(text);
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end < text.length()) {
                int cutPoint = findCutPoint(text, end, 50);
                if (cutPoint > start) {
                    end = cutPoint;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private int findCutPoint(String text, int target, int searchRange) {
        int searchStart = Math.max(target - searchRange, 0);
        for (int i = target; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？') {
                return i + 1;
            }
        }
        return target;
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 1.5d);
    }

    private String detectFileType(String fileName) {
        return FileTypeUtil.detectByExtension(fileName);
    }

    public PageResult<DocumentDTO> listDocuments(UUID kbId, int page, int size) {
        var result = documentRepository.findByKbIdAndDeletedFalse(kbId, PageRequest.of(page, size));
        List<DocumentDTO> items = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotalElements(), page, size);
    }

    public DocumentDTO getDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        return toDTO(doc);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        doc.setDeleted(true);
        doc.setUpdatedAt(java.time.LocalDateTime.now());
        documentRepository.save(doc);

        int deleted = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'document_id' = ?",
                id.toString());
        log.info("文档 [{}] 已删除，清理了 {} 条向量数据", id, deleted);
    }

    @Transactional
    public DocumentDTO reprocessDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new IllegalArgumentException("文档文件路径为空，无法重新处理");
        }

        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'document_id' = ?",
                id.toString());
        chunkRepository.deleteByDocumentId(id);

        doc.setStatus("PROCESSING");
        doc.setChunkCount(0);
        doc.setErrorMessage(null);
        doc.setUpdatedAt(java.time.LocalDateTime.now());
        documentRepository.save(doc);

        try (InputStream fileStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(doc.getFilePath())
                .build())) {
            byte[] fileBytes = fileStream.readAllBytes();
            try (InputStream parseStream = new java.io.ByteArrayInputStream(fileBytes)) {
                processAndIndexDocument(doc, parseStream,
                        doc.getFilePath().substring(doc.getFilePath().lastIndexOf('/') + 1));
            }
            doc.setStatus("READY");
            doc.setUpdatedAt(java.time.LocalDateTime.now());
            documentRepository.save(doc);
            return toDTO(doc);
        } catch (DocumentProcessException e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            return toDTO(doc);
        } catch (Exception e) {
            log.error("文档重新处理失败: {}", id, e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            return toDTO(doc);
        }
    }

    public List<DocumentChunkDTO> getChunks(UUID documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(this::toChunkDTO)
                .collect(Collectors.toList());
    }

    public InputStream getDocumentFile(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new IllegalArgumentException("文档文件路径为空");
        }
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(doc.getFilePath())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("获取文件失败: " + e.getMessage(), e);
        }
    }

    private DocumentDTO toDTO(Document doc) {
        return DocumentDTO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .title(doc.getTitle())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private DocumentChunkDTO toChunkDTO(DocumentChunk chunk) {
        return DocumentChunkDTO.builder()
                .id(chunk.getId())
                .documentId(chunk.getDocumentId())
                .content(chunk.getContent().length() > 200
                        ? chunk.getContent().substring(0, 200) + "..."
                        : chunk.getContent())
                .chunkIndex(chunk.getChunkIndex())
                .tokenCount(chunk.getTokenCount())
                .chunkType(chunk.getChunkType())
                .sectionTitle(chunk.getSectionTitle())
                .sectionPath(chunk.getSectionPath())
                .startOffset(chunk.getStartOffset())
                .endOffset(chunk.getEndOffset())
                .strategyVersion(chunk.getStrategyVersion())
                .embeddingReady(chunk.getEmbeddingReady())
                .build();
    }
}