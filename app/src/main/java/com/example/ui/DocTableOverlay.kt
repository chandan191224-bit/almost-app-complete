package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DocWordColor

@Composable
fun DocTableOverlay(
    docTable: DocTable,
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

    // Make position and bounds ultra-smooth on resize/drag
    var localX by remember(docTable.id, docTable.x) { mutableStateOf(docTable.x) }
    var localY by remember(docTable.id, docTable.y) { mutableStateOf(docTable.y) }
    var localWidth by remember(docTable.id, docTable.width) { mutableStateOf(docTable.width) }
    var localHeight by remember(docTable.id, docTable.height) { mutableStateOf(docTable.height) }

    val currentTableState = rememberUpdatedState(docTable)

    // Cell Editor Dialog State
    var showCellEditor by remember { mutableStateOf(false) }
    var editingRow by remember { mutableIntStateOf(0) }
    var editingCol by remember { mutableIntStateOf(0) }

    // Active Cell Inline Edit Focus State
    var activeEditingRow by remember { mutableStateOf<Int?>(null) }
    var activeEditingCol by remember { mutableStateOf<Int?>(null) }
    val cellFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSelected) {
        if (!isSelected) {
            activeEditingRow = null
            activeEditingCol = null
        }
    }

    val themeProps = remember(selectedDocumentTheme) {
        getThemeProperties(selectedDocumentTheme)
    }

    Box(
        modifier = Modifier
            .offset(x = localX, y = localY)
            .size(width = localWidth, height = localHeight)
            .pointerInput(docTable.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = {
                        DocTableRepository.updateTable(
                            docId = docId,
                            table = currentTableState.value.copy(x = localX, y = localY)
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
                } else {
                    Modifier
                }
            )
            .background(Color.White, RoundedCornerShape(4.dp))
    ) {
        val themeColor = remember(docTable.themeColorHex, selectedDocumentTheme) {
            try {
                if (docTable.themeColorHex.isBlank() || docTable.themeColorHex == "default" || docTable.themeColorHex == "#5B9BD5") {
                    themeProps.primaryAccent
                } else {
                    Color(android.graphics.Color.parseColor(if (docTable.themeColorHex.startsWith("#")) docTable.themeColorHex else "#${docTable.themeColorHex}"))
                }
            } catch (e: Exception) {
                themeProps.primaryAccent
            }
        }

        // Draw Table Grid cells
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until docTable.rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (c in 0 until docTable.columns) {
                        val cellText = docTable.getCellText(r, c)
                        val isHeader = docTable.hasHeaderRow && r == 0
                        val customBg = docTable.cellBgColors["$r,$c"]
                        val cellBg = if (customBg != null) {
                            try {
                                Color(android.graphics.Color.parseColor(customBg))
                            } catch (e: Exception) {
                                Color.White
                            }
                        } else {
                            if (isHeader) {
                                themeColor
                            } else if (docTable.alternateRows && r % 2 == 1) {
                                themeColor.copy(alpha = 0.08f)
                            } else {
                                Color.White
                            }
                        }

                        val isBold = docTable.cellBold["$r,$c"] ?: isHeader
                        val isItalic = docTable.cellItalic["$r,$c"] ?: false
                        val isUnderline = docTable.cellUnderline["$r,$c"] ?: false

                        val isEditingThisCell = activeEditingRow == r && activeEditingCol == c

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(cellBg)
                                .border(docTable.borderWidth, themeColor.copy(alpha = 0.6f))
                                .clickable {
                                    onSelect()
                                    editingRow = r
                                    editingCol = c
                                    activeEditingRow = r
                                    activeEditingCol = c
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isEditingThisCell) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = cellText,
                                    onValueChange = { newVal ->
                                        val key = "$r,$c"
                                        val updatedCellData = docTable.cellData.toMutableMap().apply {
                                            this[key] = newVal
                                        }
                                        val updated = docTable.copy(cellData = updatedCellData)
                                        DocTableRepository.updateTable(docId, updated)
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                                        color = if (isHeader && customBg == null) Color.White else Color(0xFF1E1F22),
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(cellFocusRequester),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                        if (isHeader && customBg == null) Color.White else Color(0xFF1E1F22)
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onDone = {
                                            activeEditingRow = null
                                            activeEditingCol = null
                                        }
                                    )
                                )
                                LaunchedEffect(r, c) {
                                    cellFocusRequester.requestFocus()
                                }
                            } else {
                                Text(
                                    text = cellText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                                    color = if (isHeader && customBg == null) Color.White else Color(0xFF1E1F22),
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected mode markers and handles
        if (isSelected) {
            val handleSize = 10.dp
            val handleColor = DocWordColor

            // Floating Move/Drag handle at TopCenter
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-18).dp)
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .background(handleColor, CircleShape)
                    .pointerInput(docTable.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDragEnd = {
                                DocTableRepository.updateTable(
                                    docId = docId,
                                    table = currentTableState.value.copy(x = localX, y = localY)
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            localX = (localX + with(density) { dragAmount.x.toDp() }).coerceIn(0.dp, (paperWidth - localWidth).coerceAtLeast(0.dp))
                            localY = (localY + with(density) { dragAmount.y.toDp() }).coerceIn(0.dp, (paperHeight - localHeight).coerceAtLeast(0.dp))
                        }
                    }
                    .clickable { onSelect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Move/Drag Table Handle",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // TopLeft resize handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -handleSize / 2, y = -handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docTable.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocTableRepository.updateTable(
                                    docId = docId,
                                    table = currentTableState.value.copy(
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
                            val newWidth = (localWidth - dx).coerceAtLeast(100.dp)
                            val newHeight = (localHeight - dy).coerceAtLeast(60.dp)
                            localX += (localWidth - newWidth)
                            localY += (localHeight - newHeight)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            // TopRight resize handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = handleSize / 2, y = -handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docTable.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocTableRepository.updateTable(
                                    docId = docId,
                                    table = currentTableState.value.copy(
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
                            val newWidth = (localWidth + dx).coerceAtLeast(100.dp)
                            val newHeight = (localHeight - dy).coerceAtLeast(60.dp)
                            localY += (localHeight - newHeight)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            // BottomLeft resize handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docTable.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocTableRepository.updateTable(
                                    docId = docId,
                                    table = currentTableState.value.copy(
                                        x = localX,
                                        width = localWidth,
                                        height = localHeight
                                    )
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            val newWidth = (localWidth - dx).coerceAtLeast(100.dp)
                            val newHeight = (localHeight + dy).coerceAtLeast(60.dp)
                            localX += (localWidth - newWidth)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            // BottomRight resize handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docTable.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocTableRepository.updateTable(
                                    docId = docId,
                                    table = currentTableState.value.copy(
                                        width = localWidth,
                                        height = localHeight
                                    )
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            localWidth = (localWidth + dx).coerceAtLeast(100.dp)
                            localHeight = (localHeight + dy).coerceAtLeast(60.dp)
                        }
                    }
            )

            // Horizontal Office Table Inline Ribbon Controls (floating underneath/above)
            val toolbarWidth = 360.dp
            val finalToolbarX = remember(localX, localWidth, paperWidth) {
                val desiredStart = localX + (localWidth - toolbarWidth) / 2
                val finalStart = desiredStart.coerceIn(4.dp, (paperWidth - toolbarWidth - 4.dp).coerceAtLeast(4.dp))
                finalStart - localX
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = finalToolbarX,
                        y = if (localY < 64.dp) localHeight + 12.dp else (-64).dp
                    )
                    .width(toolbarWidth)
                    .height(54.dp)
                    .shadow(6.dp, RoundedCornerShape(27.dp))
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF1E1F22) else Color.White,
                        RoundedCornerShape(27.dp)
                    )
                    .border(
                        1.dp,
                        DocWordColor.copy(alpha = 0.4f),
                        RoundedCornerShape(27.dp)
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* Consume */ }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Alternating Row shading state toggle
                    IconButton(
                        onClick = {
                            val updated = docTable.copy(alternateRows = !docTable.alternateRows)
                            DocTableRepository.updateTable(docId, updated)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "Toggle Zebra Rows",
                            tint = if (docTable.alternateRows) DocWordColor else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. style preset loop
                    IconButton(
                        onClick = {
                            val nextStyle = when (docTable.styleName) {
                                "classic" -> "elegant_blue"
                                "elegant_blue" -> "modern_emerald"
                                "modern_emerald" -> "warm_gold"
                                "warm_gold" -> "dark_minimalist"
                                else -> "classic"
                            }
                            val nextHex = when (nextStyle) {
                                "classic" -> "#7F7F7F"
                                "elegant_blue" -> "#4F81BD"
                                "modern_emerald" -> "#385723"
                                "warm_gold" -> "#C55A11"
                                else -> "#262626"
                            }
                            val updated = docTable.copy(styleName = nextStyle, themeColorHex = nextHex)
                            DocTableRepository.updateTable(docId, updated)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Cycle Table Style Theme",
                            tint = themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Add dynamic row at bottom
                    IconButton(
                        onClick = {
                            val updated = docTable.copy(rows = (docTable.rows + 1).coerceAtMost(10))
                            DocTableRepository.updateTable(docId, updated)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableRows,
                            contentDescription = "Append Table Row",
                            tint = DocWordColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 4. Add dynamic column at right
                    IconButton(
                        onClick = {
                            val updated = docTable.copy(columns = (docTable.columns + 1).coerceAtMost(8))
                            DocTableRepository.updateTable(docId, updated)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewColumn,
                            contentDescription = "Append Table Column",
                            tint = DocWordColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 5. Header row toggle state
                    IconButton(
                        onClick = {
                            val updated = docTable.copy(hasHeaderRow = !docTable.hasHeaderRow)
                            DocTableRepository.updateTable(docId, updated)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = "Toggle Header Row Styling",
                            tint = if (docTable.hasHeaderRow) DocWordColor else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 6. Cell text settings & background shading layout tune (B/I/U, delete rows/columns, shading fill)
                    IconButton(
                        onClick = {
                            showCellEditor = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Format Selected Cell",
                            tint = DocWordColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 7. Delete Table entirely
                    IconButton(
                        onClick = {
                            DocTableRepository.removeTable(docId, docTable.id)
                            onDeselect()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Table",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Interactive Cell Editor and Formatter Dialog (MS Office Style)
    if (showCellEditor) {
        var cellValue by remember(editingRow, editingCol) {
            mutableStateOf(docTable.getCellText(editingRow, editingCol))
        }
        var isBold by remember(editingRow, editingCol) {
            mutableStateOf(docTable.cellBold["$editingRow,$editingCol"] ?: (docTable.hasHeaderRow && editingRow == 0))
        }
        var isItalic by remember(editingRow, editingCol) {
            mutableStateOf(docTable.cellItalic["$editingRow,$editingCol"] ?: false)
        }
        var isUnderline by remember(editingRow, editingCol) {
            mutableStateOf(docTable.cellUnderline["$editingRow,$editingCol"] ?: false)
        }
        var cellBgHex by remember(editingRow, editingCol) {
            mutableStateOf(docTable.cellBgColors["$editingRow,$editingCol"])
        }

        AlertDialog(
            onDismissRequest = { showCellEditor = false },
            title = {
                Text(
                    text = "Edit Cell [$editingRow, $editingCol]",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DocWordColor
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = cellValue,
                        onValueChange = { cellValue = it },
                        label = { Text("Cell Text Content") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    // Typographic Style Decorators row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Text Formatting", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconToggleButton(
                                checked = isBold,
                                onCheckedChange = { isBold = it },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (isBold) DocWordColor.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.FormatBold, "Bold", tint = if (isBold) DocWordColor else Color.Gray)
                            }

                            IconToggleButton(
                                checked = isItalic,
                                onCheckedChange = { isItalic = it },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (isItalic) DocWordColor.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.FormatItalic, "Italic", tint = if (isItalic) DocWordColor else Color.Gray)
                            }

                            IconToggleButton(
                                checked = isUnderline,
                                onCheckedChange = { isUnderline = it },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (isUnderline) DocWordColor.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.FormatUnderlined, "Underline", tint = if (isUnderline) DocWordColor else Color.Gray)
                            }
                        }
                    }

                    // Background color presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cell Shading Paint", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colorPresets = listOf(
                                "No Color" to null,
                                "#FFF2CC" to Color(0xFFFFF2CC), // Light Gold
                                "#E2EFDA" to Color(0xFFE2EFDA), // Light Sage Green
                                "#DDEBF7" to Color(0xFFDDEBF7), // Light Cool Blue
                                "#FCE4D6" to Color(0xFFFCE4D6), // Light Red/Orange
                                "#F2F2F2" to Color(0xFFF2F2F2), // Light Slate Gray
                                "#D9D9D9" to Color(0xFFD9D9D9)  // Medium Slate Gray
                            )

                            colorPresets.forEach { (colorStr, realColor) ->
                                val isSelectedColor = cellBgHex == colorStr
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .shadow(1.dp, CircleShape)
                                        .background(realColor ?: Color.White, CircleShape)
                                        .border(
                                            width = if (isSelectedColor) 2.6.dp else 1.dp,
                                            color = if (isSelectedColor) DocWordColor else Color.LightGray,
                                            shape = CircleShape
                                        )
                                        .clickable { cellBgHex = colorStr },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (realColor == null) {
                                        Icon(Icons.Default.Block, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    } else if (isSelectedColor) {
                                        Icon(Icons.Default.Check, null, tint = DocWordColor, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row & Column operations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Delete current Row
                                if (docTable.rows > 1) {
                                    val newCellData = mutableMapOf<String, String>()
                                    val newCellBg = mutableMapOf<String, String>()
                                    val newCellBold = mutableMapOf<String, Boolean>()
                                    val newCellItalic = mutableMapOf<String, Boolean>()
                                    val newCellUnderline = mutableMapOf<String, Boolean>()

                                    // Shift everything below editingRow up by 1
                                    for (r in 0 until docTable.rows) {
                                        if (r == editingRow) continue
                                        val targetR = if (r > editingRow) r - 1 else r
                                        for (c in 0 until docTable.columns) {
                                            docTable.cellData["$r,$c"]?.let { newCellData["$targetR,$c"] = it }
                                            docTable.cellBgColors["$r,$c"]?.let { newCellBg["$targetR,$c"] = it }
                                            docTable.cellBold["$r,$c"]?.let { newCellBold["$targetR,$c"] = it }
                                            docTable.cellItalic["$r,$c"]?.let { newCellItalic["$targetR,$c"] = it }
                                            docTable.cellUnderline["$r,$c"]?.let { newCellUnderline["$targetR,$c"] = it }
                                        }
                                    }
                                    val updated = docTable.copy(
                                        rows = docTable.rows - 1,
                                        cellData = newCellData,
                                        cellBgColors = newCellBg,
                                        cellBold = newCellBold,
                                        cellItalic = newCellItalic,
                                        cellUnderline = newCellUnderline
                                    )
                                    DocTableRepository.updateTable(docId, updated)
                                    showCellEditor = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBEAEA), contentColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete Row", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                // Delete current Column
                                if (docTable.columns > 1) {
                                    val newCellData = mutableMapOf<String, String>()
                                    val newCellBg = mutableMapOf<String, String>()
                                    val newCellBold = mutableMapOf<String, Boolean>()
                                    val newCellItalic = mutableMapOf<String, Boolean>()
                                    val newCellUnderline = mutableMapOf<String, Boolean>()

                                    // Shift columns right of editingCol left by 1
                                    for (r in 0 until docTable.rows) {
                                        for (c in 0 until docTable.columns) {
                                            if (c == editingCol) continue
                                            val targetC = if (c > editingCol) c - 1 else c
                                            docTable.cellData["$r,$c"]?.let { newCellData["$r,$targetC"] = it }
                                            docTable.cellBgColors["$r,$c"]?.let { newCellBg["$r,$targetC"] = it }
                                            docTable.cellBold["$r,$c"]?.let { newCellBold["$r,$targetC"] = it }
                                            docTable.cellItalic["$r,$c"]?.let { newCellItalic["$r,$targetC"] = it }
                                            docTable.cellUnderline["$r,$c"]?.let { newCellUnderline["$r,$targetC"] = it }
                                        }
                                    }
                                    val updated = docTable.copy(
                                        columns = docTable.columns - 1,
                                        cellData = newCellData,
                                        cellBgColors = newCellBg,
                                        cellBold = newCellBold,
                                        cellItalic = newCellItalic,
                                        cellUnderline = newCellUnderline
                                    )
                                    DocTableRepository.updateTable(docId, updated)
                                    showCellEditor = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBEAEA), contentColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete Col", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = "$editingRow,$editingCol"
                        
                        val updatedCellData = docTable.cellData.toMutableMap().apply {
                            this[key] = cellValue
                        }
                        
                        val updatedBgColors = docTable.cellBgColors.toMutableMap().apply {
                            if (cellBgHex != null) {
                                this[key] = cellBgHex!!
                            } else {
                                this.remove(key)
                            }
                        }
                        
                        val updatedBold = docTable.cellBold.toMutableMap().apply {
                            this[key] = isBold
                        }
                        
                        val updatedItalic = docTable.cellItalic.toMutableMap().apply {
                            this[key] = isItalic
                        }
                        
                        val updatedUnderline = docTable.cellUnderline.toMutableMap().apply {
                            this[key] = isUnderline
                        }

                        val updated = docTable.copy(
                            cellData = updatedCellData,
                            cellBgColors = updatedBgColors,
                            cellBold = updatedBold,
                            cellItalic = updatedItalic,
                            cellUnderline = updatedUnderline
                        )
                        DocTableRepository.updateTable(docId, updated)
                        showCellEditor = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DocWordColor, contentColor = Color.White)
                ) {
                    Text("Apply Layout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCellEditor = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
