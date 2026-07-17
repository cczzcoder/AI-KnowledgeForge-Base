package com.knowledgeforge.system.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SentenceChunkingStrategy implements ChunkingStrategy {

    private static final int MAX_CHUNK_SIZE = 512;
    private static final int OVERLAP_SIZE = 50;
    private static final String STRATEGY_VERSION = "sentence-v1";

    @Override
    public List<ChunkDescriptor> chunk(String content) {
        List<ChunkDescriptor> chunks = new ArrayList<>();
        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(buildChunk(0, content, 0, content.length()));
            return chunks;
        }

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, content.length());
            if (end < content.length()) {
                int cutPoint = findCutPoint(content, end, 80);
                if (cutPoint > start) {
                    end = cutPoint;
                }
            }
            String chunkContent = content.substring(start, end).trim();
            chunks.add(buildChunk(chunks.size(), chunkContent, start, end));
            start = Math.max(start + (end - start - OVERLAP_SIZE), start + 1);
        }
        return chunks;
    }

    private int findCutPoint(String text, int target, int searchRange) {
        int searchStart = Math.max(target - searchRange, 0);
        for (int i = target; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        return target;
    }

    private ChunkDescriptor buildChunk(int index, String content, int startOffset, int endOffset) {
        return ChunkDescriptor.builder()
                .chunkIndex(index)
                .content(content)
                .tokenCount(estimateTokens(content))
                .chunkType("sentence")
                .startOffset(Math.max(startOffset, 0))
                .endOffset(Math.max(endOffset, Math.max(startOffset, 0)))
                .strategyVersion(STRATEGY_VERSION)
                .build();
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 1.5d);
    }

    @Override
    public boolean supports(String fileType) {
        return false;
    }
}
