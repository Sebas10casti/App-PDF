package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PdfDocumentItem
import com.example.data.pdf.PdfEngineService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    document: PdfDocumentItem?,
    pdfEngine: PdfEngineService,
    onBack: () -> Unit,
    onMarkOpened: (String) -> Unit,
    onSignDocument: (String) -> Unit,
    onShareDocument: (PdfDocumentItem) -> Unit,
    onExportDocument: (PdfDocumentItem) -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var nightPaperMode by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(document?.id) {
        if (document != null) {
            onMarkOpened(document.id)
            totalPages = pdfEngine.getPdfPageCount(document.localPath).coerceAtLeast(1)
        }
    }

    LaunchedEffect(document?.localPath, currentPage) {
        val path = document?.localPath ?: return@LaunchedEffect
        isLoadingPage = true
        scale = 1f
        offset = Offset.Zero
        renderedBitmap = pdfEngine.renderPdfPage(path, currentPage, targetWidthPx = 1600)
        isLoadingPage = false
    }

    val invertColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.9f, 0f, 0f, 0f, 240f,
                    0f, -0.9f, 0f, 0f, 240f,
                    0f, 0f, -0.9f, 0f, 240f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document?.title ?: "Lector PDF",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Página ${currentPage + 1} de $totalPages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { nightPaperMode = !nightPaperMode }) {
                        Icon(
                            imageVector = if (nightPaperMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.cd_night_reading_mode)
                        )
                    }
                    if (document != null) {
                        IconButton(onClick = { onSignDocument(document.id) }) {
                            Icon(Icons.Filled.Draw, contentDescription = "Firmar PDF")
                        }
                        IconButton(onClick = { onShareDocument(document) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_pdf))
                        }
                        IconButton(onClick = { onExportDocument(document) }) {
                            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_export_pdf))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Página anterior")
                    }

                    if (totalPages > 1) {
                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { currentPage = it.roundToInt().coerceIn(0, totalPages - 1) },
                            valueRange = 0f..(totalPages - 1).toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    } else {
                        Text(
                            text = "Página única",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Página siguiente")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (nightPaperMode) Color(0xFF101216) else MaterialTheme.colorScheme.surfaceVariant)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                        scale = newScale
                        offset = if (newScale > 1.01f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingPage) {
                CircularProgressIndicator()
            } else {
                val bmp = renderedBitmap
                if (bmp == null) {
                    Text(
                        text = "No se pudo cargar este documento PDF.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Surface(
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .padding(12.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Página ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            colorFilter = if (nightPaperMode) invertColorFilter else null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
