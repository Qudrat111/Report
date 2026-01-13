package com.report.export.model

import java.time.LocalDateTime

data class ExportRequest(
    val fromDate: LocalDateTime? = null,
    val toDate: LocalDateTime? = null,
    val status: String? = null,
    val columns: List<String>? = null,
    val async: Boolean = false
)
