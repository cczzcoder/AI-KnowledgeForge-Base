package com.knowledgeforge.system.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ConversationCreateDTO {

    @NotBlank(message = "对话标题不能为空")
    @Size(max = 255, message = "标题长度不能超过 255")
    private String title;

    /** 可选：关联的知识库ID */
    private UUID kbId;
}