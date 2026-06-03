package com.knowledgeforge.system.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MarkdownChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final int MIN_CHUNK_SIZE = 100;
    private static final int MAX_CHUNK_SIZE = 1024;
    private static final int OVERLAP_SIZE = 100;

    @Override
    public List<String> chunk(String content) {
        List<String> sections = splitByHeadings(content);
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            if (section.length() <= MAX_CHUNK_SIZE) {
                if (!section.isBlank()) {
                    chunks.add(section.trim());
                }
            } else {
                chunks.addAll(splitLargeSection(section));
            }
        }
        if (chunks.isEmpty()) {
            return List.of(content.trim());
        }
        return mergeSmallChunks(chunks);
    }

    private List<String> splitByHeadings(String content) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String leading = content.substring(lastEnd, matcher.start()).trim();
                if (!leading.isEmpty()) {
                    sections.add(leading);
                }
            }
            lastEnd = matcher.start();
        }

        if (lastEnd < content.length()) {
            String remaining = content.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                sections.add(remaining);
            }
        }

        if (sections.isEmpty()) {
            sections.add(content);
        }
        return sections;
    }

    private List<String> splitLargeSection(String section) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = section.split("\n\n+");
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
        return chunks;
    }

    private List<String> mergeSmallChunks(List<String> chunks) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String chunk : chunks) {
            if (current.length() + chunk.length() > MAX_CHUNK_SIZE && current.length() > 0) {
                merged.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(chunk);
        }

        if (current.length() > 0) {
            merged.add(current.toString().trim());
        }
        return merged.isEmpty() ? chunks : merged;
    }

    @Override
    public boolean supports(String fileType) {
        return "MARKDOWN".equalsIgnoreCase(fileType) || "MD".equalsIgnoreCase(fileType);
    }
}