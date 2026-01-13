package com.report.export.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val id: Long,
    val orderNumber: String,
    val customerName: String?,
    val customerEmail: String?,
    val status: String,
    val totalAmount: BigDecimal,
    val currency: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
    val notes: String?
)
