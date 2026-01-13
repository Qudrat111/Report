package com.report.export.service

import com.report.export.config.ExportConfig
import com.report.export.model.ExportJob
import com.report.export.model.ExportJobStatus
import com.report.export.model.ExportRequest
import com.report.export.repository.OrderExportRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class OrderExportService(
    private val repository: OrderExportRepository,
    private val excelWriter: ExcelWriterService,
    private val config: ExportConfig,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jobs = ConcurrentHashMap<String, ExportJob>()
    
    fun streamExport(request: ExportRequest, outputStream: OutputStream) {
        val timer = Timer.start(meterRegistry)
        val rowsWritten = AtomicLong(0)
        
        log.info("Starting sync export: fromDate={}, toDate={}, status={}", 
            request.fromDate, request.toDate, request.status)
        
        val workbook = excelWriter.createWorkbook()
        val styles = excelWriter.createCellStyles(workbook)
        
        try {
            var currentSheet = excelWriter.createSheet(workbook, "Orders")
            var sheetRowNum = 1
            var sheetIndex = 1
            var lastId = 0L
            
            do {
                val orders = repository.fetchOrdersAfter(
                    lastId = lastId,
                    limit = config.chunkSize,
                    fromDate = request.fromDate,
                    toDate = request.toDate,
                    status = request.status
                )
                
                for (order in orders) {
                    if (sheetRowNum >= config.maxRowsPerSheet) {
                        sheetIndex++
                        currentSheet = excelWriter.createSheet(workbook, "Orders_$sheetIndex")
                        sheetRowNum = 1
                        log.info("Created new sheet: Orders_$sheetIndex")
                    }
                    
                    val row = currentSheet.createRow(sheetRowNum++)
                    excelWriter.writeOrderRow(row, order, styles)
                    lastId = order.id
                    rowsWritten.incrementAndGet()
                }
                
                if (rowsWritten.get() % 50_000 == 0L && rowsWritten.get() > 0) {
                    log.info("Export progress: {} rows written", rowsWritten.get())
                }
                
            } while (orders.size == config.chunkSize)
            
            excelWriter.writeToStream(workbook, outputStream)
            
            timer.stop(meterRegistry.timer("export.duration", "type", "sync"))
            meterRegistry.counter("export.rows", "type", "sync").increment(rowsWritten.get().toDouble())
            
            log.info("Sync export completed: {} rows in {} sheets", rowsWritten.get(), sheetIndex)
            
        } catch (e: Exception) {
            meterRegistry.counter("export.errors", "type", "sync").increment()
            log.error("Export failed at row {}: {}", rowsWritten.get(), e.message, e)
            throw e
        }
    }
    
    fun createAsyncExport(request: ExportRequest): ExportJob {
        val totalCount = repository.countOrders(request.fromDate, request.toDate, request.status)
        
        val job = ExportJob(
            id = UUID.randomUUID().toString(),
            status = ExportJobStatus.PENDING,
            totalRows = totalCount,
            processedRows = 0,
            downloadUrl = null,
            errorMessage = null,
            createdAt = LocalDateTime.now(),
            completedAt = null
        )
        
        jobs[job.id] = job
        processAsyncExport(job.id, request)
        
        return job
    }
    
    @Async("exportTaskExecutor")
    fun processAsyncExport(jobId: String, request: ExportRequest) {
        val timer = Timer.start(meterRegistry)
        log.info("Starting async export job: {}", jobId)
        
        updateJobStatus(jobId, ExportJobStatus.PROCESSING)
        
        try {
            val exportDir = Paths.get(config.exportDirectory)
            if (!Files.exists(exportDir)) {
                Files.createDirectories(exportDir)
            }
            
            val filePath = exportDir.resolve("$jobId.xlsx")
            
            Files.newOutputStream(filePath).use { outputStream ->
                streamExportWithProgress(request, outputStream, jobId)
            }
            
            val downloadUrl = "/api/orders/export/download/$jobId"
            
            jobs[jobId] = jobs[jobId]!!.copy(
                status = ExportJobStatus.COMPLETED,
                downloadUrl = downloadUrl,
                completedAt = LocalDateTime.now()
            )
            
            timer.stop(meterRegistry.timer("export.duration", "type", "async"))
            log.info("Async export completed: jobId={}", jobId)
            
        } catch (e: Exception) {
            meterRegistry.counter("export.errors", "type", "async").increment()
            log.error("Async export failed: jobId={}, error={}", jobId, e.message, e)
            
            jobs[jobId] = jobs[jobId]!!.copy(
                status = ExportJobStatus.FAILED,
                errorMessage = e.message,
                completedAt = LocalDateTime.now()
            )
        }
    }
    
    private fun streamExportWithProgress(request: ExportRequest, outputStream: OutputStream, jobId: String) {
        val workbook = excelWriter.createWorkbook()
        val styles = excelWriter.createCellStyles(workbook)
        
        var currentSheet = excelWriter.createSheet(workbook, "Orders")
        var sheetRowNum = 1
        var sheetIndex = 1
        var lastId = 0L
        var processedRows = 0L
        
        do {
            val orders = repository.fetchOrdersAfter(
                lastId = lastId,
                limit = config.chunkSize,
                fromDate = request.fromDate,
                toDate = request.toDate,
                status = request.status
            )
            
            for (order in orders) {
                if (sheetRowNum >= config.maxRowsPerSheet) {
                    sheetIndex++
                    currentSheet = excelWriter.createSheet(workbook, "Orders_$sheetIndex")
                    sheetRowNum = 1
                }
                
                val row = currentSheet.createRow(sheetRowNum++)
                excelWriter.writeOrderRow(row, order, styles)
                lastId = order.id
                processedRows++
            }
            
            jobs[jobId] = jobs[jobId]!!.copy(processedRows = processedRows)
            
        } while (orders.size == config.chunkSize)
        
        excelWriter.writeToStream(workbook, outputStream)
    }
    
    fun getJobStatus(jobId: String): ExportJob? = jobs[jobId]
    
    private fun updateJobStatus(jobId: String, status: ExportJobStatus) {
        jobs[jobId] = jobs[jobId]!!.copy(status = status)
    }
    
    fun shouldUseAsync(request: ExportRequest): Boolean {
        if (request.async) return true
        val count = repository.countOrders(request.fromDate, request.toDate, request.status)
        return count > config.syncThreshold
    }
}
