package com.knowledgeforge.system.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class KnowledgeBaseResetRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255")
    private String name;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    @Size(max = 50, message = "图标名长度不能超过 50")
    private String icon;

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 255, message = "会话标题长度不能超过 255")
    private String conversationTitle;

    private MultipartFile file;
}
