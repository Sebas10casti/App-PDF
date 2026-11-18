package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FolderItem
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.data.pdf.PdfInputCandidate
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.parseHexColor
import com.example.ui.theme.ToolMergeColor
import com.example.ui.theme.ToolSignColor
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.ToolOperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    toolsViewModel: PdfToolsViewModel,
    foldersState: UiState<List<FolderItem>>,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit
) {
    val candidates by toolsViewModel.mergeCandidates.collectAsStateWithLifecycle()
    val operationState by toolsViewModel.operationState.collectAsStateWithLifecycle()

    var outputTitle by remember { mutableStateOf("Documento_Unido") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            toolsViewModel.addMergeUris(uris)
        }
    }

    LaunchedEffect(Unit) {
        toolsViewModel.resetState()
    }

    val folders = (foldersState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unir PDFs") },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_pdfs_to_merge_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir archivos PDF (${candidates.size})")
                }

                if (candidates.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Filled.Layers,
                        title = "Selecciona 2 o más PDFs",
                        subtitle = "Podrás reordenar la secuencia exacta antes de generar el documento combinado.",
                        actionLabel = "Elegir archivos PDF",
                        onAction = { multiPdfPicker.launch(arrayOf("application/pdf")) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(candidates) { index, item ->
                            MergeCandidateRow(
                                index = index,
                                total = candidates.size,
                                item = item,
                                onMoveUp = { toolsViewModel.moveMergeItemUp(index) },
                                onMoveDown = { toolsViewModel.moveMergeItemDown(index) },
                                onRemove = { toolsViewModel.removeMergeItem(index) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { if (it.length <= 120) outputTitle = it },
                        label = { Text("Nombre del PDF resultante") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (folders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Guardar en carpeta", style = MaterialTheme.typography.labelMedium)
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
                    }

                    val isBusy = operationState is ToolOperationState.Processing
                    Button(
                        onClick = { toolsViewModel.executeMerge(outputTitle, selectedFolderId) },
                        enabled = candidates.size >= 2 && outputTitle.trim().isNotEmpty() && !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_merge_button"),
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
                            Icon(Icons.Filled.Layers, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unir ${candidates.size} PDFs")
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
                title = { Text("PDF unido correctamente") },
                text = { Text("\"${state.title}\" ya está guardado en tu biblioteca.") },
                confirmButton = {
                    Button(onClick = {
                        val docId = state.documentId
                        toolsViewModel.resetState()
                        onOpenDocument(docId)
                    }) {
                        Text("Abrir en lector")
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
                title = { Text("No se pudo unir") },
                text = { Text(state.errorMessage) },
                confirmButton = {
                    Button(onClick = { toolsViewModel.resetState() }) { Text("Entendido") }
                }
            )
        }
        else -> Unit
    }
}

@Composable
private fun MergeCandidateRow(
    index: Int,
    total: Int,
    item: PdfInputCandidate,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ToolMergeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ToolMergeColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.pageCount} pág • ${PdfEngineService.formatFileSize(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir")
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
