package com.naturasonic.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.VoiceMetricsDao
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AlertSoundClass
import com.naturasonic.app.data.local.entity.VoiceMetricsEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ReportSummary(
    val alertCount: Int,
    val voiceMetricsCount: Int,
    val dateRange: String
)

@Singleton
class WellnessReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertEventDao: AlertEventDao,
    private val voiceMetricsDao: VoiceMetricsDao
) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    private val headerPaint = Paint().apply {
        textSize = 18f
        isFakeBoldText = true
        color = android.graphics.Color.rgb(33, 33, 33)
    }

    private val titlePaint = Paint().apply {
        textSize = 14f
        isFakeBoldText = true
        color = android.graphics.Color.rgb(33, 33, 33)
    }

    private val bodyPaint = Paint().apply {
        textSize = 11f
        color = android.graphics.Color.rgb(66, 66, 66)
    }

    private val disclaimerPaint = Paint().apply {
        textSize = 8f
        color = android.graphics.Color.rgb(128, 128, 128)
    }

    private val linePaint = Paint().apply {
        color = android.graphics.Color.rgb(200, 200, 200)
        strokeWidth = 1f
    }

    private val tableLabelPaint = Paint().apply {
        textSize = 10f
        isFakeBoldText = true
        color = android.graphics.Color.rgb(33, 33, 33)
    }

    private val tableValuePaint = Paint().apply {
        textSize = 10f
        color = android.graphics.Color.rgb(66, 66, 66)
    }

    suspend fun getSummary(): ReportSummary {
        val since = sevenDaysAgoMillis()
        val alerts = alertEventDao.getSince(since)
        val metrics = voiceMetricsDao.getSince(since)
        val now = Date()
        val rangeStart = Date(since)
        return ReportSummary(
            alertCount = alerts.size,
            voiceMetricsCount = metrics.size,
            dateRange = "${dateFormat.format(rangeStart)} — ${dateFormat.format(now)}"
        )
    }

    suspend fun generatePdf(): File {
        val since = sevenDaysAgoMillis()
        val alerts = alertEventDao.getSince(since)
        val metrics = voiceMetricsDao.getSince(since)
        val now = Date()
        val rangeStart = Date(since)
        val dateRange = "${dateFormat.format(rangeStart)} — ${dateFormat.format(now)}"

        val printAttrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins(50, 50, 50, 50))
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .build()

        val document = PrintedPdfDocument(context, printAttrs)
        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 50f
        val marginRight = 545f
        val contentWidth = marginRight - marginLeft

        var pageNumber = 1
        var page = document.startPage(pageNumber - 1)
        var canvas = page.canvas
        var yPos = drawHeader(canvas, marginLeft, marginRight, dateRange)

        yPos = drawSectionTitle(canvas, "Resumen de Alertas Detectadas", marginLeft, yPos + 20f)

        if (alerts.isEmpty()) {
            canvas.drawText("Sin alertas registradas en este periodo.", marginLeft, yPos + 16f, bodyPaint)
            yPos += 30f
        } else {
            val grouped = alerts.groupBy { it.soundClass }
            yPos += 8f
            canvas.drawText("Tipo de alerta", marginLeft, yPos + 12f, tableLabelPaint)
            canvas.drawText("Cantidad", marginLeft + 250f, yPos + 12f, tableLabelPaint)
            canvas.drawText("Ultima deteccion", marginLeft + 350f, yPos + 12f, tableLabelPaint)
            yPos += 16f
            canvas.drawLine(marginLeft, yPos, marginRight, yPos, linePaint)
            yPos += 4f

            for ((classKey, events) in grouped) {
                if (yPos > pageHeight - 100f) {
                    drawFooter(canvas, marginLeft, marginRight, pageHeight.toFloat(), pageNumber)
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(pageNumber - 1)
                    canvas = page.canvas
                    yPos = drawHeader(canvas, marginLeft, marginRight, dateRange)
                    yPos += 20f
                }

                val displayName = AlertSoundClass.fromKey(classKey)?.displayName ?: classKey
                canvas.drawText(displayName, marginLeft, yPos + 12f, tableValuePaint)
                canvas.drawText("${events.size}", marginLeft + 250f, yPos + 12f, tableValuePaint)
                val lastTime = dateTimeFormat.format(Date(events.first().detectedAt))
                canvas.drawText(lastTime, marginLeft + 350f, yPos + 12f, tableValuePaint)
                yPos += 18f
            }
            yPos += 8f
            canvas.drawText("Total: ${alerts.size} alertas", marginLeft, yPos + 12f, tableLabelPaint)
            yPos += 20f
        }

        canvas.drawLine(marginLeft, yPos, marginRight, yPos, linePaint)
        yPos += 10f

        yPos = drawSectionTitle(canvas, "Metricas de Salud Vocal", marginLeft, yPos + 10f)

        if (metrics.isEmpty()) {
            canvas.drawText("Sin muestras vocales registradas en este periodo.", marginLeft, yPos + 16f, bodyPaint)
            yPos += 30f
        } else {
            val byDay = metrics.groupBy { dateFormat.format(Date(it.recordedAt)) }

            yPos += 8f
            canvas.drawText("Dia", marginLeft, yPos + 12f, tableLabelPaint)
            canvas.drawText("Muestras", marginLeft + 120f, yPos + 12f, tableLabelPaint)
            canvas.drawText("Jitter prom.", marginLeft + 210f, yPos + 12f, tableLabelPaint)
            canvas.drawText("Shimmer prom.", marginLeft + 320f, yPos + 12f, tableLabelPaint)
            canvas.drawText("Pitch prom.", marginLeft + 430f, yPos + 12f, tableLabelPaint)
            yPos += 16f
            canvas.drawLine(marginLeft, yPos, marginRight, yPos, linePaint)
            yPos += 4f

            for ((day, entries) in byDay) {
                if (yPos > pageHeight - 100f) {
                    drawFooter(canvas, marginLeft, marginRight, pageHeight.toFloat(), pageNumber)
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(pageNumber - 1)
                    canvas = page.canvas
                    yPos = drawHeader(canvas, marginLeft, marginRight, dateRange)
                    yPos += 20f
                }

                val avgJitter = entries.map { it.jitterPercent }.average()
                val avgShimmer = entries.map { it.shimmerPercent }.average()
                val avgPitch = entries.map { it.pitchHz }.average()

                canvas.drawText(day, marginLeft, yPos + 12f, tableValuePaint)
                canvas.drawText("${entries.size}", marginLeft + 120f, yPos + 12f, tableValuePaint)
                canvas.drawText("%.2f%%".format(avgJitter), marginLeft + 210f, yPos + 12f, tableValuePaint)
                canvas.drawText("%.2f%%".format(avgShimmer), marginLeft + 320f, yPos + 12f, tableValuePaint)
                canvas.drawText("%.0f Hz".format(avgPitch), marginLeft + 430f, yPos + 12f, tableValuePaint)
                yPos += 18f
            }

            yPos += 8f
            val overallJitter = metrics.map { it.jitterPercent }.average()
            val overallShimmer = metrics.map { it.shimmerPercent }.average()
            canvas.drawText(
                "Promedio general: Jitter %.2f%% · Shimmer %.2f%% (%d muestras)".format(
                    overallJitter, overallShimmer, metrics.size
                ),
                marginLeft, yPos + 12f, tableLabelPaint
            )
            yPos += 20f
        }

        drawFooter(canvas, marginLeft, marginRight, pageHeight.toFloat(), pageNumber)
        document.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports")
        reportsDir.mkdirs()
        val fileName = "NaturaSonic_Reporte_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
        val file = File(reportsDir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return file
    }

    fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NaturaSonic — Reporte de Bienestar")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun drawHeader(canvas: Canvas, left: Float, right: Float, dateRange: String): Float {
        var y = 40f
        canvas.drawText("NaturaSonic — Reporte de Bienestar Auditivo", left, y, headerPaint)
        y += 16f
        canvas.drawText("Periodo: $dateRange", left, y, bodyPaint)
        y += 14f
        canvas.drawText(DISCLAIMER_SHORT, left, y, disclaimerPaint)
        y += 10f
        canvas.drawLine(left, y, right, y, linePaint)
        return y + 8f
    }

    private fun drawFooter(canvas: Canvas, left: Float, right: Float, pageHeight: Float, pageNum: Int) {
        val footerY = pageHeight - 50f
        canvas.drawLine(left, footerY, right, footerY, linePaint)
        canvas.drawText(DISCLAIMER_FULL_LINE1, left, footerY + 12f, disclaimerPaint)
        canvas.drawText(DISCLAIMER_FULL_LINE2, left, footerY + 22f, disclaimerPaint)
        canvas.drawText(
            "Generado: ${dateTimeFormat.format(Date())}  ·  Pagina $pageNum",
            left, footerY + 34f, disclaimerPaint
        )
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, x: Float, y: Float): Float {
        canvas.drawText(title, x, y, titlePaint)
        return y + 6f
    }

    private fun sevenDaysAgoMillis(): Long =
        System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

    companion object {
        private const val DISCLAIMER_SHORT =
            "PSAP — Producto de amplificacion personal. No es un dispositivo medico."

        private const val DISCLAIMER_FULL_LINE1 =
            "NaturaSonic es un producto PSAP (Personal Sound Amplification Product). NO es un dispositivo medico"

        private const val DISCLAIMER_FULL_LINE2 =
            "ni una herramienta de diagnostico clinico. Consulte a un profesional de salud para evaluacion medica."
    }
}
