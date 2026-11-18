package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.ToolImagesColor
import com.example.ui.theme.ToolMergeColor
import com.example.ui.theme.ToolSignColor

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onTestUserSignIn: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSession by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var mergeFeatureTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var showTestLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Editorial Brand Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Editorial Studio Hero Artwork
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1794967392372),
                        contentDescription = stringResource(R.string.cd_studio_hero),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.auth_welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.auth_welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(
                        icon = Icons.Filled.Layers,
                        tint = ToolMergeColor,
                        title = "Une PDFs y convierte PNG/JPEG",
                        subtitle = "Combina documentos o fotos en segundos sin perder nitidez",
                        modifier = Modifier
                            .testTag("feature_merge_images_row")
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTimeMs > 3000L) {
                                    mergeFeatureTapCount = 1
                                } else {
                                    mergeFeatureTapCount += 1
                                }
                                lastTapTimeMs = now
                                if (mergeFeatureTapCount >= 5) {
                                    mergeFeatureTapCount = 0
                                    showTestLoginDialog = true
                                }
                            }
                    )
                    FeatureRow(
                        icon = Icons.Filled.Draw,
                        tint = ToolSignColor,
                        title = "Múltiples firmas y colocación libre",
                        subtitle = "Dibuja tus firmas una vez y colócalas sobre cualquier página"
                    )
                    FeatureRow(
                        icon = Icons.Filled.FolderSpecial,
                        tint = ToolImagesColor,
                        title = "Carpetas y favoritos sincronizados",
                        subtitle = "Organiza tu catálogo y conserva tus firmas en tu cuenta Google"
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isSigningIn = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isSigningIn = false
                            }
                        )
                    },
                    enabled = !isSigningIn && !isCheckingSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSigningIn || isCheckingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isCheckingSession) "Verificando sesión..." else "Conectando...",
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.auth_sign_in_google),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showTestLoginDialog) {
        var testUsername by remember { mutableStateOf("usuario_test") }
        AlertDialog(
            onDismissRequest = { showTestLoginDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Modo de pruebas", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ingresa con un usuario de prueba local para evaluar todas las herramientas PDF sin cuenta de Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = testUsername,
                        onValueChange = { if (it.length <= 40) testUsername = it },
                        label = { Text("Usuario test") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_user_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = testUsername.trim().ifEmpty { "usuario_test" }
                        showTestLoginDialog = false
                        onTestUserSignIn(clean)
                    },
                    modifier = Modifier.testTag("confirm_test_user_button")
                ) {
                    Text("Ingresar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestLoginDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
