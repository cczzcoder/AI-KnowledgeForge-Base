package com.knowledgeforge.system.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ParagraphChunkingStrategy implements ChunkingStrategy {

    private static final int MAX_CHUNK_SIZE = 1024;
    private static final int MIN_CHUNK_SIZE = 100;
    private static final int OVERLAP_SIZE = 100;

    @Override
    public List<String> chunk(String content) {
        String[] paragraphs = content.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() > MAX_CHUNK_SIZE && current.length() > MIN_CHUNK_SIZE) {
                chunks.add(current.toString().trim());
                if (current.length() > OVERLAP_SIZE) {
                    String overlap = current.substring(current.length() - OVERLAP_SIZE);
                    current = new StringBuilder(overlap);
                } else {
                    current = new StringBuilder();
                }
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        if (chunks.isEmpty()) {
            return List.of(content.trim());
        }
        return chunks;
    }

    @Override
    public boolean supports(String fileType) {
        return "WORD".equalsIgnoreCase(fileType)
                || "PDF".equalsIgnoreCase(fileType)
                || "TEXT".equalsIgnoreCase(fileType);
    }
}