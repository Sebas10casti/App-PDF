package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min

private data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val widthPx: Float
)

private val SignatureInkOptions = listOf(
    "#1E2430" to "Grafito",
    "#1D4ED8" to "Azul",
    "#E84855" to "Coral"
)

@Composable
fun SignatureDrawingDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (name: String, inkColorHex: String, bitmap: Bitmap) -> Unit
) {
    var signatureName by remember { mutableStateOf("Mi firma") }
    var selectedHex by remember { mutableStateOf("#1E2430") }
    var strokeWidthPx by remember { mutableFloatStateOf(9f) }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val selectedColor = remember(selectedHex) { parseHexColor(selectedHex, Color(0xFF1E2430)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dibujar nueva firma",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                OutlinedTextField(
                    value = signatureName,
                    onValueChange = { if (it.length <= 60) signatureName = it },
                    label = { Text("Nombre de la firma") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_name_input")
                )

                // Ink color and thickness controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SignatureInkOptions.forEach { (hex, _) ->
                            val c = parseHexColor(hex)
                            val isSelected = selectedHex == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(6f to "Fino", 9f to "Medio", 13f to "Grueso").forEach { (width, label) ->
                            FilterChip(
                                selected = strokeWidthPx == width,
                                onClick = { strokeWidthPx = width },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Drawing Canvas on bright paper background
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("signature_canvas"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (strokes.isEmpty() && activePoints.isEmpty()) {
                            Text(
                                text = "Dibuja tu firma aquí con el dedo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9AA0AB),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Subtle baseline rule
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(1.dp)
                                .background(Color(0xFFE5E7EB))
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 44.dp)
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(selectedColor, strokeWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            activePoints.clear()
                                            activePoints.add(start)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activePoints.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (activePoints.isNotEmpty()) {
                                                strokes.add(
                                                    InkStroke(
                                                        points = activePoints.toList(),
                                                        color = selectedColor,
                                                        widthPx = strokeWidthPx
                                                    )
                                                )
                                                activePoints.clear()
                                            }
                                        },
                                        onDragCancel = {
                                            activePoints.clear()
                                        }
                                    )
                                }
                        ) {
                            strokes.forEach { stroke ->
                                drawPath(
                                    path = buildSmoothComposePath(stroke.points),
                                    color = stroke.color,
                                    style = Stroke(
                                        width = stroke.widthPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            if (activePoints.isNotEmpty()) {
                                drawPath(
                                    path = buildSmoothComposePath(activePoints),
                                    color = selectedColor,
                                    style = Stroke(
                                        width = strokeWidthPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                            enabled = strokes.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deshacer")
                        }
                        TextButton(
                            onClick = { strokes.clear() },
                            enabled = strokes.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar")
                        }
                    }

                    Button(
                        onClick = {
                            val bmp = renderTrimmedSignatureBitmap(
                                strokes = strokes.toList(),
                                width = canvasSize.width.coerceAtLeast(300),
                                height = canvasSize.height.coerceAtLeast(150)
                            )
                            if (bmp != null) {
                                onSaveSignature(signatureName.trim().ifEmpty { "Mi firma" }, selectedHex, bmp)
                            }
                        },
                        enabled = strokes.isNotEmpty() && signatureName.trim().isNotEmpty(),
                        modifier = Modifier.testTag("save_signature_button")
                    ) {
                        Text("Guardar firma")
                    }
                }
            }
        }
    }
}

private fun buildSmoothComposePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) {
        path.lineTo(points.first().x + 0.5f, points.first().y + 0.5f)
        return path
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) / 2f
        val midY = (prev.y + curr.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun renderTrimmedSignatureBitmap(
    strokes: List<InkStroke>,
    width: Int,
    height: Int
): Bitmap? {
    if (strokes.isEmpty()) return null

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    var maxStroke = 10f

    for (stroke in strokes) {
        maxStroke = max(maxStroke, stroke.widthPx)
        for (p in stroke.points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
    }

    val pad = maxStroke + 16f
    val left = (minX - pad).coerceIn(0f, width.toFloat()).toInt()
    val top = (minY - pad).coerceIn(0f, height.toFloat()).toInt()
    val right = (maxX + pad).coerceIn(left + 20f, width.toFloat()).toInt()
    val bottom = (maxY + pad).coerceIn(top + 20f, height.toFloat()).toInt()

    val cropW = (right - left).coerceAtLeast(40)
    val cropH = (bottom - top).coerceAtLeast(40)

    val bitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.translate(-left.toFloat(), -top.toFloat())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.widthPx
        val androidPath = AndroidPath()
        val pts = stroke.points
        if (pts.isNotEmpty()) {
            androidPath.moveTo(pts.first().x, pts.first().y)
            if (pts.size == 1) {
                androidPath.lineTo(pts.first().x + 0.5f, pts.first().y + 0.5f)
            } else {
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val curr = pts[i]
                    androidPath.quadTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                }
                androidPath.lineTo(pts.last().x, pts.last().y)
            }
            canvas.drawPath(androidPath, paint)
        }
    }
    return bitmap
}
