package com.knowledgeforge.system.document.service;

import com.knowledgeforge.system.document.dto.ParsedDocument;
import com.knowledgeforge.core.shared.exception.DocumentProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class PdfParser {

    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();

            if (text.isBlank()) {
                throw new DocumentProcessException("PDF 文件无法提取文本内容，可能是扫描件或图片型 PDF");
            }

            String title = extractTitle(fileName, text);
            return ParsedDocument.builder()
                    .title(title)
                    .content(text.trim())
                    .fileType("PDF")
                    .pageCount(pageCount)
                    .build();
        } catch (IOException e) {
            log.error("PDF 解析失败: {}", fileName, e);
            throw new DocumentProcessException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    private String extractTitle(String fileName, String text) {
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        if (firstLine.length() > 100) {
            firstLine = firstLine.substring(0, 100);
        }
        return firstLine.isBlank() ? fileName.replaceFirst("\\.pdf$", "") : firstLine;
    }
}