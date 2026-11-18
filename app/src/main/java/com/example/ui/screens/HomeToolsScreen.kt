package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FolderItem
import com.example.data.model.PdfDocumentItem
import com.example.data.model.UiState
import com.example.ui.components.DocumentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.RenameDialog
import com.example.ui.theme.ToolImagesColor
import com.example.ui.theme.ToolMergeColor
import com.example.ui.theme.ToolReaderColor
import com.example.ui.theme.ToolSignColor
import java.io.File

@Composable
fun HomeToolsScreen(
    userName: String?,
    documentsState: UiState<List<PdfDocumentItem>>,
    foldersState: UiState<List<FolderItem>>,
    onNavigateMerge: () -> Unit,
    onNavigateImagesToPdf: () -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenSigner: (String) -> Unit,
    onNavigateFiles: () -> Unit,
    onImportPdf: (Uri, (String) -> Unit) -> Unit,
    onRelinkPdf: (String, Uri) -> Unit,
    onToggleFavorite: (PdfDocumentItem) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onMoveDocument: (String, String?) -> Unit,
    onShareDocument: (PdfDocumentItem) -> Unit,
    onExportDocument: (PdfDocumentItem) -> Unit,
    onDeleteDocument: (PdfDocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingPickAction by remember { mutableStateOf<String?>(null) } // "sign" or "read"
    var showDocChooserFor by remember { mutableStateOf<String?>(null) }
    var docToRename by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var docToMove by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var relinkTargetId by remember { mutableStateOf<String?>(null) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val action = pendingPickAction
            pendingPickAction = null
            onImportPdf(uri) { newDocId ->
                when (action) {
                    "sign" -> onOpenSigner(newDocId)
                    "read" -> onOpenReader(newDocId)
                }
            }
        } else {
            pendingPickAction = null
        }
    }

    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = relinkTargetId
        relinkTargetId = null
        if (uri != null && id != null) {
            onRelinkPdf(id, uri)
        }
    }

    val folders = (foldersState as? UiState.Success)?.data ?: emptyList()
    val folderMap = remember(folders) { folders.associateBy { it.id } }
    val allDocs = (documentsState as? UiState.Success)?.data ?: emptyList()
    val localDocs = remember(allDocs) {
        allDocs.filter { it.localPath.isNotBlank() && File(it.localPath).exists() }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Proprietary FolioPDF Studio Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FolioPDF",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val greetingName = userName?.substringBefore(" ")?.takeIf { it.isNotBlank() }
                        Text(
                            text = if (greetingName != null) "Hola, $greetingName" else "Mesa de trabajo",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            pendingPickAction = null
                            importPdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("home_import_pdf_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_pdf))
                    }
                }
            }

            // 2x2 Proprietary Studio Tool Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudioToolCard(
                            title = stringResource(R.string.tool_merge_title),
                            description = stringResource(R.string.tool_merge_desc),
                            icon = Icons.Filled.Layers,
                            accent = ToolMergeColor,
                            onClick = onNavigateMerge,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_merge_card")
                        )
                        StudioToolCard(
                            title = stringResource(R.string.tool_images_title),
                            description = stringResource(R.string.tool_images_desc),
                            icon = Icons.Filled.Image,
                            accent = ToolImagesColor,
                            onClick = onNavigateImagesToPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_images_card")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudioToolCard(
                            title = stringResource(R.string.tool_sign_title),
                            description = stringResource(R.string.tool_sign_desc),
                            icon = Icons.Filled.Draw,
                            accent = ToolSignColor,
                            onClick = {
                                if (localDocs.isEmpty()) {
                                    pendingPickAction = "sign"
                                    importPdfLauncher.launch(arrayOf("application/pdf"))
                                } else {
                                    showDocChooserFor = "sign"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_sign_card")
                        )
                        StudioToolCard(
                            title = stringResource(R.string.tool_reader_title),
                            description = stringResource(R.string.tool_reader_desc),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            accent = ToolReaderColor,
                            onClick = {
                                if (localDocs.isEmpty()) {
                                    pendingPickAction = "read"
                                    importPdfLauncher.launch(arrayOf("application/pdf"))
                                } else {
                                    showDocChooserFor = "read"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_reader_card")
                        )
                    }
                }
            }

            // Recent Activity Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Documentos recientes",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (allDocs.isNotEmpty()) {
                        TextButton(onClick = onNavigateFiles) {
                            Text("Ver todos (${allDocs.size})")
                        }
                    }
                }
            }

            when (documentsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Description,
                            title = "No se pudo sincronizar",
                            subtitle = documentsState.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (documentsState.data.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.FolderOpen,
                                title = "Tu mesa está lista",
                                subtitle = "Importa un PDF, une varios archivos o convierte fotos PNG/JPEG para empezar.",
                                actionLabel = "Importar primer PDF",
                                onAction = {
                                    pendingPickAction = null
                                    importPdfLauncher.launch(arrayOf("application/pdf"))
                                }
                            )
                        }
                    } else {
                        items(documentsState.data.take(6), key = { it.id }) { doc ->
                            DocumentCard(
                                doc = doc,
                                folderName = doc.folderId?.let { folderMap[it]?.name },
                                onOpen = { onOpenReader(doc.id) },
                                onSign = { onOpenSigner(doc.id) },
                                onToggleFavorite = { onToggleFavorite(doc) },
                                onRename = { docToRename = doc },
                                onMoveToFolder = { docToMove = doc },
                                onShare = { onShareDocument(doc) },
                                onExport = { onExportDocument(doc) },
                                onRelinkLocal = {
                                    relinkTargetId = doc.id
                                    relinkLauncher.launch(arrayOf("application/pdf"))
                                },
                                onDelete = { onDeleteDocument(doc) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDocChooserFor != null) {
        val isSigning = showDocChooserFor == "sign"
        AlertDialog(
            onDismissRequest = { showDocChooserFor = null },
            title = {
                Text(
                    text = if (isSigning) "Elige un PDF para firmar" else "Elige un PDF para leer",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val mode = showDocChooserFor
                            showDocChooserFor = null
                            pendingPickAction = mode
                            importPdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Seleccionar PDF del dispositivo")
                    }

                    localDocs.take(5).forEach { doc ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showDocChooserFor = null
                                    if (isSigning) onOpenSigner(doc.id) else onOpenReader(doc.id)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSigning) Icons.Filled.Draw else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = if (isSigning) ToolSignColor else ToolReaderColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.pageCount} páginas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDocChooserFor = null }) { Text("Cerrar") }
            }
        )
    }

    docToRename?.let { doc ->
        RenameDialog(
            title = "Renombrar documento",
            initialValue = doc.title,
            onDismiss = { docToRename = null },
            onConfirm = { newTitle ->
                onRenameDocument(doc.id, newTitle)
                docToRename = null
            }
        )
    }

    docToMove?.let { doc ->
        FolderPickerDialog(
            folders = folders,
            currentFolderId = doc.folderId,
            onDismiss = { docToMove = null },
            onSelectFolder = { targetFolderId ->
                onMoveDocument(doc.id, targetFolderId)
                docToMove = null
            }
        )
    }
}

@Composable
private fun StudioToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
