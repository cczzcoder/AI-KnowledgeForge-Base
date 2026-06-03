package com.knowledgeforge.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("知否 API")
                        .version("1.0.0")
                        .description("基于 Spring AI 的个人知识库 RAG 问答应用接口文档")
                        .contact(new Contact()
                                .name("知否团队"))
                        .license(new License()
                                .name("MIT")));
    }
}