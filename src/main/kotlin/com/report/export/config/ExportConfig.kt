package com.report.export.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "export")
class ExportConfig {
    var chunkSize: Int = 1000
    var maxRowsPerSheet: Int = 1_000_000
    var syncThreshold: Int = 100_000
    var exportDirectory: String = "./exports"
    var memoryRowsInWindow: Int = 100
}
