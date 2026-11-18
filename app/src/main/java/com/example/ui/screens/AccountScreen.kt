package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.FolderItem
import com.example.data.model.PdfDocumentItem
import com.example.data.model.SavedSignature
import com.example.data.model.UiState
import com.example.ui.auth.signOut
import com.example.ui.theme.ToolImagesColor
import com.example.ui.theme.ToolReaderColor
import com.example.ui.theme.ToolSignColor
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun AccountScreen(
    documentsState: UiState<List<PdfDocumentItem>>,
    foldersState: UiState<List<FolderItem>>,
    signaturesState: UiState<List<SavedSignature>>,
    isTestMode: Boolean = false,
    testUserName: String? = null,
    onTestSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = if (isTestMode) null else Firebase.auth.currentUser

    val docCount = (documentsState as? UiState.Success)?.data?.size ?: 0
    val folderCount = (foldersState as? UiState.Success)?.data?.size ?: 0
    val signatureCount = (signaturesState as? UiState.Success)?.data?.size ?: 0

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cuenta y sincronización",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // User Profile Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayName = if (isTestMode) {
                        testUserName ?: "Usuario Test"
                    } else {
                        user?.displayName ?: "Usuario Google"
                    }
                    val subtitleText = if (isTestMode) {
                        "Sesión local de pruebas"
                    } else {
                        user?.email ?: "Sesión activa"
                    }
                    val initial = (displayName.firstOrNull() ?: 'F').uppercaseChar()
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val badgeTint = if (isTestMode) ToolImagesColor else ToolSignColor
                        Surface(
                            color = badgeTint.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isTestMode) Icons.Filled.BugReport else Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = badgeTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isTestMode) "Modo de Pruebas Local (QA)" else "Sincronizado con Cloud Firestore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeTint
                                )
                            }
                        }
                    }
                }
            }

            // Library Summary Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    icon = Icons.Filled.Description,
                    tint = ToolReaderColor,
                    count = docCount.toString(),
                    label = "Documentos",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Filled.Folder,
                    tint = ToolImagesColor,
                    count = folderCount.toString(),
                    label = "Carpetas",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Filled.Draw,
                    tint = ToolSignColor,
                    count = signatureCount.toString(),
                    label = "Firmas",
                    modifier = Modifier.weight(1f)
                )
            }

            // Info card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Privacidad y almacenamiento",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isTestMode) {
                            "Estás usando una sesión local de pruebas (QA). Tus documentos, carpetas y firmas se guardan únicamente en este dispositivo dentro de un entorno aislado, sin escribir en Firebase."
                        } else {
                            "Tus firmas dibujadas, carpetas, favoritos e índice de documentos se sincronizan de forma aislada con tu cuenta Google. Los archivos PDF completos se procesan y guardan de manera privada en el almacenamiento seguro de la app."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (isTestMode) {
                        onTestSignOut()
                    } else {
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isTestMode) "Salir del modo test" else "Cerrar sesión")
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
