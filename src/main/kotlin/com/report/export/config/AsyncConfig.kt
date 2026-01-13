package com.report.export.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {
    
    @Bean("exportTaskExecutor")
    fun exportTaskExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 10
            setThreadNamePrefix("export-")
            initialize()
        }
    }
}
