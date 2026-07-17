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
    private static final String STRATEGY_VERSION = "markdown-v1";

    @Override
    public List<ChunkDescriptor> chunk(String content) {
        List<Section> sections = splitByHeadings(content);
        List<ChunkDescriptor> chunks = new ArrayList<>();
        int nextChunkIndex = 0;
        for (Section section : sections) {
            if (section.content().isBlank()) {
                continue;
            }
            if (section.content().length() <= MAX_CHUNK_SIZE) {
                chunks.add(buildChunk(nextChunkIndex++, section.content().trim(), section.title(), section.path(),
                        section.startOffset(), section.endOffset(), "markdown-section"));
            } else {
                List<ChunkDescriptor> sectionChunks = splitLargeSection(section, nextChunkIndex);
                chunks.addAll(sectionChunks);
                nextChunkIndex += sectionChunks.size();
            }
        }
        if (chunks.isEmpty()) {
            return List.of(buildChunk(0, content.trim(), null, null, 0, content.trim().length(), "markdown-section"));
        }
        return mergeSmallChunks(chunks);
    }

    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        List<HeadingMatch> headings = new ArrayList<>();

        while (matcher.find()) {
            headings.add(new HeadingMatch(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).trim()));
        }

        if (headings.isEmpty()) {
            sections.add(new Section(null, null, content, 0, content.length()));
            return sections;
        }

        List<String> titleStack = new ArrayList<>();
        int lastEnd = 0;
        if (headings.get(0).start() > 0) {
            String leading = content.substring(0, headings.get(0).start()).trim();
            if (!leading.isEmpty()) {
                sections.add(new Section(null, null, leading, 0, headings.get(0).start()));
            }
        }

        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch current = headings.get(i);
            int nextStart = i + 1 < headings.size() ? headings.get(i + 1).start() : content.length();
            while (titleStack.size() >= current.level()) {
                titleStack.remove(titleStack.size() - 1);
            }
            titleStack.add(current.title());
            String sectionContent = content.substring(current.start(), nextStart).trim();
            if (!sectionContent.isEmpty()) {
                sections.add(new Section(current.title(), String.join(" > ", titleStack), sectionContent,
                        current.start(), nextStart));
            }
            lastEnd = nextStart;
        }

        if (lastEnd < content.length()) {
            String trailing = content.substring(lastEnd).trim();
            if (!trailing.isEmpty()) {
                sections.add(new Section(titleStack.isEmpty() ? null : titleStack.get(titleStack.size() - 1),
                        titleStack.isEmpty() ? null : String.join(" > ", titleStack), trailing, lastEnd, content.length()));
            }
        }
        return sections;
    }

    private List<ChunkDescriptor> splitLargeSection(Section section, int startChunkIndex) {
        List<ChunkDescriptor> chunks = new ArrayList<>();
        String[] paragraphs = section.content().split("\n\n+");
        StringBuilder current = new StringBuilder();
        int chunkStartOffset = section.startOffset();
        int chunkEndOffset = section.startOffset();
        int searchOffset = section.startOffset();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int paragraphStart = findOffset(section.content(), trimmed, Math.max(searchOffset - section.startOffset(), 0)) + section.startOffset();
            int paragraphEnd = paragraphStart + trimmed.length();
            searchOffset = paragraphEnd;

            if (current.length() + trimmed.length() > MAX_CHUNK_SIZE && current.length() > MIN_CHUNK_SIZE) {
                chunks.add(buildChunk(startChunkIndex + chunks.size(), current.toString().trim(), section.title(),
                        section.path(), chunkStartOffset, chunkEndOffset, "markdown-paragraph"));
                if (current.length() > OVERLAP_SIZE) {
                    String overlap = current.substring(current.length() - OVERLAP_SIZE);
                    current = new StringBuilder(overlap);
                    chunkStartOffset = Math.max(chunkEndOffset - OVERLAP_SIZE, section.startOffset());
                } else {
                    current = new StringBuilder();
                    chunkStartOffset = paragraphStart;
                }
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            chunkEndOffset = paragraphEnd;
        }

        if (current.length() > 0) {
            chunks.add(buildChunk(startChunkIndex + chunks.size(), current.toString().trim(), section.title(),
                    section.path(), chunkStartOffset, chunkEndOffset, "markdown-paragraph"));
        }
        return chunks;
    }

    private List<ChunkDescriptor> mergeSmallChunks(List<ChunkDescriptor> chunks) {
        List<ChunkDescriptor> merged = new ArrayList<>();
        ChunkDescriptor pending = null;

        for (ChunkDescriptor chunk : chunks) {
            if (pending == null) {
                pending = chunk;
                continue;
            }
            if (pending.getContent().length() + chunk.getContent().length() <= MAX_CHUNK_SIZE
                    && pending.getContent().length() < MIN_CHUNK_SIZE) {
                pending = ChunkDescriptor.builder()
                        .chunkIndex(pending.getChunkIndex())
                        .content((pending.getContent() + "\n\n" + chunk.getContent()).trim())
                        .tokenCount(estimateTokens((pending.getContent() + "\n\n" + chunk.getContent()).trim()))
                        .chunkType(pending.getChunkType())
                        .sectionTitle(pending.getSectionTitle())
                        .sectionPath(pending.getSectionPath())
                        .startOffset(pending.getStartOffset())
                        .endOffset(chunk.getEndOffset())
                        .parentRef(pending.getParentRef())
                        .strategyVersion(STRATEGY_VERSION)
                        .build();
            } else {
                merged.add(reindexChunk(pending, merged.size()));
                pending = chunk;
            }
        }

        if (pending != null) {
            merged.add(reindexChunk(pending, merged.size()));
        }
        return merged;
    }

    private ChunkDescriptor buildChunk(int index, String content, String sectionTitle, String sectionPath,
                                       int startOffset, int endOffset, String chunkType) {
        return ChunkDescriptor.builder()
                .chunkIndex(index)
                .content(content)
                .tokenCount(estimateTokens(content))
                .chunkType(chunkType)
                .sectionTitle(sectionTitle)
                .sectionPath(sectionPath)
                .startOffset(Math.max(startOffset, 0))
                .endOffset(Math.max(endOffset, Math.max(startOffset, 0)))
                .strategyVersion(STRATEGY_VERSION)
                .build();
    }

    private ChunkDescriptor reindexChunk(ChunkDescriptor chunk, int index) {
        return ChunkDescriptor.builder()
                .chunkIndex(index)
                .content(chunk.getContent())
                .tokenCount(chunk.getTokenCount())
                .chunkType(chunk.getChunkType())
                .sectionTitle(chunk.getSectionTitle())
                .sectionPath(chunk.getSectionPath())
                .startOffset(chunk.getStartOffset())
                .endOffset(chunk.getEndOffset())
                .parentRef(chunk.getParentRef())
                .strategyVersion(chunk.getStrategyVersion())
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
        return "MARKDOWN".equalsIgnoreCase(fileType) || "MD".equalsIgnoreCase(fileType);
    }

    private record HeadingMatch(int start, int end, int level, String title) {
    }

    private record Section(String title, String path, String content, int startOffset, int endOffset) {
    }
}
