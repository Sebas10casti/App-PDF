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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DocumentSortOrder
import com.example.data.model.FolderItem
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.model.UiState
import com.example.ui.components.DocumentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FolderDialog
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.parseHexColor
import com.example.ui.theme.FavoriteGold

@Composable
fun FilesOrganizerScreen(
    searchQuery: String,
    selectedFolderId: String?,
    selectedCategory: PdfCategory?,
    favoritesOnly: Boolean,
    sortOrder: DocumentSortOrder,
    foldersState: UiState<List<FolderItem>>,
    filteredDocsState: UiState<List<PdfDocumentItem>>,
    onSearchChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectCategory: (PdfCategory?) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onSortChange: (DocumentSortOrder) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onUpdateFolder: (String, String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onImportPdf: (Uri) -> Unit,
    onRelinkPdf: (String, Uri) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenSigner: (String) -> Unit,
    onToggleFavorite: (PdfDocumentItem) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onMoveDocument: (String, String?) -> Unit,
    onShareDocument: (PdfDocumentItem) -> Unit,
    onExportDocument: (PdfDocumentItem) -> Unit,
    onDeleteDocument: (PdfDocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateFolder by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderItem?>(null) }
    var docToRename by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var docToMove by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var relinkTargetId by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<FolderItem?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPdf(uri)
    }

    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = relinkTargetId
        relinkTargetId = null
        if (uri != null && id != null) onRelinkPdf(id, uri)
    }

    val folders = (foldersState as? UiState.Success)?.data ?: emptyList()
    val folderMap = remember(folders) { folders.associateBy { it.id } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            // Top Header & Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Archivos",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(sortOrder.labelEs)
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DocumentSortOrder.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.labelEs) },
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSortChange(option)
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { showCreateFolder = true },
                            modifier = Modifier.testTag("new_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = stringResource(R.string.action_new_folder),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Buscar documentos por nombre...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_documents_input")
                )
            }

            // Horizontal Folders Strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectFolder(null) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedFolderId == null) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "Todas las carpetas",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedFolderId == null) MaterialTheme.colorScheme.onSecondary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                items(folders, key = { it.id }) { folder ->
                    val isSelected = selectedFolderId == folder.id
                    val folderColor = parseHexColor(folder.colorHex)
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectFolder(folder.id) }
                                .testTag("folder_chip_${folder.id}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) folderColor.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) folderColor else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = folderColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { folderMenuTarget = folder },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Gestionar carpeta",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = folderMenuTarget?.id == folder.id,
                            onDismissRequest = { folderMenuTarget = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar carpeta") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = {
                                    folderMenuTarget = null
                                    folderToEdit = folder
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar carpeta", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    folderMenuTarget = null
                                    onDeleteFolder(folder.id)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category & Favorites Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = onToggleFavoritesFilter,
                        label = { Text("Favoritos") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (favoritesOnly) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FavoriteGold.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.testTag("filter_favorites_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("Todos") }
                    )
                }

                items(PdfCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            onSelectCategory(if (selectedCategory == category) null else category)
                        },
                        label = { Text(category.labelEs) },
                        modifier = Modifier.testTag("filter_cat_${category.firestoreValue}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Documents List
            when (filteredDocsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.padding(20.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.FolderOpen,
                            title = "Error al cargar archivos",
                            subtitle = filteredDocsState.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (filteredDocsState.data.isEmpty()) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            EmptyStateCard(
                                icon = Icons.Filled.FolderOpen,
                                title = "Sin documentos aquí",
                                subtitle = if (searchQuery.isNotBlank() || favoritesOnly || selectedCategory != null || selectedFolderId != null)
                                    "Prueba cambiando los filtros o la búsqueda."
                                else
                                    "Importa un PDF o crea uno desde la pestaña Inicio.",
                                actionLabel = "Importar PDF",
                                onAction = { importLauncher.launch(arrayOf("application/pdf")) }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 92.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredDocsState.data, key = { it.id }) { doc ->
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

        FloatingActionButton(
            onClick = { importLauncher.launch(arrayOf("application/pdf")) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("files_import_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_import_pdf))
        }
    }

    if (showCreateFolder) {
        FolderDialog(
            onDismiss = { showCreateFolder = false },
            onConfirm = { name, colorHex ->
                onCreateFolder(name, colorHex)
                showCreateFolder = false
            }
        )
    }

    folderToEdit?.let { folder ->
        FolderDialog(
            initialName = folder.name,
            initialColorHex = folder.colorHex,
            isEditing = true,
            onDismiss = { folderToEdit = null },
            onConfirm = { name, colorHex ->
                onUpdateFolder(folder.id, name, colorHex)
                folderToEdit = null
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
            onSelectFolder = { targetId ->
                onMoveDocument(doc.id, targetId)
                docToMove = null
            }
        )
    }
}
