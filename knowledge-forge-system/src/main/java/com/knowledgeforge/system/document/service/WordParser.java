package com.knowledgeforge.system.document.service;

import com.knowledgeforge.system.document.dto.ParsedDocument;
import com.knowledgeforge.core.shared.exception.DocumentProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class WordParser {

    public ParsedDocument parse(InputStream inputStream, String fileName) {
        byte[] bytes = readAllBytes(inputStream);
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".docx")) {
            return parseDocx(bytes, fileName);
        } else if (lowerName.endsWith(".doc")) {
            return parseDoc(bytes, fileName);
        }
        return parseDocx(bytes, fileName);
    }

    private byte[] readAllBytes(InputStream inputStream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new DocumentProcessException("读取文件流失败: " + e.getMessage(), e);
        }
    }

    private ParsedDocument parseDocx(byte[] bytes, String fileName) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder content = new StringBuilder();

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    content.append(text).append("\n");
                }
            }

            String text = content.toString();
            if (text.isBlank()) {
                throw new DocumentProcessException("Word 文档无法提取文本内容");
            }

            return ParsedDocument.builder()
                    .title(fileName.replaceFirst("\\.docx?$", ""))
                    .content(text.trim())
                    .fileType("WORD")
                    .pageCount(1)
                    .build();
        } catch (Exception e) {
            log.warn("OOXML 解析失败，尝试降级为纯文本: {} - {}", fileName, e.getMessage());
            return parseAsPlainText(bytes, fileName);
        }
    }

    private ParsedDocument parseDoc(byte[] bytes, String fileName) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();

            if (text == null || text.isBlank()) {
                throw new DocumentProcessException("Word .doc 文档无法提取文本内容");
            }

            return ParsedDocument.builder()
                    .title(fileName.replaceFirst("\\.doc$", ""))
                    .content(text.trim())
                    .fileType("WORD")
                    .pageCount(1)
                    .build();
        } catch (Exception e) {
            log.warn("旧版 .doc 解析失败，尝试降级为纯文本: {} - {}", fileName, e.getMessage());
            return parseAsPlainText(bytes, fileName);
        }
    }

    private ParsedDocument parseAsPlainText(byte[] bytes, String fileName) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new DocumentProcessException("文件无法解析，请确认文件格式正确");
        }
        log.info("文件 [{}] 已降级为纯文本解析", fileName);
        return ParsedDocument.builder()
                .title(fileName.replaceFirst("\\.(docx?|doc)$", ""))
                .content(content.trim())
                .fileType("WORD")
                .pageCount(1)
                .build();
    }
}