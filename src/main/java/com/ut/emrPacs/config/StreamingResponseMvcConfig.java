package com.ut.emrPacs.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StreamingResponseMvcConfig implements WebMvcConfigurer {

    @Value("${pacs.viewer.dicomweb.streaming.async-timeout-ms:7200000}")
    private long asyncTimeoutMs;

    @Value("${pacs.viewer.dicomweb.streaming.core-pool-size:16}")
    private int corePoolSize;

    @Value("${pacs.viewer.dicomweb.streaming.max-pool-size:128}")
    private int maxPoolSize;

    @Value("${pacs.viewer.dicomweb.streaming.queue-capacity:512}")
    private int queueCapacity;

    @Bean
    public ThreadPoolTaskExecutor pacsViewerStreamingTaskExecutor() {
        int safeCorePoolSize = Math.max(4, corePoolSize);
        int safeMaxPoolSize = Math.max(safeCorePoolSize, maxPoolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("pacs-viewer-stream-");
        executor.setCorePoolSize(safeCorePoolSize);
        executor.setMaxPoolSize(safeMaxPoolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(Math.max(30000L, asyncTimeoutMs));
        configurer.setTaskExecutor(pacsViewerStreamingTaskExecutor());
    }
}
