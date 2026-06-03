package com.knowledgeforge.system.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    private String title;
    private String content;
    private String fileType;
    private long fileSize;
    private int pageCount;
}