package com.knowledgeforge.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledgeforge.ai.embedding")
public class EmbeddingProviderProperties {

    /**
     * 当前先支持 OpenAI 兼容协议，后续如需扩展可在 AiConfig 中按 provider 分支构造。
     */
    private String provider = "openai-compatible";

    private String baseUrl;

    private String apiKey;

    private String model = "BAAI/bge-large-zh-v1.5";

    private int dimensions = 1024;

    public void validate() {
        Assert.isTrue(StringUtils.hasText(provider), "Embedding provider 未配置");
        Assert.isTrue(StringUtils.hasText(baseUrl), "Embedding baseUrl 未配置");
        Assert.isTrue(StringUtils.hasText(apiKey), "Embedding apiKey 未配置");
        Assert.isTrue(StringUtils.hasText(model), "Embedding model 未配置");
        Assert.isTrue(dimensions > 0, "Embedding dimensions 必须大于 0");
    }
}
