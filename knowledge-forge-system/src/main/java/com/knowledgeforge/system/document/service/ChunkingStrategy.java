package com.knowledgeforge.system.document.service;

import java.util.List;

public interface ChunkingStrategy {

    List<ChunkDescriptor> chunk(String content);

    boolean supports(String fileType);
}