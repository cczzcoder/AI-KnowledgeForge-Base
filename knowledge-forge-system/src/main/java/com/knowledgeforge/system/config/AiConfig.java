package com.knowledgeforge.system.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
    private int vectorDimensions;

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(EmbeddingProviderProperties embeddingProperties) {
        embeddingProperties.validate();
        validateDimensions(embeddingProperties);
        validateProvider(embeddingProperties);

        log.info("初始化 EmbeddingModel: provider={}, baseUrl={}, model={}, dimensions={}",
                embeddingProperties.getProvider(),
                sanitizeBaseUrl(embeddingProperties.getBaseUrl()),
                embeddingProperties.getModel(),
                embeddingProperties.getDimensions());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(embeddingProperties.getBaseUrl())
                .apiKey(embeddingProperties.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingProperties.getModel())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(false)
                .vectorTableName("vector_store")
                .dimensions(vectorDimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .build();
    }

    private void validateProvider(EmbeddingProviderProperties embeddingProperties) {
        Assert.isTrue("openai-compatible".equalsIgnoreCase(embeddingProperties.getProvider())
                        || "openai".equalsIgnoreCase(embeddingProperties.getProvider()),
                "当前仅支持 openai-compatible / openai 类型的 Embedding provider");
    }

    private void validateDimensions(EmbeddingProviderProperties embeddingProperties) {
        Assert.isTrue(embeddingProperties.getDimensions() == vectorDimensions,
                "Embedding dimensions 与 pgvector dimensions 不一致: embedding=%s, pgvector=%s"
                        .formatted(embeddingProperties.getDimensions(), vectorDimensions));
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "<empty>";
        }
        int schemeIdx = baseUrl.indexOf("://");
        if (schemeIdx < 0) {
            return baseUrl;
        }
        int pathIdx = baseUrl.indexOf('/', schemeIdx + 3);
        return pathIdx < 0 ? baseUrl : baseUrl.substring(0, pathIdx);
    }
}
