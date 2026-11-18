package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.pdf.PdfEngineService
import com.example.ui.theme.FavoriteGold
import com.example.ui.theme.ToolImagesColor
import com.example.ui.theme.ToolMergeColor
import com.example.ui.theme.ToolReaderColor
import com.example.ui.theme.ToolSignColor
import java.io.File

val FolderPaletteHexes = listOf(
    "#E84855",
    "#D9822B",
    "#1B8A6B",
    "#3B5BDB",
    "#7B4AE2",
    "#1E2430"
)

fun parseHexColor(hex: String, fallback: Color = ToolMergeColor): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

fun categoryColor(category: PdfCategory): Color = when (category) {
    PdfCategory.MERGED -> ToolMergeColor
    PdfCategory.CONVERTED -> ToolImagesColor
    PdfCategory.SIGNED -> ToolSignColor
    PdfCategory.IMPORTED -> ToolReaderColor
}

fun categoryIcon(category: PdfCategory): ImageVector = when (category) {
    PdfCategory.MERGED -> Icons.Filled.Layers
    PdfCategory.CONVERTED -> Icons.Filled.Image
    PdfCategory.SIGNED -> Icons.Filled.Draw
    PdfCategory.IMPORTED -> Icons.Filled.Description
}

@Composable
fun DocumentCard(
    doc: PdfDocumentItem,
    folderName: String?,
    onOpen: () -> Unit,
    onSign: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMoveToFolder: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRelinkLocal: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cat = doc.categoryEnum
    val accent = categoryColor(cat)
    val localExists = remember(doc.localPath) {
        doc.localPath.isNotBlank() && File(doc.localPath).exists()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}")
            .clickable {
                if (localExists) onOpen() else onRelinkLocal()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(cat),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = cat.labelEs,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${doc.pageCount} pág • ${PdfEngineService.formatFileSize(doc.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!folderName.isNullOrBlank() || !localExists) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!folderName.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!localExists) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = ToolImagesColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Solo índice • Toca para vincular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ToolImagesColor
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("favorite_btn_${doc.id}")
            ) {
                Icon(
                    imageVector = if (doc.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (doc.favorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_btn_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (localExists) {
                        DropdownMenuItem(
                            text = { Text("Abrir en lector") },
                            leadingIcon = { Icon(Icons.Filled.Description, null) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Firmar documento") },
                            leadingIcon = { Icon(Icons.Filled.Draw, null) },
                            onClick = {
                                menuExpanded = false
                                onSign()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Vincular PDF local") },
                            leadingIcon = { Icon(Icons.Filled.Description, null) },
                            onClick = {
                                menuExpanded = false
                                onRelinkLocal()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Mover a carpeta") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                        onClick = {
                            menuExpanded = false
                            onMoveToFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    if (localExists) {
                        DropdownMenuItem(
                            text = { Text("Compartir") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Guardar en dispositivo") },
                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderDialog(
    initialName: String = "",
    initialColorHex: String = FolderPaletteHexes.first(),
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Editar carpeta" else "Nueva carpeta",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text("Nombre de la carpeta") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Color identificador",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FolderPaletteHexes.forEach { hex ->
                            val color = parseHexColor(hex)
                            val isSelected = selectedColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedColor) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_folder_button")
            ) {
                Text(if (isEditing) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun FolderPickerDialog(
    folders: List<FolderItem>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a carpeta", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (currentFolderId == null) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelectFolder(null) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sin carpeta (Raíz)", style = MaterialTheme.typography.bodyLarge)
                }

                folders.forEach { folder ->
                    val selected = folder.id == currentFolderId
                    val folderColor = parseHexColor(folder.colorHex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelectFolder(folder.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = folderColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun RenameDialog(
    title: String,
    initialValue: String,
    maxChars: Int = 120,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxChars) text = it },
                singleLine = true,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
