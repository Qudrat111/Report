package com.report.export.model

import java.time.LocalDateTime

data class ExportJob(
    val id: String,
    val status: ExportJobStatus,
    val totalRows: Long?,
    val processedRows: Long,
    val downloadUrl: String?,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?
)

enum class ExportJobStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
