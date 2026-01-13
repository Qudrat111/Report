package com.report.export.repository

import com.report.export.model.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime

@Repository
class OrderExportRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    
    fun fetchOrdersAfter(
        lastId: Long,
        limit: Int,
        fromDate: LocalDateTime?,
        toDate: LocalDateTime?,
        status: String?
    ): List<Order> {
        val sql = buildString {
            append("SELECT id, order_number, customer_name, customer_email, status, ")
            append("total_amount, currency, created_at, updated_at, notes ")
            append("FROM orders WHERE id > ? ")
            fromDate?.let { append("AND created_at >= ? ") }
            toDate?.let { append("AND created_at <= ? ") }
            status?.let { append("AND status = ? ") }
            append("ORDER BY id ASC LIMIT ?")
        }
        
        val params = mutableListOf<Any>(lastId)
        fromDate?.let { params.add(it) }
        toDate?.let { params.add(it) }
        status?.let { params.add(it) }
        params.add(limit)
        
        return jdbcTemplate.query(sql, OrderRowMapper(), *params.toTypedArray())
    }
    
    fun countOrders(fromDate: LocalDateTime?, toDate: LocalDateTime?, status: String?): Long {
        val sql = buildString {
            append("SELECT COUNT(*) FROM orders WHERE 1=1 ")
            fromDate?.let { append("AND created_at >= ? ") }
            toDate?.let { append("AND created_at <= ? ") }
            status?.let { append("AND status = ? ") }
        }
        
        val params = mutableListOf<Any>()
        fromDate?.let { params.add(it) }
        toDate?.let { params.add(it) }
        status?.let { params.add(it) }
        
        return jdbcTemplate.queryForObject(sql, Long::class.java, *params.toTypedArray()) ?: 0
    }
    
    private class OrderRowMapper : RowMapper<Order> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Order {
            return Order(
                id = rs.getLong("id"),
                orderNumber = rs.getString("order_number"),
                customerName = rs.getString("customer_name"),
                customerEmail = rs.getString("customer_email"),
                status = rs.getString("status"),
                totalAmount = rs.getBigDecimal("total_amount"),
                currency = rs.getString("currency"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime(),
                notes = rs.getString("notes")
            )
        }
    }
}
