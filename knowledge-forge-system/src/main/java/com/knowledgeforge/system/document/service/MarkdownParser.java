package com.knowledgeforge.system.document.service;

import com.knowledgeforge.system.document.dto.ParsedDocument;
import com.knowledgeforge.core.shared.exception.DocumentProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MarkdownParser {

    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            String text = content.toString();
            if (text.isBlank()) {
                throw new DocumentProcessException("Markdown 文件内容为空");
            }

            String title = text.lines()
                    .filter(l -> l.startsWith("# "))
                    .findFirst()
                    .map(l -> l.substring(2).trim())
                    .orElse(fileName.replaceFirst("\\.md$", ""));

            return ParsedDocument.builder()
                    .title(title)
                    .content(text.trim())
                    .fileType("MARKDOWN")
                    .pageCount(1)
                    .build();
        } catch (IOException e) {
            log.error("Markdown 解析失败: {}", fileName, e);
            throw new DocumentProcessException("Markdown 解析失败: " + e.getMessage(), e);
        }
    }
}