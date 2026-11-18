package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.MarginOption
import com.example.data.model.PageSizeOption
import com.example.data.model.SignaturePlacement
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ProcessedPdfResult(
    val title: String,
    val fileName: String,
    val localPath: String,
    val pageCount: Long,
    val fileSizeBytes: Long
)

data class PdfInputCandidate(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int,
    val fileSizeBytes: Long
)

class PdfEngineService(private val context: Context) {

    private val appContext = context.applicationContext

    private fun ensurePdfStorageDir(): File {
        val dir = File(appContext.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun createUniquePdfFile(baseTitle: String): File {
        val sanitized = baseTitle
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_\\- ]"), "")
            .trim()
            .replace(" ", "_")
            .take(40)
            .ifEmpty { "documento" }
        val shortId = UUID.randomUUID().toString().take(6)
        return File(ensurePdfStorageDir(), "${sanitized}_$shortId.pdf")
    }

    suspend fun inspectPdfUri(uri: Uri): PdfInputCandidate = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "Documento.pdf"
        val size = queryFileSize(uri)
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("No se pudo abrir el archivo PDF seleccionado.")
        val pages = pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
        PdfInputCandidate(
            uri = uri,
            displayName = displayName,
            pageCount = pages.coerceAtLeast(1),
            fileSizeBytes = size
        )
    }

    suspend fun importPdfFromUri(uri: Uri): ProcessedPdfResult = withContext(Dispatchers.IO) {
        val rawName = queryDisplayName(uri) ?: "Documento_importado.pdf"
        val title = rawName.removeSuffix(".pdf").removeSuffix(".PDF").trim().ifEmpty { "Documento" }
        val destFile = createUniquePdfFile(title)

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("No se pudo leer el PDF seleccionado.")

        val pageCount = countPagesInternal(destFile)
        ProcessedPdfResult(
            title = title,
            fileName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$title.pdf",
            localPath = destFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = destFile.length()
        )
    }

