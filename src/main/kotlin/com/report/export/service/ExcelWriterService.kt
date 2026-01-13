package com.report.export.service

import com.report.export.config.ExportConfig
import com.report.export.model.Order
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.time.format.DateTimeFormatter

@Service
class ExcelWriterService(
    private val config: ExportConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    companion object {
        val HEADERS = listOf(
            "ID", "Order Number", "Customer Name", "Email", "Status",
            "Total Amount", "Currency", "Created At", "Updated At", "Notes"
        )
        const val MAX_CELL_LENGTH = 32767
    }
    
    fun createWorkbook(): SXSSFWorkbook {
        return SXSSFWorkbook(config.memoryRowsInWindow).apply {
            isForceFormulaRecalculation = false
        }
    }
    
    fun createSheet(workbook: SXSSFWorkbook, sheetName: String): Sheet {
        return workbook.createSheet(sheetName).also { sheet ->
            writeHeaderRow(sheet)
        }
    }
    
    fun createCellStyles(workbook: SXSSFWorkbook): CellStyles {
        val dateStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss")
        }
        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.creationHelper.createDataFormat().getFormat("#,##0.00")
        }
        return CellStyles(dateStyle, currencyStyle)
    }
    
    private fun writeHeaderRow(sheet: Sheet) {
        val headerRow = sheet.createRow(0)
        HEADERS.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }
    }
    
    fun writeOrderRow(row: Row, order: Order, styles: CellStyles) {
        var col = 0
        row.createCell(col++).setCellValue(order.id.toDouble())
        row.createCell(col++).setCellValue(truncate(order.orderNumber))
        row.createCell(col++).setCellValue(truncate(order.customerName ?: ""))
        row.createCell(col++).setCellValue(truncate(order.customerEmail ?: ""))
        row.createCell(col++).setCellValue(truncate(order.status))
        row.createCell(col++).apply {
            setCellValue(order.totalAmount.toDouble())
            cellStyle = styles.currencyStyle
        }
        row.createCell(col++).setCellValue(truncate(order.currency))
        row.createCell(col++).setCellValue(order.createdAt.format(dateFormatter))
        row.createCell(col++).setCellValue(order.updatedAt?.format(dateFormatter) ?: "")
        row.createCell(col).setCellValue(truncate(order.notes ?: ""))
    }
    
    fun writeToStream(workbook: SXSSFWorkbook, outputStream: OutputStream) {
        try {
            workbook.write(outputStream)
        } finally {
            workbook.dispose()
            workbook.close()
        }
    }
    
    private fun truncate(value: String): String {
        return if (value.length > MAX_CELL_LENGTH) {
            value.substring(0, MAX_CELL_LENGTH - 3) + "..."
        } else {
            value
        }
    }
    
    data class CellStyles(
        val dateStyle: CellStyle,
        val currencyStyle: CellStyle
    )
}
