package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PdfDocumentItem
import com.example.data.model.SavedSignature
import com.example.data.model.SignaturePlacement
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.ui.components.SignatureDrawingDialog
import com.example.ui.theme.ToolSignColor
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.ToolOperationState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    document: PdfDocumentItem?,
    signaturesState: UiState<List<SavedSignature>>,
    pdfEngine: PdfEngineService,
    toolsViewModel: PdfToolsViewModel,
    onSaveNewSignature: (name: String, inkColorHex: String, bitmap: Bitmap, onSaved: (String) -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenSignedDocument: (String) -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    val signatures = (signaturesState as? UiState.Success)?.data ?: emptyList()
    var selectedSignatureId by remember { mutableStateOf<String?>(null) }
    var showCreateSignature by remember { mutableStateOf(false) }

    // Normalized coordinates [0..1] on the PDF page
    var xFraction by remember { mutableFloatStateOf(0.52f) }
    var yFraction by remember { mutableFloatStateOf(0.70f) }
    var widthFraction by remember { mutableFloatStateOf(0.32f) }

    val operationState by toolsViewModel.operationState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        toolsViewModel.resetState()
    }

    LaunchedEffect(signatures) {
        if (selectedSignatureId == null && signatures.isNotEmpty()) {
            selectedSignatureId = signatures.first().id
        }
    }

    LaunchedEffect(document?.localPath, currentPage) {
        val path = document?.localPath ?: return@LaunchedEffect
        isLoadingPage = true
        totalPages = pdfEngine.getPdfPageCount(path).coerceAtLeast(1)
        pageBitmap = pdfEngine.renderPdfPage(path, currentPage, targetWidthPx = 1280)
        isLoadingPage = false
    }

    val activeSignature = remember(signatures, selectedSignatureId) {
        signatures.firstOrNull { it.id == selectedSignatureId }
    }
    val activeSignatureBitmap = remember(activeSignature?.imageBase64) {
        activeSignature?.let { PdfEngineService.decodeBase64ToBitmap(it.imageBase64) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Firmar PDF",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = document?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    val isBusy = operationState is ToolOperationState.Processing
                    Button(
                        onClick = {
                            val doc = document ?: return@Button
                            val sigBmp = activeSignatureBitmap ?: return@Button
                            toolsViewModel.executeSignPdf(
                                sourcePath = doc.localPath,
                                signatureBitmap = sigBmp,
                                placement = SignaturePlacement(
                                    pageIndex = currentPage,
                                    xFraction = xFraction,
                                    yFraction = yFraction,
                                    widthFraction = widthFraction
                                ),
                                outputTitle = "${doc.title}_firmado",
                                folderId = doc.folderId
                            )
                        },
                        enabled = document != null && activeSignatureBitmap != null && !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .testTag("apply_signature_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Page Switcher & Scale Slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Página anterior")
                            }
                            Text(
                                text = "Pág ${currentPage + 1}/$totalPages",
                                style = MaterialTheme.typography.labelLarge
                            )
                            IconButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                enabled = currentPage < totalPages - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Página siguiente")
                            }
                        }

                        if (activeSignatureBitmap != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            ) {
                                Text("Tamaño", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = widthFraction,
                                    onValueChange = { widthFraction = it.coerceIn(0.12f, 0.75f) },
                                    valueRange = 0.12f..0.75f,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    // Saved Signatures Tray
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            OutlinedButton(
                                onClick = { showCreateSignature = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("sign_pdf_new_sig_button")
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nueva")
                            }
                        }

                        items(signatures, key = { it.id }) { sig ->
                            val isSelected = sig.id == selectedSignatureId
                            val thumb = remember(sig.imageBase64) {
                                PdfEngineService.decodeBase64ToBitmap(sig.imageBase64)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .height(54.dp)
                                    .width(110.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedSignatureId = sig.id }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = sig.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingPage) {
                CircularProgressIndicator()
            } else {
                val bmp = pageBitmap
                if (bmp == null) {
                    Text("No se pudo mostrar la página del PDF.")
                } else {
                    val pageAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                    val density = LocalDensity.current

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val containerW = constraints.maxWidth.toFloat()
                        val containerH = constraints.maxHeight.toFloat()
                        val boxW: Float
                        val boxH: Float
                        if (containerW / containerH > pageAspect) {
                            boxH = containerH
                            boxW = containerH * pageAspect
                        } else {
                            boxW = containerW
                            boxH = containerW / pageAspect
                        }

                        val boxWDp = with(density) { boxW.toDp() }
                        val boxHDp = with(density) { boxH.toDp() }

                        Surface(
                            shadowElevation = 6.dp,
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(boxWDp, boxHDp)
                                .aspectRatio(pageAspect)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Página para firmar",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Draggable Signature Overlay
                                if (activeSignatureBitmap != null) {
                                    val sigAspect = activeSignatureBitmap.height.toFloat() /
                                        activeSignatureBitmap.width.toFloat().coerceAtLeast(1f)
                                    val sigPxW = widthFraction * boxW
                                    val sigPxH = sigPxW * sigAspect

                                    val maxXFrac = (1f - (sigPxW / boxW)).coerceAtLeast(0f)
                                    val maxYFrac = (1f - (sigPxH / boxH)).coerceAtLeast(0f)
                                    val clampedX = xFraction.coerceIn(0f, maxXFrac)
                                    val clampedY = yFraction.coerceIn(0f, maxYFrac)

                                    val sigWDp = with(density) { sigPxW.toDp() }
                                    val sigHDp = with(density) { sigPxH.toDp() }

                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (clampedX * boxW).roundToInt(),
                                                    (clampedY * boxH).roundToInt()
                                                )
                                            }
                                            .size(sigWDp, sigHDp)
                                            .border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .pointerInput(boxW, boxH, widthFraction) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    xFraction = (xFraction + dragAmount.x / boxW)
                                                        .coerceIn(0f, maxXFrac)
                                                    yFraction = (yFraction + dragAmount.y / boxH)
                                                        .coerceIn(0f, maxYFrac)
                                                }
                                            }
                                    ) {
                                        Image(
                                            bitmap = activeSignatureBitmap.asImageBitmap(),
                                            contentDescription = "Firma colocada",
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.OpenWith,
                                            contentDescription = "Arrastrar firma",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(16.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .clickable { showCreateSignature = true }
                                    ) {
                                        Text(
                                            text = "Toca \"+ Nueva\" abajo para dibujar tu firma",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSignature) {
        SignatureDrawingDialog(
            onDismiss = { showCreateSignature = false },
            onSaveSignature = { name, hex, bmp ->
                onSaveNewSignature(name, hex, bmp) { createdId ->
                    selectedSignatureId = createdId
                }
                showCreateSignature = false
            }
        )
    }

    when (val state = operationState) {
        is ToolOperationState.Completed -> {
            AlertDialog(
                onDismissRequest = { toolsViewModel.resetState() },
                icon = {
                    Icon(Icons.Filled.CheckCircle, null, tint = ToolSignColor, modifier = Modifier.size(36.dp))
                },
                title = { Text("PDF firmado") },
                text = { Text("Se guardó \"${state.title}\" con tu firma estampada.") },
                confirmButton = {
                    Button(onClick = {
                        val docId = state.documentId
                        toolsViewModel.resetState()
                        onOpenSignedDocument(docId)
                    }) {
                        Text("Abrir PDF firmado")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        toolsViewModel.resetState()
                        onBack()
                    }) {
                        Text("Volver")
                    }
                }
            )
        }
        is ToolOperationState.Failed -> {
            AlertDialog(
                onDismissRequest = { toolsViewModel.resetState() },
                title = { Text("Error al firmar") },
                text = { Text(state.errorMessage) },
                confirmButton = {
                    Button(onClick = { toolsViewModel.resetState() }) { Text("Entendido") }
                }
            )
        }
        else -> Unit
    }
}
