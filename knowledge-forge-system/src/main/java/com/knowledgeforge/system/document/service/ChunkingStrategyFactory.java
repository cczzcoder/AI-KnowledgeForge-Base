package com.knowledgeforge.system.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ChunkingStrategyFactory {

    private final MarkdownChunkingStrategy markdownStrategy;
    private final ParagraphChunkingStrategy paragraphStrategy;
    private final SentenceChunkingStrategy sentenceStrategy;

    public ChunkingStrategyFactory(MarkdownChunkingStrategy markdownStrategy,
                                   ParagraphChunkingStrategy paragraphStrategy,
                                   SentenceChunkingStrategy sentenceStrategy) {
        this.markdownStrategy = markdownStrategy;
        this.paragraphStrategy = paragraphStrategy;
        this.sentenceStrategy = sentenceStrategy;
    }

    public ChunkingStrategy getStrategy(String fileType) {
        if (markdownStrategy.supports(fileType)) {
            log.debug("为文件类型 {} 选择 MarkdownChunkingStrategy", fileType);
            return markdownStrategy;
        }
        if (paragraphStrategy.supports(fileType)) {
            log.debug("为文件类型 {} 选择 ParagraphChunkingStrategy", fileType);
            return paragraphStrategy;
        }
        log.debug("为文件类型 {} 选择默认 SentenceChunkingStrategy", fileType);
        return sentenceStrategy;
    }

    public List<ChunkDescriptor> chunk(String content, String fileType) {
        return getStrategy(fileType).chunk(content);
    }
}
