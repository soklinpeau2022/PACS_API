package com.ut.emrPacs.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StreamingResponseMvcConfigTest {

    @Test
    void streamingExecutorShouldNeverRunRejectedStreamsOnServletThread() {
        StreamingResponseMvcConfig config = new StreamingResponseMvcConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", 4);
        ReflectionTestUtils.setField(config, "maxPoolSize", 8);
        ReflectionTestUtils.setField(config, "queueCapacity", 16);

        ThreadPoolTaskExecutor executor = config.pacsViewerStreamingTaskExecutor();
        try {
            assertInstanceOf(
                    ThreadPoolExecutor.AbortPolicy.class,
                    executor.getThreadPoolExecutor().getRejectedExecutionHandler()
            );
        } finally {
            executor.shutdown();
        }
    }
}
