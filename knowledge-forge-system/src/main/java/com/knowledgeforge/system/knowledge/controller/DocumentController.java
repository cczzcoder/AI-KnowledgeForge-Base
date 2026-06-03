package com.knowledgeforge.system.knowledge.controller;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.knowledge.dto.DocumentChunkDTO;
import com.knowledgeforge.system.knowledge.dto.DocumentDTO;
import com.knowledgeforge.system.knowledge.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1)
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/knowledge-bases/{kbId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentDTO> upload(
            @PathVariable UUID kbId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(documentService.uploadDocument(kbId, file));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<PageResult<DocumentDTO>> listDocuments(
            @PathVariable UUID kbId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(documentService.listDocuments(kbId, page, Math.min(size, SystemConstants.MAX_PAGE_SIZE)));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentDTO> getDocument(@PathVariable UUID id) {
        return ApiResponse.success(documentService.getDocument(id));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/documents/{id}/reprocess")
    public ApiResponse<DocumentDTO> reprocessDocument(@PathVariable UUID id) {
        return ApiResponse.success(documentService.reprocessDocument(id));
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<DocumentChunkDTO>> getChunks(@PathVariable UUID id) {
        return ApiResponse.success(documentService.getChunks(id));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable UUID id) {
        DocumentDTO doc = documentService.getDocument(id);
        InputStream stream = documentService.getDocumentFile(id);
        String encodedFileName = URLEncoder.encode(doc.getTitle(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }
}