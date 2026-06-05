package com.wendao.system.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI分析异步配置
 *
 * @author wendao
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({NewsAiProperties.class, NewsFetchProperties.class})
public class NewsAiConfig
{
    /**
     * AI分析专用线程池
     */
    @Bean("aiAnalysisExecutor")
    public Executor aiAnalysisExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
