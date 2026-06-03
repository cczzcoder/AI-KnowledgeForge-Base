package com.knowledgeforge.system.document.service;

import com.knowledgeforge.system.document.dto.ParsedDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class DocumentParserFactory {

    private final PdfParser pdfParser;
    private final WordParser wordParser;
    private final MarkdownParser markdownParser;

    public DocumentParserFactory(PdfParser pdfParser, WordParser wordParser, MarkdownParser markdownParser) {
        this.pdfParser = pdfParser;
        this.wordParser = wordParser;
        this.markdownParser = markdownParser;
    }

    public ParsedDocument parse(InputStream inputStream, String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return pdfParser.parse(inputStream, fileName);
        } else if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return wordParser.parse(inputStream, fileName);
        } else if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return markdownParser.parse(inputStream, fileName);
        } else if (lowerName.endsWith(".txt")) {
            return parseTextFile(inputStream, fileName);
        }
        throw new IllegalArgumentException("不支持的文件格式: " + fileName + "，支持 PDF、Word(.doc/.docx)、Markdown、TXT");
    }

    private ParsedDocument parseTextFile(InputStream inputStream, String fileName) {
        try {
            String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return ParsedDocument.builder()
                    .title(fileName.replaceFirst("\\.txt$", ""))
                    .content(content.trim())
                    .fileType("TEXT")
                    .pageCount(1)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("TXT 文件解析失败", e);
        }
    }
}