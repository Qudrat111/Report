package com.report.export.controller

import com.report.export.config.ExportConfig
import com.report.export.model.ExportJob
import com.report.export.model.ExportRequest
import com.report.export.service.OrderExportService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/orders/export")
class OrderExportController(
    private val exportService: OrderExportService,
    private val config: ExportConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @GetMapping
    fun exportOrders(
        @RequestParam fromDate: LocalDateTime?,
        @RequestParam toDate: LocalDateTime?,
        @RequestParam status: String?,
        @RequestParam(defaultValue = "false") async: Boolean,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        val request = ExportRequest(fromDate, toDate, status, async = async)
        
        return if (exportService.shouldUseAsync(request)) {
            val job = exportService.createAsyncExport(request)
            ResponseEntity.accepted().body(job)
        } else {
            streamExport(request, response)
            ResponseEntity.ok().build<Unit>()
        }
    }
    
    @GetMapping("/sync")
    fun exportOrdersSync(
        @RequestParam fromDate: LocalDateTime?,
        @RequestParam toDate: LocalDateTime?,
        @RequestParam status: String?,
        response: HttpServletResponse
    ) {
        val request = ExportRequest(fromDate, toDate, status)
        streamExport(request, response)
    }
    
    @PostMapping("/async")
    fun exportOrdersAsync(@RequestBody request: ExportRequest): ResponseEntity<ExportJob> {
        val job = exportService.createAsyncExport(request)
        return ResponseEntity.accepted().body(job)
    }
    
    @GetMapping("/status/{jobId}")
    fun getExportStatus(@PathVariable jobId: String): ResponseEntity<ExportJob> {
        val job = exportService.getJobStatus(jobId)
        return if (job != null) {
            ResponseEntity.ok(job)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @GetMapping("/download/{jobId}")
    fun downloadExport(@PathVariable jobId: String): ResponseEntity<Resource> {
        val job = exportService.getJobStatus(jobId)
            ?: return ResponseEntity.notFound().build()
        
        if (job.downloadUrl == null) {
            return ResponseEntity.badRequest().build()
        }
        
        val filePath = Paths.get(config.exportDirectory, "$jobId.xlsx")
        val resource = FileSystemResource(filePath)
        
        if (!resource.exists()) {
            return ResponseEntity.notFound().build()
        }
        
        val filename = "orders_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.xlsx"
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(resource)
    }
    
    private fun streamExport(request: ExportRequest, response: HttpServletResponse) {
        val filename = "orders_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.xlsx"
        
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
        
        exportService.streamExport(request, response.outputStream)
    }
}
