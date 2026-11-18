package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedSignature
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RenameDialog
import com.example.ui.components.SignatureDrawingDialog
import com.example.ui.components.parseHexColor

@Composable
fun SignaturesVaultScreen(
    signaturesState: UiState<List<SavedSignature>>,
    onSaveSignature: (name: String, inkColorHex: String, bitmap: Bitmap) -> Unit,
    onRenameSignature: (id: String, newName: String) -> Unit,
    onDeleteSignature: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPad by remember { mutableStateOf(false) }
    var signatureToRename by remember { mutableStateOf<SavedSignature?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Bóveda de firmas",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Guarda múltiples firmas sincronizadas en tu cuenta Google para firmar cualquier PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (signaturesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.padding(20.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.Draw,
                            title = "Error al cargar firmas",
                            subtitle = signaturesState.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (signaturesState.data.isEmpty()) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            EmptyStateCard(
                                icon = Icons.Filled.Draw,
                                title = "Aún no tienes firmas guardadas",
                                subtitle = "Dibuja tu firma o rúbrica una vez y úsala en todos tus contratos.",
                                actionLabel = "Dibujar primera firma",
                                onAction = { showPad = true }
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(signaturesState.data, key = { it.id }) { sig ->
                                SignatureVaultCard(
                                    signature = sig,
                                    onRename = { signatureToRename = sig },
                                    onDelete = { onDeleteSignature(sig.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showPad = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.action_new_signature)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_signature_fab")
        )
    }

    if (showPad) {
        SignatureDrawingDialog(
            onDismiss = { showPad = false },
            onSaveSignature = { name, hex, bmp ->
                onSaveSignature(name, hex, bmp)
                showPad = false
            }
        )
    }

    signatureToRename?.let { sig ->
        RenameDialog(
            title = "Renombrar firma",
            initialValue = sig.name,
            maxChars = 60,
            onDismiss = { signatureToRename = null },
            onConfirm = { newName ->
                onRenameSignature(sig.id, newName)
                signatureToRename = null
            }
        )
    }
}

@Composable
private fun SignatureVaultCard(
    signature: SavedSignature,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bitmap = remember(signature.imageBase64) {
        PdfEngineService.decodeBase64ToBitmap(signature.imageBase64)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signature_card_${signature.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(signature.inkColorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = signature.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Paper sheet preview box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_signature_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
