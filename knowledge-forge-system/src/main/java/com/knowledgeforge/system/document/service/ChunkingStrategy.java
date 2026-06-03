package com.knowledgeforge.system.document.service;

import java.util.List;

public interface ChunkingStrategy {

    List<String> chunk(String content);

    boolean supports(String fileType);
}