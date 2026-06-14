package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocShape
import com.example.ui.DocShapeRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocShapeOverlay(
    docShape: DocShape,
    docId: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    paperWidth: Dp,
    paperHeight: Dp,
    selectedDocumentTheme: String = "Office Classic",
    selectedThemeEffect: String = "None"
) {
    val density = LocalDensity.current
    
    // Smooth hardware-accelerated drag/resize state
    var localX by remember(docShape.id, docShape.x) { mutableStateOf(docShape.x) }
    var localY by remember(docShape.id, docShape.y) { mutableStateOf(docShape.y) }
    var localWidth by remember(docShape.id, docShape.width) { mutableStateOf(docShape.width) }
    var localHeight by remember(docShape.id, docShape.height) { mutableStateOf(docShape.height) }
    
    val currentShapeState = rememberUpdatedState(docShape)
    var showColorPicker by remember { mutableStateOf(false) }
    var showTextFormat by remember { mutableStateOf(false) }

    val themeProps = remember(selectedDocumentTheme) {
        getThemeProperties(selectedDocumentTheme)
    }

    // Parse safety values
    val fillColor = remember(docShape.fillColorHex, selectedDocumentTheme) {
        try {
            if (docShape.fillColorHex.isBlank() || docShape.fillColorHex == "default" || docShape.fillColorHex.equals("#4F81BD", ignoreCase = true)) {
                themeProps.secondaryAccent
            } else {
                Color(android.graphics.Color.parseColor(docShape.fillColorHex))
            }
        }
        catch (e: Exception) { themeProps.secondaryAccent }
    }
    val borderColor = remember(docShape.borderColorHex, selectedDocumentTheme) {
        try {
            if (docShape.borderColorHex.isBlank() || docShape.borderColorHex == "default" || docShape.borderColorHex.equals("#1B365D", ignoreCase = true)) {
                themeProps.primaryAccent
            } else {
                Color(android.graphics.Color.parseColor(docShape.borderColorHex))
            }
        }
        catch (e: Exception) { themeProps.primaryAccent }
    }
    val textColor = remember(docShape.textColorHex) {
        try { Color(android.graphics.Color.parseColor(docShape.textColorHex)) }
        catch (e: Exception) { Color(0xFFFFFFFF) }
    }

    Box(
        modifier = Modifier
            .offset(x = localX, y = localY)
            .size(width = localWidth, height = localHeight)
            .pointerInput(docShape.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = {
                        DocShapeRepository.updateShape(
                            docId = docId,
                            shape = currentShapeState.value.copy(x = localX, y = localY)
                        )
                    }
                ) { change, dragAmount ->
                    change.consume()
                    localX = (localX + with(density) { dragAmount.x.toDp() }).coerceIn(0.dp, (paperWidth - localWidth).coerceAtLeast(0.dp))
                    localY = (localY + with(density) { dragAmount.y.toDp() }).coerceIn(0.dp, (paperHeight - localHeight).coerceAtLeast(0.dp))
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                onSelect()
            }
            .applyThemeEffect(selectedThemeEffect, RoundedCornerShape(4.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, themeProps.secondaryAccent, RoundedCornerShape(4.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Shapes Drawing Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val borderPx = docShape.borderWidthDp.toPx()
            val w = size.width
            val h = size.height

            // 1. Build the path based on shape type
            val path = Path().apply {
                when (docShape.type) {
                    "triangle" -> {
                        moveTo(w / 2f, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    "right_triangle" -> {
                        moveTo(0f, 0f)
                        lineTo(0f, h)
                        lineTo(w, h)
                        close()
                    }
                    "diamond" -> {
                        moveTo(w / 2f, 0f)
                        lineTo(w, h / 2f)
                        lineTo(w / 2f, h)
                        lineTo(0f, h / 2f)
                        close()
                    }
                    "hexagon" -> {
                        moveTo(w * 0.25f, 0f)
                        lineTo(w * 0.75f, 0f)
                        lineTo(w, h / 2f)
                        lineTo(w * 0.75f, h)
                        lineTo(w * 0.25f, h)
                        lineTo(0f, h / 2f)
                        close()
                    }
                    "cloud" -> {
                        moveTo(w * 0.25f, h * 0.75f)
                        cubicTo(w * 0.05f, h * 0.75f, w * 0.05f, h * 0.45f, w * 0.25f, h * 0.45f)
                        cubicTo(w * 0.25f, h * 0.2f, w * 0.55f, h * 0.2f, w * 0.55f, h * 0.45f)
                        cubicTo(w * 0.75f, h * 0.3f, w * 0.95f, h * 0.5f, w * 0.85f, h * 0.65f)
                        cubicTo(w * 0.95f, h * 0.85f, w * 0.75f, h * 0.85f, w * 0.65f, h * 0.85f)
                        lineTo(w * 0.25f, h * 0.85f)
                        close()
                    }
                    "heart" -> {
                        moveTo(w / 2f, h * 0.25f)
                        cubicTo(w * 0.2f, h * -0.05f, w * -0.1f, h * 0.35f, w / 2f, h * 0.85f)
                        cubicTo(w * 1.1f, h * 0.35f, w * 0.8f, h * -0.05f, w / 2f, h * 0.25f)
                    }
                    "right_arrow" -> {
                        moveTo(0f, h * 0.3f)
                        lineTo(w * 0.6f, h * 0.3f)
                        lineTo(w * 0.6f, 0f)
                        lineTo(w, h / 2f)
                        lineTo(w * 0.6f, h)
                        lineTo(w * 0.6f, h * 0.7f)
                        lineTo(0f, h * 0.7f)
                        close()
                    }
                    "left_arrow" -> {
                        moveTo(w, h * 0.3f)
                        lineTo(w * 0.4f, h * 0.3f)
                        lineTo(w * 0.4f, 0f)
                        lineTo(0f, h / 2f)
                        lineTo(w * 0.4f, h)
                        lineTo(w * 0.4f, h * 0.7f)
                        lineTo(w, h * 0.7f)
                        close()
                    }
                    "up_arrow" -> {
                        moveTo(w * 0.3f, h)
                        lineTo(w * 0.3f, h * 0.4f)
                        lineTo(0f, h * 0.4f)
                        lineTo(w / 2f, 0f)
                        lineTo(w, h * 0.4f)
                        lineTo(w * 0.7f, h * 0.4f)
                        lineTo(w * 0.7f, h)
                        close()
                    }
                    "down_arrow" -> {
                        moveTo(w * 0.3f, 0f)
                        lineTo(w * 0.3f, h * 0.6f)
                        lineTo(0f, h * 0.6f)
                        lineTo(w / 2f, h)
                        lineTo(w, h * 0.6f)
                        lineTo(w * 0.7f, h * 0.6f)
                        lineTo(w * 0.7f, 0f)
                        close()
                    }
                    "plus_eq" -> {
                        moveTo(w * 0.35f, 0f)
                        lineTo(w * 0.65f, 0f)
                        lineTo(w * 0.65f, h * 0.35f)
                        lineTo(w, h * 0.35f)
                        lineTo(w, h * 0.65f)
                        lineTo(w * 0.65f, h * 0.65f)
                        lineTo(w * 0.65f, h)
                        lineTo(w * 0.35f, h)
                        lineTo(w * 0.35f, h * 0.65f)
                        lineTo(0f, h * 0.65f)
                        lineTo(0f, h * 0.35f)
                        lineTo(w * 0.35f, h * 0.35f)
                        close()
                    }
                    "minus_eq" -> {
                        moveTo(0f, h * 0.35f)
                        lineTo(w, h * 0.35f)
                        lineTo(w, h * 0.65f)
                        lineTo(0f, h * 0.65f)
                        close()
                    }
                    "star_5" -> {
                        val cx = w / 2f
                        val cy = h / 2f
                        val r = w / 2f
                        val innerRadius = r * 0.4f
                        var angle = -Math.PI / 2.0
                        val dAngle = Math.PI / 5.0
                        for (i in 0 until 10) {
                            val currentRadius = if (i % 2 == 0) r else innerRadius
                            val curX = cx + currentRadius * Math.cos(angle)
                            val curY = cy + currentRadius * Math.sin(angle)
                            if (i == 0) moveTo(curX.toFloat(), curY.toFloat())
                            else lineTo(curX.toFloat(), curY.toFloat())
                            angle += dAngle
                        }
                        close()
                    }
                }
            }

            // 2. Draw standard rectangle/oval configurations or custom Paths
            when (docShape.type) {
                "rectangle" -> {
                    drawRect(color = fillColor)
                    if (borderPx > 0f) {
                        drawRect(color = borderColor, style = Stroke(width = borderPx))
                    }
                }
                "round_rectangle" -> {
                    val radius = CornerRadius(16f, 16f)
                    drawRoundRect(color = fillColor, cornerRadius = radius)
                    if (borderPx > 0f) {
                        drawRoundRect(color = borderColor, cornerRadius = radius, style = Stroke(width = borderPx))
                    }
                }
                "ellipse" -> {
                    drawOval(color = fillColor)
                    if (borderPx > 0f) {
                        drawOval(color = borderColor, style = Stroke(width = borderPx))
                    }
                }
                "smiley" -> {
                    drawCircle(color = fillColor, radius = size.minDimension / 2f)
                    drawCircle(color = borderColor, radius = size.minDimension / 2f, style = Stroke(width = borderPx))
                    val eyeR = size.minDimension * 0.06f
                    drawCircle(color = borderColor, radius = eyeR, center = Offset(w * 0.35f, h * 0.4f))
                    drawCircle(color = borderColor, radius = eyeR, center = Offset(w * 0.65f, h * 0.4f))
                    drawArc(
                        color = borderColor,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.3f, h * 0.5f),
                        size = Size(w * 0.4f, h * 0.25f),
                        style = Stroke(width = borderPx, cap = StrokeCap.Round)
                    )
                }
                else -> {
                    // Custom Path Drawing (triangle, star, cloud, arrow, diamond, etc.)
                    drawPath(path = path, color = fillColor)
                    if (borderPx > 0f) {
                        drawPath(path = path, color = borderColor, style = Stroke(width = borderPx, join = StrokeJoin.Round))
                    }
                }
            }
        }

        // Internal Text Layer
        if (docShape.textInside.isNotEmpty()) {
            Text(
                text = docShape.textInside,
                color = textColor,
                fontSize = docShape.textSizeSp.sp,
                fontWeight = if (docShape.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (docShape.isItalic) FontStyle.Italic else FontStyle.Normal,
                textAlign = when (docShape.textAlignment) {
                    "left" -> TextAlign.Left
                    "right" -> TextAlign.Right
                    else -> TextAlign.Center
                },
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.Center)
            )
        }

        // Selection Overlay Controls
        if (isSelected) {
            val handleSize = 10.dp
            val handleColor = MaterialTheme.colorScheme.primary

            // Top center quick move handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-18).dp)
                    .size(24.dp)
                    .shadow(4.dp, CircleShape)
                    .background(handleColor, CircleShape)
                    .pointerInput(docShape.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocShapeRepository.updateShape(
                                    docId = docId,
                                    shape = currentShapeState.value.copy(x = localX, y = localY)
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            localX = (localX + with(density) { dragAmount.x.toDp() }).coerceIn(0.dp, (paperWidth - localWidth).coerceAtLeast(0.dp))
                            localY = (localY + with(density) { dragAmount.y.toDp() }).coerceIn(0.dp, (paperHeight - localHeight).coerceAtLeast(0.dp))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OpenWith, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }

            // Top-Left Resize Handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -handleSize / 2, y = -handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docShape.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocShapeRepository.updateShape(
                                    docId = docId,
                                    shape = currentShapeState.value.copy(
                                        x = localX,
                                        y = localY,
                                        width = localWidth,
                                        height = localHeight
                                    )
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            val newWidth = (localWidth - dx).coerceAtLeast(30.dp)
                            val newHeight = (localHeight - dy).coerceAtLeast(30.dp)
                            localX += (localWidth - newWidth)
                            localY += (localHeight - newHeight)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            // Bottom-Right Resize Handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docShape.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocShapeRepository.updateShape(
                                    docId = docId,
                                    shape = currentShapeState.value.copy(
                                        width = localWidth,
                                        height = localHeight
                                    )
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            localWidth = (localWidth + dx).coerceAtLeast(30.dp)
                            localHeight = (localHeight + dy).coerceAtLeast(30.dp)
                        }
                    }
            )

            // Shape Quick action shortcut toolbar floating on top of active shape
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 48.dp)
                    .height(38.dp)
                    .shadow(6.dp, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showColorPicker = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Palette, "Shape Color Fill & Borders", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { showTextFormat = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.TextFields, "Shape Internal Paragraph Text Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        DocShapeRepository.removeShape(docId, docShape.id)
                        onDeselect()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "Delete Shape", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // Modal dialog for fill/border editing
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Shape Fill & Outline Borders") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Fill Color options
                    Text("Fill Color preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val fills = listOf("#4F81BD", "#4BACC6", "#F79646", "#7F7F7F", "#333333", "#E06666", "#93C47D", "#FFD966")
                    val fillScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(fillScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fills.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (docShape.fillColorHex == hex) 3.dp else 1.dp,
                                        color = if (docShape.fillColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        DocShapeRepository.updateShape(docId, docShape.copy(fillColorHex = hex))
                                    }
                            )
                        }
                    }

                    // Border Color options
                    Text("Border Outline color preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val borders = listOf("#1B365D", "#1D525F", "#8F4500", "#3E3E3E", "#111111", "#7B0000", "#1C4E00", "#7F6000")
                    val borderScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(borderScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        borders.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (docShape.borderColorHex == hex) 3.dp else 1.dp,
                                        color = if (docShape.borderColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        DocShapeRepository.updateShape(docId, docShape.copy(borderColorHex = hex))
                                    }
                            )
                        }
                    }

                    // Border thickness slider
                    Text("Border Thickness (${docShape.borderWidthDp.value} dp):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = docShape.borderWidthDp.value,
                        onValueChange = {
                            DocShapeRepository.updateShape(docId, docShape.copy(borderWidthDp = it.toInt().dp))
                        },
                        valueRange = 0f..8f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text("Close") }
            }
        )
    }

    // Modal dialog for internal shape content and formatting
    if (showTextFormat) {
        var localTextInside by remember { mutableStateOf(docShape.textInside) }
        AlertDialog(
            onDismissRequest = { showTextFormat = false },
            title = { Text("Shape Text Formatting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = localTextInside,
                        onValueChange = {
                            localTextInside = it
                            DocShapeRepository.updateShape(docId, docShape.copy(textInside = it))
                        },
                        label = { Text("Text inside shape") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Style:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconToggleButton(
                                checked = docShape.isBold,
                                onCheckedChange = { DocShapeRepository.updateShape(docId, docShape.copy(isBold = it)) }
                            ) {
                                Icon(Icons.Default.FormatBold, "Bold text formatting")
                            }
                            IconToggleButton(
                                checked = docShape.isItalic,
                                onCheckedChange = { DocShapeRepository.updateShape(docId, docShape.copy(isItalic = it)) }
                            ) {
                                Icon(Icons.Default.FormatItalic, "Italic text formatting")
                            }
                        }
                    }

                    // Text Font Size preset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Text Size (sp):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (docShape.textSizeSp > 10f) {
                                    DocShapeRepository.updateShape(docId, docShape.copy(textSizeSp = docShape.textSizeSp - 1f))
                                }
                            }) {
                                Icon(Icons.Default.Remove, "Decrease Text Size")
                            }
                            Text(docShape.textSizeSp.toInt().toString(), fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (docShape.textSizeSp < 28f) {
                                    DocShapeRepository.updateShape(docId, docShape.copy(textSizeSp = docShape.textSizeSp + 1f))
                                }
                            }) {
                                Icon(Icons.Default.Add, "Increase Text Size")
                            }
                        }
                    }

                    // Text color presets
                    Text("Text Color Preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val textColors = listOf("#FFFFFF", "#FFFF00", "#000000", "#1B365D", "#7B0000", "#1C4E00")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textColors.forEach { hColor ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(android.graphics.Color.parseColor(hColor)), CircleShape)
                                    .border(
                                        width = if (docShape.textColorHex == hColor) 2.dp else 1.dp,
                                        color = if (docShape.textColorHex == hColor) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        DocShapeRepository.updateShape(docId, docShape.copy(textColorHex = hColor))
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextFormat = false }) { Text("OK") }
            }
        )
    }
}
