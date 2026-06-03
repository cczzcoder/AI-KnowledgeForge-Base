package com.knowledgeforge.system.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SentenceChunkingStrategy implements ChunkingStrategy {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
            "[^。！？；\\n]+[。！？；\\n]?");
    private static final int MAX_CHUNK_SIZE = 512;
    private static final int OVERLAP_SIZE = 50;

    @Override
    public List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();
        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(content);
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
            chunks.add(content.substring(start, end).trim());
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

    @Override
    public boolean supports(String fileType) {
        return false;
    }
}