package com.knowledgeforge.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置。
 * 为文档处理（解析、分块、向量化、图谱构建）提供专用线程池，
 * 避免阻塞主请求线程，同时限制并发数防止资源耗尽。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文档处理专用线程池。
     * - 核心线程数 2：保持空闲时也能快速响应
     * - 最大线程数 4：限制并发，避免 LLM/向量化 API 过载
     * - 队列容量 100：缓冲等待中的任务
     * - 拒绝策略：CallerRunsPolicy，队列满时由调用线程执行（提供背压）
     */
    @Bean("docProcessExecutor")
    public Executor docProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-process-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}