    suspend fun mergePdfs(
        sources: List<PdfInputCandidate>,
        outputTitle: String
    ): ProcessedPdfResult = withContext(Dispatchers.IO) {
        require(sources.size >= 2) { "Selecciona al menos 2 archivos PDF para unir." }
        val cleanTitle = outputTitle.trim().ifEmpty { "PDF_Unido" }
        val destFile = createUniquePdfFile(cleanTitle)
        val tempInputs = mutableListOf<File>()

        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = destFile.absolutePath

            sources.forEachIndexed { index, candidate ->
                val tempFile = File(appContext.cacheDir, "merge_src_${index}_${UUID.randomUUID()}.pdf")
                appContext.contentResolver.openInputStream(candidate.uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("No se pudo leer ${candidate.displayName}")
                tempInputs.add(tempFile)
                merger.addSource(tempFile)
            }

            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())

            val pages = countPagesInternal(destFile)
            ProcessedPdfResult(
                title = cleanTitle,
                fileName = "$cleanTitle.pdf",
                localPath = destFile.absolutePath,
                pageCount = pages,
                fileSizeBytes = destFile.length()
            )
        } finally {
            tempInputs.forEach { runCatching { it.delete() } }
        }
    }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputTitle: String,
        pageSizeOption: PageSizeOption,
        marginOption: MarginOption
    ): ProcessedPdfResult = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Selecciona al menos una imagen PNG o JPEG." }
        val cleanTitle = outputTitle.trim().ifEmpty { "Imagenes_PDF" }
        val destFile = createUniquePdfFile(cleanTitle)

        PDDocument().use { document ->
            for (uri in imageUris) {
                val bitmap = decodeOrientedBitmap(uri, maxDimension = 2000)
                    ?: throw IllegalStateException("No se pudo decodificar una de las imágenes.")
                try {
                    val pageRect = when (pageSizeOption) {
                        PageSizeOption.A4 -> PDRectangle.A4
                        PageSizeOption.LETTER -> PDRectangle.LETTER
                        PageSizeOption.FIT_IMAGE -> {
                            val maxPt = 842f
                            val scale = if (bitmap.width > maxPt || bitmap.height > maxPt) {
                                minOf(maxPt / bitmap.width, maxPt / bitmap.height)
                            } else 1f
                            PDRectangle(bitmap.width * scale, bitmap.height * scale)
                        }
                    }

                    val marginPts = when (marginOption) {
                        MarginOption.NONE -> 0f
                        MarginOption.STANDARD -> if (pageSizeOption == PageSizeOption.FIT_IMAGE) 20f else 36f
                    }

                    val page = PDPage(pageRect)
                    document.addPage(page)

                    val pdImage = LosslessFactory.createFromImage(document, bitmap)
                    val availW = (pageRect.width - 2f * marginPts).coerceAtLeast(10f)
                    val availH = (pageRect.height - 2f * marginPts).coerceAtLeast(10f)
                    val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                    val boxRatio = availW / availH

                    val drawW: Float
                    val drawH: Float
                    if (imgRatio > boxRatio) {
                        drawW = availW
                        drawH = availW / imgRatio
                    } else {
                        drawH = availH
                        drawW = availH * imgRatio
                    }

                    val drawX = (pageRect.width - drawW) / 2f
                    val drawY = (pageRect.height - drawH) / 2f

                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(pdImage, drawX, drawY, drawW, drawH)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            document.save(destFile)
        }

        val pages = countPagesInternal(destFile)
        ProcessedPdfResult(
            title = cleanTitle,
            fileName = "$cleanTitle.pdf",
            localPath = destFile.absolutePath,
            pageCount = pages,
            fileSizeBytes = destFile.length()
        )
    }

    suspend fun stampSignatureOnPdf(
        sourcePath: String,
        signatureBitmap: Bitmap,
        placement: SignaturePlacement,
        outputTitle: String
    ): ProcessedPdfResult = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePath)
        require(sourceFile.exists()) { "El archivo PDF original no está disponible en este dispositivo." }

        val cleanTitle = outputTitle.trim().ifEmpty { "Documento_Firmado" }
        val destFile = createUniquePdfFile(cleanTitle)

        PDDocument.load(sourceFile).use { document ->
            val pageIndex = placement.pageIndex.coerceIn(0, (document.numberOfPages - 1).coerceAtLeast(0))
            val page = document.getPage(pageIndex)
            val box = page.cropBox ?: page.mediaBox
            val rotation = ((page.rotation % 360) + 360) % 360

            // Visual dimensions after rotation
            val visualW = if (rotation == 90 || rotation == 270) box.height else box.width
            val visualH = if (rotation == 90 || rotation == 270) box.width else box.height

            val sigRatio = signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat().coerceAtLeast(1f)
            val drawW = (placement.widthFraction.coerceIn(0.10f, 0.85f)) * visualW
            val drawH = drawW * sigRatio

            val clampedXFrac = placement.xFraction.coerceIn(0f, (1f - (drawW / visualW)).coerceAtLeast(0f))
            val clampedYFrac = placement.yFraction.coerceIn(0f, (1f - (drawH / visualH)).coerceAtLeast(0f))

            // Top-left visual coordinates -> bottom-left visual coordinates
            val visualX = clampedXFrac * visualW
            val visualYBottom = visualH - (clampedYFrac * visualH) - drawH

            val pdImage = LosslessFactory.createFromImage(document, signatureBitmap)
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                when (rotation) {
                    90 -> {
                        val matrix = com.tom_roush.pdfbox.util.Matrix(
                            0f, 1f, -1f, 0f,
                            box.lowerLeftX + box.width - visualYBottom,
                            box.lowerLeftY + visualX
                        )
                        stream.saveGraphicsState()
                        stream.transform(matrix)
                        stream.drawImage(pdImage, 0f, 0f, drawW, drawH)
                        stream.restoreGraphicsState()
                    }
                    180 -> {
                        val matrix = com.tom_roush.pdfbox.util.Matrix(
                            -1f, 0f, 0f, -1f,
                            box.lowerLeftX + box.width - visualX,
                            box.lowerLeftY + box.height - visualYBottom
                        )
                        stream.saveGraphicsState()
                        stream.transform(matrix)
                        stream.drawImage(pdImage, 0f, 0f, drawW, drawH)
                        stream.restoreGraphicsState()
                    }
                    270 -> {
                        val matrix = com.tom_roush.pdfbox.util.Matrix(
                            0f, -1f, 1f, 0f,
                            box.lowerLeftX + visualYBottom,
                            box.lowerLeftY + box.height - visualX
                        )
                        stream.saveGraphicsState()
                        stream.transform(matrix)
                        stream.drawImage(pdImage, 0f, 0f, drawW, drawH)
                        stream.restoreGraphicsState()
                    }
                    else -> {
                        stream.drawImage(
                            pdImage,
                            box.lowerLeftX + visualX,
                            box.lowerLeftY + visualYBottom,
                            drawW,
                            drawH
                        )
                    }
                }
            }
            document.save(destFile)
        }

        val pages = countPagesInternal(destFile)
        ProcessedPdfResult(
            title = cleanTitle,
            fileName = "$cleanTitle.pdf",
            localPath = destFile.absolutePath,
            pageCount = pages,
            fileSizeBytes = destFile.length()
        )
    }

    suspend fun renderPdfPage(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1440
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return@withContext null
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val width = targetWidthPx.coerceIn(320, 2200)
                    val height = ((page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)) * width)
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    suspend fun getPdfPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext 0
        countPagesInternal(file).toInt()
    }

    suspend fun exportPdfToUri(sourcePath: String, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val file = File(sourcePath)
        require(file.exists()) { "El archivo PDF no existe en el dispositivo." }
        appContext.contentResolver.openOutputStream(destinationUri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("No se pudo escribir en la ubicación seleccionada.")
    }

    fun deleteLocalPdfFile(localPath: String) {
        if (localPath.isBlank()) return
        runCatching {
            val file = File(localPath)
            if (file.exists() && file.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                file.delete()
            }
        }
    }

    fun createShareIntent(localPath: String, title: String): Intent {
        val file = File(localPath)
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun countPagesInternal(file: File): Long {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer -> renderer.pageCount.toLong().coerceAtLeast(1L) }
        }
    }

    private fun decodeOrientedBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / sampleSize > maxDimension || rawH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val orientation = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return decoded

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun queryFileSize(uri: Uri): Long {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
    }

    companion object {
        fun encodeBitmapToBase64(bitmap: Bitmap): String {
            // Scale cleanly to max 520px width so PNG stays well under Firestore's 300KB limit
            val maxW = 520
            val scaled = if (bitmap.width > maxW) {
                val h = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * maxW).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, maxW, h, true)
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
            if (scaled != bitmap) scaled.recycle()
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        fun decodeBase64ToBitmap(base64: String): Bitmap? {
            if (base64.isBlank()) return null
            return runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> String.format("%.1f MB", bytes / (1024f * 1024f))
            }
        }
    }
}
