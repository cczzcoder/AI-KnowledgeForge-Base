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
    private static final String STRATEGY_VERSION = "paragraph-v1";

    @Override
    public List<ChunkDescriptor> chunk(String content) {
        String[] paragraphs = content.split("\n\n+");
        List<ChunkDescriptor> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkStartOffset = -1;
        int chunkEndOffset = -1;
        int searchOffset = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int paragraphStart = findOffset(content, trimmed, searchOffset);
            int paragraphEnd = paragraphStart >= 0 ? paragraphStart + trimmed.length() : searchOffset + trimmed.length();
            if (paragraphStart >= 0) {
                searchOffset = paragraphEnd;
            }

            if (current.length() + trimmed.length() > MAX_CHUNK_SIZE && current.length() > MIN_CHUNK_SIZE) {
                chunks.add(buildChunk(chunks.size(), current.toString().trim(), chunkStartOffset, chunkEndOffset));
                if (current.length() > OVERLAP_SIZE) {
                    String overlap = current.substring(current.length() - OVERLAP_SIZE);
                    current = new StringBuilder(overlap);
                    chunkStartOffset = Math.max(chunkEndOffset - OVERLAP_SIZE, 0);
                } else {
                    current = new StringBuilder();
                    chunkStartOffset = -1;
                }
                chunkEndOffset = -1;
            }

            if (current.length() == 0) {
                chunkStartOffset = paragraphStart >= 0 ? paragraphStart : searchOffset;
            } else {
                current.append("\n\n");
            }
            current.append(trimmed);
            chunkEndOffset = paragraphEnd;
        }

        if (current.length() > 0) {
            chunks.add(buildChunk(chunks.size(), current.toString().trim(), chunkStartOffset, chunkEndOffset));
        }

        if (chunks.isEmpty()) {
            return List.of(buildChunk(0, content.trim(), 0, content.trim().length()));
        }
        return chunks;
    }

    private ChunkDescriptor buildChunk(int index, String content, int startOffset, int endOffset) {
        return ChunkDescriptor.builder()
                .chunkIndex(index)
                .content(content)
                .tokenCount(estimateTokens(content))
                .chunkType("paragraph")
                .startOffset(Math.max(startOffset, 0))
                .endOffset(Math.max(endOffset, Math.max(startOffset, 0)))
                .strategyVersion(STRATEGY_VERSION)
                .build();
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 1.5d);
    }

    private int findOffset(String content, String segment, int fromIndex) {
        if (segment.isEmpty()) {
            return Math.max(fromIndex, 0);
        }
        int safeFromIndex = Math.max(fromIndex, 0);
        int offset = content.indexOf(segment, safeFromIndex);
        return offset >= 0 ? offset : safeFromIndex;
    }

    @Override
    public boolean supports(String fileType) {
        return "WORD".equalsIgnoreCase(fileType)
                || "PDF".equalsIgnoreCase(fileType)
                || "TEXT".equalsIgnoreCase(fileType);
    }
}
