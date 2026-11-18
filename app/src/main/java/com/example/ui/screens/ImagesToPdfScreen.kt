package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FolderItem
import com.example.data.model.MarginOption
import com.example.data.model.PageSizeOption
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.parseHexColor
import com.example.ui.theme.ToolSignColor
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.ToolOperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    toolsViewModel: PdfToolsViewModel,
    foldersState: UiState<List<FolderItem>>,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit
) {
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var outputTitle by remember { mutableStateOf("Imagenes_PDF") }
    var pageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var margin by remember { mutableStateOf(MarginOption.STANDARD) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }

    val operationState by toolsViewModel.operationState.collectAsStateWithLifecycle()

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
        }
    }

    LaunchedEffect(Unit) {
        toolsViewModel.resetState()
    }

    val folders = (foldersState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PNG / JPEG a PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_images_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar imágenes PNG / JPEG (${selectedImages.size})")
                }

                if (selectedImages.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Filled.Image,
                        title = "Convierte fotos e imágenes en PDF",
                        subtitle = "Selecciona varias imágenes PNG o JPEG, ordénalas y ajusta el tamaño de página.",
                        actionLabel = "Elegir imágenes",
                        onAction = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(selectedImages) { index, uri ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Imagen ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Página ${index + 1}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Imagen PNG / JPEG",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val item = selectedImages.removeAt(index)
                                                selectedImages.add(index - 1, item)
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < selectedImages.lastIndex) {
                                                val item = selectedImages.removeAt(index)
                                                selectedImages.add(index + 1, item)
                                            }
                                        },
                                        enabled = index < selectedImages.lastIndex
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar")
                                    }
                                    IconButton(onClick = { selectedImages.removeAt(index) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Quitar",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Page Size & Margin Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageSizeOption.entries.forEach { option ->
                            FilterChip(
                                selected = pageSize == option,
                                onClick = { pageSize = option },
                                label = { Text(option.labelEs) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarginOption.entries.forEach { option ->
                            FilterChip(
                                selected = margin == option,
                                onClick = { margin = option },
                                label = { Text(option.labelEs) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { if (it.length <= 120) outputTitle = it },
                        label = { Text("Nombre del PDF") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (folders.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedFolderId == null,
                                    onClick = { selectedFolderId = null },
                                    label = { Text("Raíz") }
                                )
                            }
                            items(folders, key = { it.id }) { folder ->
                                FilterChip(
                                    selected = selectedFolderId == folder.id,
                                    onClick = { selectedFolderId = folder.id },
                                    label = { Text(folder.name) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Folder,
                                            null,
                                            tint = parseHexColor(folder.colorHex),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    val isBusy = operationState is ToolOperationState.Processing
                    Button(
                        onClick = {
                            toolsViewModel.executeImagesToPdf(
                                imageUris = selectedImages.toList(),
                                outputTitle = outputTitle,
                                pageSizeOption = pageSize,
                                marginOption = margin,
                                folderId = selectedFolderId
                            )
                        },
                        enabled = selectedImages.isNotEmpty() && outputTitle.trim().isNotEmpty() && !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("convert_images_to_pdf_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text((operationState as ToolOperationState.Processing).message)
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Crear PDF (${selectedImages.size} páginas)")
                        }
                    }
                }
            }
        }
    }

    when (val state = operationState) {
        is ToolOperationState.Completed -> {
            AlertDialog(
                onDismissRequest = { toolsViewModel.resetState() },
                icon = {
                    Icon(Icons.Filled.CheckCircle, null, tint = ToolSignColor, modifier = Modifier.size(36.dp))
                },
                title = { Text("PDF creado correctamente") },
                text = { Text("\"${state.title}\" se convirtió y guardó en tu biblioteca.") },
                confirmButton = {
                    Button(onClick = {
                        val docId = state.documentId
                        selectedImages.clear()
                        toolsViewModel.resetState()
                        onOpenDocument(docId)
                    }) {
                        Text("Abrir en lector")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        selectedImages.clear()
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
                title = { Text("Error de conversión") },
                text = { Text(state.errorMessage) },
                confirmButton = {
                    Button(onClick = { toolsViewModel.resetState() }) { Text("Entendido") }
                }
            )
        }
        else -> Unit
    }
}
