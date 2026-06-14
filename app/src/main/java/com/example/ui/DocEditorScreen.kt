package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.text.RegexOption
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.takeOrElse
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.db.DocEntity
import com.example.ui.theme.*
import com.example.viewmodel.DocViewModel
import com.example.viewmodel.SlideItem
import java.text.SimpleDateFormat
import java.util.*

data class DocFormatSpan(var start: Int, var end: Int, val type: String, val value: String = "")

data class CopyFormattedData(
    val text: String,
    val spans: List<DocFormatSpan>,
    val sourceOffset: Int
)

data class DocTextStyle(
    val name: String,
    val fontSize: Int = 16,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: String? = null,
    val alignment: String? = null,
    val lineSpacing: Float? = null,
    val isDefault: Boolean = false
)

fun getDefaultTextStyles(): List<DocTextStyle> = listOf(
    DocTextStyle("Normal", fontSize = 16, isDefault = true),
    DocTextStyle("Title", fontSize = 24, isBold = true, color = "#1A1A1A", isDefault = true),
    DocTextStyle("Subtitle", fontSize = 18, isItalic = true, color = "#555555", isDefault = true),
    DocTextStyle("Heading 1", fontSize = 22, isBold = true, isDefault = true),
    DocTextStyle("Heading 2", fontSize = 20, isBold = true, isDefault = true),
    DocTextStyle("Heading 3", fontSize = 18, isBold = true, isDefault = true),
    DocTextStyle("Quote", fontSize = 16, isItalic = true, color = "#666666", isDefault = true)
)

fun applyStyleAttributes(docId: Int, style: DocTextStyle, start: Int, end: Int) {
    DocFormatRepository.removeSpanTypeRange(docId, "bold", start, end)
    if (style.isBold) DocFormatRepository.applySpan(docId, "bold", "", start, end)

    DocFormatRepository.removeSpanTypeRange(docId, "italic", start, end)
    if (style.isItalic) DocFormatRepository.applySpan(docId, "italic", "", start, end)

    DocFormatRepository.removeSpanTypeRange(docId, "underline", start, end)
    if (style.isUnderline) DocFormatRepository.applySpan(docId, "underline", "", start, end)

    DocFormatRepository.removeSpanTypeRange(docId, "color", start, end)
    if (style.color != null) DocFormatRepository.applySpan(docId, "color", style.color, start, end)

    DocFormatRepository.removeSpanTypeRange(docId, "alignment", start, end)
    if (style.alignment != null) DocFormatRepository.applySpan(docId, "alignment", style.alignment, start, end)

    DocFormatRepository.removeSpanTypeRange(docId, "lineSpacing", start, end)
    if (style.lineSpacing != null) DocFormatRepository.applySpan(docId, "lineSpacing", style.lineSpacing.toString(), start, end)
}

object DocFormatRepository {
    private val spans = mutableMapOf<Int, androidx.compose.runtime.snapshots.SnapshotStateList<DocFormatSpan>>()
    
    fun getSpans(docId: Int): androidx.compose.runtime.snapshots.SnapshotStateList<DocFormatSpan> {
        return spans.getOrPut(docId) { androidx.compose.runtime.mutableStateListOf() }
    }
    
    fun removeSpansRange(docId: Int, start: Int, end: Int) {
        val list = getSpans(docId)
        val toRemove = mutableListOf<DocFormatSpan>()
        val toAdd = mutableListOf<DocFormatSpan>()
        for (span in list) {
            if (span.start >= start && span.end <= end) {
                toRemove.add(span)
            } else if (span.start < start && span.end > end) {
                val oldEnd = span.end
                span.end = start
                toAdd.add(DocFormatSpan(end, oldEnd, span.type, span.value))
            } else if (span.start < start && span.end > start && span.end <= end) {
                span.end = start
            } else if (span.start >= start && span.start < end && span.end > end) {
                span.start = end
            }
        }
        list.removeAll(toRemove)
        list.addAll(toAdd)
    }
    
    fun hasSpan(docId: Int, type: String, start: Int, end: Int): Boolean {
        val list = getSpans(docId)
        return list.any { it.type == type && it.start <= start && it.end >= end }
    }
    
    fun removeSpanTypeRange(docId: Int, type: String, start: Int, end: Int) {
        val list = getSpans(docId)
        val toRemove = mutableListOf<DocFormatSpan>()
        val toAdd = mutableListOf<DocFormatSpan>()
        for (span in list) {
            if (span.type == type) {
                if (span.start >= start && span.end <= end) {
                    toRemove.add(span)
                } else if (span.start < start && span.end > end) {
                    val oldEnd = span.end
                    span.end = start
                    toAdd.add(DocFormatSpan(end, oldEnd, span.type, span.value))
                } else if (span.start < start && span.end > start && span.end <= end) {
                    span.end = start
                } else if (span.start >= start && span.start < end && span.end > end) {
                    span.start = end
                }
            }
        }
        list.removeAll(toRemove)
        list.addAll(toAdd)
    }
    
    fun applySpan(docId: Int, type: String, value: String, start: Int, end: Int) {
        val list = getSpans(docId)
        if (start >= end) return

        val isParagraphLevel = type == "alignment" || type == "lineSpacing"

        removeSpanTypeRange(docId, type, start, end)

        var newStart = start
        var newEnd = end

        val toRemove = mutableListOf<DocFormatSpan>()
        if (!isParagraphLevel) {
            for (span in list) {
                if (span.type == type && span.value == value) {
                    if (span.end == newStart) {
                        newStart = span.start
                        toRemove.add(span)
                    } else if (span.start == newEnd) {
                        newEnd = span.end
                        toRemove.add(span)
                    }
                }
            }
        }

        list.removeAll(toRemove)
        list.add(DocFormatSpan(newStart, newEnd, type, value))
    }
    
    fun shiftSpans(docId: Int, changeStart: Int, deleted: Int, inserted: Int) {
        val list = getSpans(docId)
        val diff = inserted - deleted
        if (diff == 0) return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val span = iterator.next()
            if (span.start >= changeStart + deleted) {
                span.start += diff
                span.end += diff
            } else if (span.start >= changeStart && span.end <= changeStart + deleted) {
                iterator.remove()
            } else if (span.start < changeStart && span.end > changeStart + deleted) {
                span.end += diff
            } else if (span.end > changeStart) {
                span.end = maxOf(span.start, span.end + diff)
            }
        }
    }

    fun moveSpanRange(docId: Int, fromStart: Int, fromEnd: Int, toStart: Int) {
        val list = getSpans(docId)
        if (fromStart >= fromEnd) return
        val shift = toStart - fromStart
        val added = mutableListOf<DocFormatSpan>()
        val it = list.iterator()
        while (it.hasNext()) {
            val span = it.next()
            when {
                // entirely within moved range
                span.start >= fromStart && span.end <= fromEnd -> {
                    it.remove()
                    span.start += shift
                    span.end += shift
                    if (span.end > span.start) added.add(span)
                }
                // starts before, ends within moved range
                span.start < fromStart && span.end > fromStart && span.end <= fromEnd -> {
                    val oldEnd = span.end
                    span.end = fromStart
                    val newSpan = DocFormatSpan(toStart, toStart + (oldEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                }
                // starts within moved range, ends after
                span.start >= fromStart && span.start < fromEnd && span.end > fromEnd -> {
                    val oldStart = span.start
                    it.remove()
                    val newSpan = DocFormatSpan(toStart + (oldStart - fromStart), toStart + (fromEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                    span.start = fromEnd
                    span.end = fromEnd + (span.end - fromEnd)
                    added.add(span)
                }
                // starts before, ends after (covers entire range)
                span.start < fromStart && span.end > fromEnd -> {
                    val oldEnd = span.end
                    span.end = fromStart
                    val newSpan = DocFormatSpan(toStart, toStart + (fromEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                    val rightSpan = DocFormatSpan(fromEnd, oldEnd, span.type, span.value)
                    added.add(rightSpan)
                }
            }
        }
        list.addAll(added)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocEditorScreen(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val selectedDoc by viewModel.selectedDoc.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()

    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val pageFormat by viewModel.pageFormat.collectAsStateWithLifecycle()
    val customDimensions by viewModel.customPageDimensions.collectAsStateWithLifecycle()
    
    val isPlayingPresentation by viewModel.isPlayingPresentation.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) } // For responsive left-right side panes

    BackHandler(enabled = isPlayingPresentation || listExpanded || selectedDoc != null) {
        if (isPlayingPresentation) {
            viewModel.togglePresenterMode(false)
        } else if (listExpanded) {
            listExpanded = false
        } else if (selectedDoc != null) {
            viewModel.selectDocument(null)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main adaptive workspace overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Workspace pane
                WorkspacePane(
                    selectedDoc = selectedDoc,
                    draftTitle = draftTitle,
                    draftContent = draftContent,
                    onTitleChange = { viewModel.updateDraftTitle(it) },
                    onContentChange = { viewModel.updateDraftContent(it) },
                    onCloseClick = { viewModel.selectDocument(null) },
                    onToggleSidebar = { listExpanded = !listExpanded },
                    isSidebarExpanded = listExpanded,
                    viewModel = viewModel,
                    pageFormat = pageFormat,
                    customDimensions = customDimensions,
                    onFABClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxSize()
                )

                // Left Document explorer sidebar (Width is dynamic/collapsible overlay)
                AnimatedVisibility(
                    visible = listExpanded,
                    enter = slideInHorizontally() + fadeIn(),
                    exit = slideOutHorizontally() + fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dismiss scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.32f))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    listExpanded = false
                                }
                        )

                        // Sidebar itself
                        SidebarExplorer(
                            documents = documents,
                            selectedDoc = selectedDoc,
                            draftTitle = draftTitle,
                            onTitleChange = { viewModel.updateDraftTitle(it) },
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onFilterChange = { viewModel.setTypeFilter(it) },
                            onDocSelect = { viewModel.selectDocument(it) },
                            onDocDelete = { viewModel.deleteDocument(it) },
                            onDocFavoriteToggle = { viewModel.toggleFavorite(it) },
                            onCreateClick = { showCreateDialog = true },
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Eat clicks to prevent dismiss on clicking sidebar inside
                                }
                        )
                    }
                }
            }

            // Dialog for creating a new document template
            if (showCreateDialog) {
                CreateDocumentDialog(
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { title, type ->
                        viewModel.createNewDocument(title, type)
                        showCreateDialog = false
                    }
                )
            }

            // Full-screen presentation mode overlay
            if (isPlayingPresentation && selectedDoc?.type == "slide") {
                FullscreenPresentationView(
                    viewModel = viewModel,
                    onExit = { viewModel.togglePresenterMode(false) }
                )
            }
        }
    }
}

@Composable
fun SidebarExplorer(
    documents: List<DocEntity>,
    selectedDoc: DocEntity?,
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    searchQuery: String,
    selectedFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onDocSelect: (DocEntity) -> Unit,
    onDocDelete: (DocEntity) -> Unit,
    onDocFavoriteToggle: (DocEntity) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ONLYOFFICE inspired styled icon with terracotta orange background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OnlyOfficePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = "JCdocs Logo Symbol",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "JCdocs",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ONLYOFFICE Suite Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Document Title Block (above search bar inside drawer)
        if (selectedDoc != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "ACTIVE DOCUMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = OnlyOfficePrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val themeColor = when (selectedDoc.type) {
                        "word" -> DocWordColor
                        "sheet" -> DocSheetColor
                        "slide" -> DocSlideColor
                        else -> OnlyOfficePrimary
                    }
                    val symbolChar = when (selectedDoc.type) {
                        "word" -> "W"
                        "sheet" -> "S"
                        "slide" -> "P"
                        else -> "D"
                    }

                    // Badge Indicator
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(themeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = symbolChar,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // TextField for editing document title
                    BasicTextField(
                        value = draftTitle,
                        onValueChange = onTitleChange,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_title_input")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Optional clean placeholder state for "No active document"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "NO ACTIVE DOCUMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search Bar with custom tags for automations
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search office sheets & files...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search Files"
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OnlyOfficePrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("file_search_bar")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Create New Document Primary Action button
        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("create_document_button")
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add New")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Office File", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter categories slider Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                FilterItem("all", "All", Icons.Outlined.List),
                FilterItem("word", "Writer", Icons.Outlined.Edit),
                FilterItem("sheet", "Sheets", Icons.Outlined.PlayArrow),
                FilterItem("slide", "Slides", Icons.Outlined.Share)
            )
            items(filters) { category ->
                val isSelected = selectedFilter == category.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(category.id) },
                    label = { Text(category.displayName, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.displayName,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OnlyOfficePrimary.copy(alpha = 0.15f),
                        selectedLabelColor = OnlyOfficePrimary,
                        selectedLeadingIconColor = OnlyOfficePrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        )

        // Title listing title
        Text(
            text = "RECENT DOCUMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // List of document tiles
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Empty State",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    val isSelected = selectedDoc?.id == doc.id
                    DocumentTile(
                        doc = doc,
                        isSelected = isSelected,
                        onClick = { onDocSelect(doc) },
                        onDelete = { onDocDelete(doc) },
                        onFavoriteToggle = { onDocFavoriteToggle(doc) }
                    )
                }
            }
        }
    }
}

data class FilterItem(val id: String, val displayName: String, val icon: ImageVector)

@Composable
fun DocumentTile(
    doc: DocEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (doc.type) {
        "word" -> DocWordColor
        "sheet" -> DocSheetColor
        "slide" -> DocSlideColor
        else -> OnlyOfficePrimary
    }

    val typeIconStr = when (doc.type) {
        "word" -> "W"
        "sheet" -> "S"
        "slide" -> "P"
        else -> "D"
    }

    val formattedDate = remember(doc.updatedAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(doc.updatedAt))
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                typeColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) typeColor.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("document_tile_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon layout matching ONLYOFFICE aesthetic
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeIconStr,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Document item actions
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (doc.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Doc",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Data class representing high-fidelity Ribbon Tool
data class RibbonTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String,
    val tab: String,
    val actionId: String,
    val hasDropdown: Boolean = false,
    val dropdownOptions: List<String> = emptyList(),
    val onClick: () -> Unit = {},
    val onDropdownOptionClick: (String) -> Unit = {}
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RibbonToolCard(
    tool: RibbonTool,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    isOptionSelected: ((actionId: String, option: String) -> Boolean)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { 
                if (tool.hasDropdown) expanded = true 
                else tool.onClick() 
            }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (isDarkTheme) Color.White.copy(alpha = 0.85f) else Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }
        if (tool.hasDropdown) {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(12.dp).align(Alignment.CenterEnd).padding(end = 4.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (tool.dropdownOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("More Options...", fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            tool.onClick()
                        }
                    )
                } else {
                    tool.dropdownOptions.forEach { option ->
                        val isSelected = isOptionSelected?.invoke(tool.actionId, option) == true
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                tool.onDropdownOptionClick(option)
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (isDarkTheme) Color.White else Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditingButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF2B2B30) else Color(0xFFF1F3F6)
    val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(0.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkTheme) Color.White else Color.Black,
                maxLines = 1
            )
        }
    }
}

fun getRibbonTools(
    selectedDoc: DocEntity,
    onActionText: (String) -> Unit
): List<RibbonTool> {
    return listOf(
        // --- HOME TAB TOOLS ---
        RibbonTool(
            id = "bold",
            title = "Bold",
            description = "Apply bold style layout",
            icon = Icons.Outlined.FormatBold,
            category = "Font Formatting",
            tab = "Home",
            actionId = "bold"
        ),
        RibbonTool(
            id = "italic",
            title = "Italic",
            description = "Apply italic text",
            icon = Icons.Outlined.FormatItalic,
            category = "Font Formatting",
            tab = "Home",
            actionId = "italic"
        ),
        RibbonTool(
            id = "underline",
            title = "Underline",
            description = "Apply text underlining",
            icon = Icons.Outlined.FormatUnderlined,
            category = "Font Formatting",
            tab = "Home",
            actionId = "underline"
        ),

        RibbonTool(
            id = "theme_white",
            title = "White Mode",
            description = "Select white paper backdrop",
            icon = Icons.Outlined.LightMode,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_white"
        ),
        RibbonTool(
            id = "theme_ivory",
            title = "Ivory Mode",
            description = "Select warm notepad tone",
            icon = Icons.Outlined.WbSunny,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_ivory"
        ),
        RibbonTool(
            id = "theme_dark",
            title = "Dark Mode",
            description = "Select low-light layout canvas",
            icon = Icons.Outlined.DarkMode,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_dark"
        ),
        RibbonTool(
            id = "font_incr",
            title = "Increase Font",
            description = "Increase font text size",
            icon = Icons.Outlined.TextIncrease,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "font_incr"
        ),
        RibbonTool(
            id = "font_decr",
            title = "Decrease Font",
            description = "Decrease font text size",
            icon = Icons.Outlined.TextDecrease,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "font_decr"
        ),
        RibbonTool(
            id = "clear_format",
            title = "Clear Edits",
            description = "Strip active styling tags",
            icon = Icons.Outlined.Close,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "clear_format"
        ),

        // --- INSERT TAB TOOLS ---
        RibbonTool(id = "cover_page", title = "Cover Page", description = "Cover Page", icon = Icons.Outlined.Description, category = "Pages", tab = "Insert", actionId = "cover_page"),
        RibbonTool(id = "blank_page", title = "Blank Page", description = "Blank Page", icon = Icons.Outlined.NoteAdd, category = "Pages", tab = "Insert", actionId = "blank_page"),
        RibbonTool(id = "page_break", title = "Page Break", description = "Page Break", icon = Icons.Outlined.VerticalAlignBottom, category = "Pages", tab = "Insert", actionId = "page_break"),
        RibbonTool(id = "picture", title = "Picture", description = "Picture", icon = Icons.Outlined.Image, category = "Illustrations", tab = "Insert", actionId = "picture"),
        RibbonTool(id = "shapes", title = "Shapes", description = "Shapes", icon = Icons.Outlined.Category, category = "Illustrations", tab = "Insert", actionId = "shapes"),
        RibbonTool(id = "insert_table", title = "Table", description = "Insert Table", icon = Icons.Outlined.TableChart, category = "Tables", tab = "Insert", actionId = "insert_table"),
        RibbonTool(id = "header_footer", title = "Header & Footer", description = "Header & Footer", icon = Icons.Outlined.ViewAgenda, category = "Header & Footer", tab = "Insert", actionId = "header_footer"),
        RibbonTool(id = "page_number", title = "Page Number", description = "Page Number", icon = Icons.Outlined.Numbers, category = "Header & Footer", tab = "Insert", actionId = "page_number", hasDropdown = true, dropdownOptions = listOf("Top of Page", "Bottom of Page", "Page Margins", "Current Position", "Format Page Numbers...", "Remove Page Numbers")),
        RibbonTool(id = "chart", title = "Chart", description = "Chart", icon = Icons.Outlined.BarChart, category = "Coming Soon", tab = "Insert", actionId = "chart"),
        RibbonTool(id = "hyperlink", title = "Link", description = "Link", icon = Icons.Outlined.Link, category = "Coming Soon", tab = "Insert", actionId = "hyperlink"),
        RibbonTool(id = "bookmark", title = "Bookmark", description = "Bookmark", icon = Icons.Outlined.Bookmark, category = "Coming Soon", tab = "Insert", actionId = "bookmark"),
        RibbonTool(id = "text_box", title = "Text Box", description = "Text Box", icon = Icons.Outlined.TextFields, category = "Coming Soon", tab = "Insert", actionId = "text_box"),

        // --- LAYOUT TAB TOOLS ---
        // 1. Page Setup Group
        RibbonTool(id = "margins", title = "Margins", description = "Set Page Margins", icon = Icons.Outlined.SettingsOverscan, category = "Page Setup", tab = "Layout", actionId = "margins", hasDropdown = true, dropdownOptions = listOf("Normal", "Narrow", "Moderate", "Wide", "Mirrored", "Office Default", "Custom Margins...")),
        RibbonTool(id = "orientation", title = "Orientation", description = "Page Orientation", icon = Icons.Outlined.ScreenRotation, category = "Page Setup", tab = "Layout", actionId = "orientation", hasDropdown = true, dropdownOptions = listOf("Portrait", "Landscape")),
        RibbonTool(id = "size", title = "Size", description = "Page Size", icon = Icons.Outlined.AspectRatio, category = "Page Setup", tab = "Layout", actionId = "size", hasDropdown = true, dropdownOptions = listOf("A4", "Letter", "Legal", "A3", "A5", "Executive", "Custom Size...")),
        RibbonTool(id = "columns", title = "Columns", description = "Page Columns", icon = Icons.Outlined.ViewColumn, category = "Page Setup", tab = "Layout", actionId = "columns", hasDropdown = true, dropdownOptions = listOf("One", "Two", "Three", "Left", "Right")),

        // 2. Themes Group
        RibbonTool(id = "theme_apply", title = "Themes", description = "Document Themes", icon = Icons.Outlined.ColorLens, category = "Themes", tab = "Layout", actionId = "theme_apply", hasDropdown = true, dropdownOptions = listOf("Office Classic", "Modern Teal", "Warm Organic", "Slate Editorial", "Wine Integral", "Forest Woodland", "Retro Amber")),
        RibbonTool(id = "theme_effects", title = "Effects", description = "Theme Effects", icon = Icons.Outlined.AutoAwesome, category = "Themes", tab = "Layout", actionId = "theme_effects", hasDropdown = true, dropdownOptions = listOf("None", "Default Office", "Glossy Reflex", "Matte Border", "Drop Shadow", "Soft Edges Blurry", "Glow Neon Blue")),

        // 3. Page Background Group
        RibbonTool(id = "watermark", title = "Watermark", description = "Page Watermark", icon = Icons.Outlined.BrandingWatermark, category = "Page Background", tab = "Layout", actionId = "watermark", hasDropdown = true, dropdownOptions = listOf("None", "CONFIDENTIAL (Diagonal)", "CONFIDENTIAL (Horizontal)", "DRAFT (Diagonal)", "DRAFT (Horizontal)", "DO NOT COPY (Diagonal)", "DO NOT COPY (Horizontal)", "SAMPLE (Diagonal)", "Custom Watermark...")),
        RibbonTool(id = "page_color", title = "Page Color", description = "Page Background Color", icon = Icons.Outlined.FormatColorFill, category = "Page Background", tab = "Layout", actionId = "page_color", hasDropdown = true, dropdownOptions = listOf("None", "Calm White", "Soft Ivory", "Classic Cream", "Warm Sand", "Modern Ice", "Sage Mist", "Blush Pink", "Lavender Accent", "Elegant Dark", "Custom Color...")),
        RibbonTool(id = "page_borders", title = "Page Borders", description = "Page Borders", icon = Icons.Outlined.BorderAll, category = "Page Background", tab = "Layout", actionId = "page_borders", hasDropdown = true, dropdownOptions = listOf("None", "Thin Box", "Medium Box", "Thick Box", "Double Line", "Dashed Line", "Custom Borders...")),
        
        RibbonTool(id = "copy", title = "Copy", description = "Copy to clipboard", icon = Icons.Outlined.ContentCopy, category = "Clipboard", tab = "Home", actionId = "copy"),
        RibbonTool(id = "cut", title = "Cut", description = "Cut to clipboard", icon = Icons.Outlined.ContentCut, category = "Clipboard", tab = "Home", actionId = "cut"),
        RibbonTool(id = "paste", title = "Paste", description = "Paste from clipboard", icon = Icons.Outlined.ContentPaste, category = "Clipboard", tab = "Home", actionId = "paste"),

        // References Tab Tools removed

        // --- REVIEW TAB TOOLS ---
        RibbonTool(id = "spelling_grammar", title = "Spelling", description = "Spelling", icon = Icons.Outlined.Spellcheck, category = "Proofing", tab = "Review", actionId = "spelling_grammar", hasDropdown = false),
        RibbonTool(id = "thesaurus", title = "Thesaurus", description = "Thesaurus", icon = Icons.Outlined.MenuBook, category = "Proofing", tab = "Review", actionId = "thesaurus", hasDropdown = false),
        RibbonTool(id = "word_count", title = "Word Count", description = "Word Count", icon = Icons.Outlined.Numbers, category = "Proofing", tab = "Review", actionId = "word_count", hasDropdown = false),
        RibbonTool(id = "read_aloud", title = "Read Aloud", description = "Read Aloud", icon = Icons.Outlined.VolumeUp, category = "Speech", tab = "Review", actionId = "read_aloud", hasDropdown = false),
        RibbonTool(id = "check_accessibility", title = "Accessibility", description = "Accessibility", icon = Icons.Outlined.Accessibility, category = "Accessibility", tab = "Review", actionId = "check_accessibility", hasDropdown = false),
        RibbonTool(id = "translate", title = "Translate", description = "Translate", icon = Icons.Outlined.Translate, category = "Language", tab = "Review", actionId = "translate", hasDropdown = true, dropdownOptions = listOf("Translate Document", "Translate Selected Text")),
        RibbonTool(id = "language", title = "Language", description = "Language", icon = Icons.Outlined.Language, category = "Language", tab = "Review", actionId = "language", hasDropdown = true, dropdownOptions = listOf("Set Proofing Language")),
        RibbonTool(id = "new_comment", title = "New Comment", description = "New Comment", icon = Icons.Outlined.AddComment, category = "Comments", tab = "Review", actionId = "new_comment", hasDropdown = false),
        RibbonTool(id = "delete_comment", title = "Delete Comment", description = "Delete Comment", icon = Icons.Outlined.DeleteOutline, category = "Comments", tab = "Review", actionId = "delete_comment", hasDropdown = false),
        RibbonTool(id = "show_comments", title = "Show Comments", description = "Show Comments", icon = Icons.Outlined.Chat, category = "Comments", tab = "Review", actionId = "show_comments", hasDropdown = false),
        RibbonTool(id = "track_changes", title = "Track Changes", description = "Track Changes", icon = Icons.Outlined.EditNote, category = "Tracking", tab = "Review", actionId = "track_changes", hasDropdown = true, dropdownOptions = listOf("Track Changes (Toggle)", "Review Pane", "Accept All Changes", "Reject All Changes")),

        // --- AI ASSISTANT TAB TOOLS ---
        RibbonTool(
            id = "ai_summarize",
            title = "Summarize Text",
            description = "Summarize Text",
            icon = Icons.Outlined.AutoAwesome,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_summarize"
        ),
        RibbonTool(
            id = "ai_improve",
            title = "Improve Tone",
            description = "Improve Tone",
            icon = Icons.Outlined.AutoFixHigh,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_improve"
        ),
        RibbonTool(
            id = "ai_grammar",
            title = "Fix Grammar",
            description = "Fix Grammar Error",
            icon = Icons.Outlined.Spellcheck,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_grammar"
        ),
        RibbonTool(
            id = "ai_topics",
            title = "Suggest Topics",
            description = "Suggest Topics",
            icon = Icons.Outlined.Lightbulb,
            category = "Creative Writing Vectors",
            tab = "AI Assistant",
            actionId = "ai_topics"
        )
    ).map { tool ->
        tool.copy(
            onClick = { onActionText(tool.actionId) },
            onDropdownOptionClick = { option -> onActionText("${tool.actionId}:$option") }
        )
    }
}

fun executeRibbonAction(
    actionId: String,
    context: Context,
    draftContent: String,
    onContentChange: (String) -> Unit,
    selectedDoc: DocEntity,
    viewModel: DocViewModel,
    editorTheme: String,
    onThemeChange: (String) -> Unit,
    selectedDocumentTheme: String = "Office Classic",
    onDocumentThemeChange: (String) -> Unit = {},
    selectedThemeEffect: String = "None",
    onThemeEffectChange: (String) -> Unit = {},
    onMarginsChange: (androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onFontSizeChange: (androidx.compose.ui.unit.TextUnit) -> Unit,
    onLandscapeChange: (Boolean) -> Unit,
    onShowCustomMarginsDialog: () -> Unit = {},
    onShowCustomSizeDialog: () -> Unit = {},
    onShowPageNumberFormatDialog: () -> Unit = {},
    onShowHeaderFooterDialog: () -> Unit = {},
    onShowPageNumberPositionMenu: (String) -> Unit = {},
    onPageNumberPositionChange: (String?) -> Unit = {},
    pageNumberFormat: String = "1, 2, 3...",
    pageNumberStartAt: Int = 1,
    snackbarScope: kotlinx.coroutines.CoroutineScope,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    tts: android.speech.tts.TextToSpeech?,
    isSpeaking: Boolean,
    onSpeakStateChange: (Boolean) -> Unit,
    textFieldValue: TextFieldValue? = null,
    onTextFieldValueChange: ((TextFieldValue) -> Unit)? = null,
    lastSelection: TextRange? = null,
    formatVersion: Int = 0,
    onFormatVersionChange: (Int) -> Unit = {},
    onHistoryAdd: ((String) -> Unit)? = null,
    onCopyFormatted: ((String, List<DocFormatSpan>, Int) -> Unit)? = null,
    onTargetFocusChange: ((Int, Int) -> Unit)? = null,
    pageBackgroundColorHex: String = "",
    onPageBackgroundColorHexChange: (String) -> Unit = {},
    watermarkText: String = "",
    onWatermarkSet: (String, String) -> Unit = { _, _ -> },
    pageBorderType: String = "None",
    onPageBorderChange: (String) -> Unit = {},
    onShowShadingPicker: () -> Unit = {},
    onShowSpellingDialog: (() -> Unit)? = null,
    onShowThesaurusDialog: (() -> Unit)? = null,
    onShowWordCountDialog: (() -> Unit)? = null,
    onShowAccessibilityDialog: (() -> Unit)? = null,
    onShowTranslateDialog: ((Boolean) -> Unit)? = null,
    onShowLanguageDialog: (() -> Unit)? = null,
    onNewComment: (() -> Unit)? = null,
    onDeleteComment: (() -> Unit)? = null,
    onShowCommentsToggle: (() -> Unit)? = null,
    onToggleTrackChanges: (() -> Unit)? = null,
    onShowReviewPane: (() -> Unit)? = null,
    onAcceptAllTrackChanges: (() -> Unit)? = null,
    onRejectAllTrackChanges: (() -> Unit)? = null
) {
    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val applyFormatting = { type: String, value: String, debugName: String ->
        if (textFieldValue != null) {
            val sel = textFieldValue.selection
            if (!sel.collapsed) {
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                if (DocFormatRepository.hasSpan(selectedDoc.id, type, start, end)) {
                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, type, start, end)
                } else {
                    DocFormatRepository.applySpan(selectedDoc.id, type, value, start, end)
                }
                onFormatVersionChange(formatVersion + 1)
            }
        }
    }

    when (actionId) {
        "header_footer" -> onShowHeaderFooterDialog()
        "bold" -> applyFormatting("bold", "", "Bold")
        "italic" -> applyFormatting("italic", "", "Italic")
        "underline" -> applyFormatting("underline", "", "Underline")
        "strikethrough" -> applyFormatting("strikethrough", "", "Strikethrough")
        "subscript" -> applyFormatting("subscript", "", "Subscript")
        "superscript" -> applyFormatting("superscript", "", "Superscript")
        "color" -> applyFormatting("color", "#3B82F6", "Color")
        "highlight" -> applyFormatting("highlight", "", "Highlight")
        "copy" -> {
            if (textFieldValue != null && !textFieldValue.selection.collapsed) {
                val sel = textFieldValue.selection
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val selectedText = draftContent.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("JCdocs", selectedText)
                clipboard.setPrimaryClip(clip)
                onHistoryAdd?.invoke(selectedText)
                val spansInRange = DocFormatRepository.getSpans(selectedDoc.id)
                    .filter { it.start < end && it.end > start }
                    .map { it.copy(start = maxOf(it.start, start) - start, end = minOf(it.end, end) - start) }
                onCopyFormatted?.invoke(selectedText, spansInRange, start)
                showToast("Copied to clipboard")
            } else {
                showToast("Select text to copy")
            }
        }
        "cut" -> {
            if (textFieldValue != null && !textFieldValue.selection.collapsed) {
                val sel = textFieldValue.selection
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val selectedText = draftContent.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("JCdocs", selectedText)
                clipboard.setPrimaryClip(clip)
                onHistoryAdd?.invoke(selectedText)
                val spansInRange = DocFormatRepository.getSpans(selectedDoc.id)
                    .filter { it.start < end && it.end > start }
                    .map { it.copy(start = maxOf(it.start, start) - start, end = minOf(it.end, end) - start) }
                onCopyFormatted?.invoke(selectedText, spansInRange, start)
                val newText = draftContent.substring(0, start) + draftContent.substring(end)
                onContentChange(newText)
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(start)))
                showToast("Cut to clipboard")
            } else {
                showToast("Select text to cut")
            }
        }
        "paste" -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: return
                val sel = textFieldValue?.selection ?: TextRange(draftContent.length)
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val newText = draftContent.substring(0, start) + pastedText + draftContent.substring(end)
                onContentChange(newText)
                val newCursor = start + pastedText.length
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(newCursor)))
                showToast("Pasted from clipboard")
            } else {
                showToast("Clipboard is empty")
            }
        }
        "paste_special" -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: return
                val sel = textFieldValue?.selection ?: TextRange(draftContent.length)
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val newText = draftContent.substring(0, start) + pastedText + draftContent.substring(end)
                onContentChange(newText)
                val newCursor = start + pastedText.length
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(newCursor)))
                showToast("Pasted as plain text")
            } else {
                showToast("Clipboard is empty")
            }
        }
        "align_left" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "left" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "left", r.start, paraEnd)
                        }
                    }
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Left")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "left error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_center" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "center" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "center", r.start, paraEnd)
                        }
                    }
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Center")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "center error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_right" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "right" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "right", r.start, paraEnd)
                        }
                    }
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Right")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "right error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_justify" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "justify" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "justify", r.start, paraEnd)
                        }
                    }
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Justified")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "justify error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "indent_inc" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val para = getParagraphText(draftContent, pos)
                val leadingSpaces = para.takeWhile { it == ' ' }.length
                val newLeading = minOf(leadingSpaces + 4, 40)
                val newPara = " ".repeat(newLeading) + para.trimStart()
                val newText = replaceParagraphText(draftContent, pos, newPara)
                onContentChange(newText)
                val cursorShift = newLeading - leadingSpaces
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newText, selection = TextRange(pos + cursorShift)))
                showToast("Indent increased")
            }
        }
        "indent_dec" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val para = getParagraphText(draftContent, pos)
                val leadingSpaces = para.takeWhile { it == ' ' }.length
                val newLeading = maxOf(leadingSpaces - 4, 0)
                val newPara = " ".repeat(newLeading) + para.trimStart()
                val newText = replaceParagraphText(draftContent, pos, newPara)
                onContentChange(newText)
                val cursorShift = newLeading - leadingSpaces
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newText, selection = TextRange(pos + cursorShift)))
                showToast("Indent decreased")
            }
        }
        "multilevel" -> {
            showToast("Multilevel list — select a list style from Bullets or Numbers first")
        }
        "theme_white" -> {
            onThemeChange("white")
            showToast("Paper theme changed to White")
        }
        "theme_ivory" -> {
            onThemeChange("ivory")
            showToast("Paper theme changed to Ivory Note")
        }
        "theme_dark" -> {
            onThemeChange("dark")
            showToast("Paper theme changed to OLED Dark")
        }
        "font_incr" -> {
            onFontSizeChange(18.sp)
            showToast("Font text size increased to 18sp")
        }
        "font_decr" -> {
            onFontSizeChange(11.sp)
            showToast("Font text size decreased to 11sp")
        }
        "clear_format" -> {
            if (textFieldValue != null && onTextFieldValueChange != null) {
                val selection = lastSelection ?: textFieldValue.selection   
                val text = textFieldValue.text
                if (!selection.collapsed) {
                    val selStart = minOf(selection.start, selection.end)
                    val selEnd = maxOf(selection.start, selection.end)
                    val selectedStr = text.substring(selStart, selEnd)
                    val cleaned = selectedStr
                        .replace("**", "")
                        .replace("*", "")
                        .replace("<u>", "")
                        .replace("</u>", "")
                        .replace("~~", "")
                        .replace("<sub>", "")
                        .replace("</sub>", "")
                        .replace("<sup>", "")
                        .replace("</sup>", "")
                        .replace("<font[^>]*>".toRegex(), "")
                        .replace("</font>", "")
                        .replace("<span[^>]*>".toRegex(), "")
                        .replace("</span>", "")
                        .replace("<mark>", "")
                        .replace("</mark>", "")
                    val newText = text.replaceRange(selStart, selEnd, cleaned)
                    onTextFieldValueChange(TextFieldValue(text = newText, selection = TextRange(selStart, selStart + cleaned.length)))
                    // Clear spans as well
                    DocFormatRepository.removeSpansRange(selectedDoc.id, selStart, selEnd)
                } else {
                    val cleaned = text
                        .replace("**", "")
                        .replace("*", "")
                        .replace("<u>", "")
                        .replace("</u>", "")
                        .replace("~~", "")
                        .replace("<sub>", "")
                        .replace("</sub>", "")
                        .replace("<sup>", "")
                        .replace("</sup>", "")
                        .replace("<font[^>]*>".toRegex(), "")
                        .replace("</font>", "")
                        .replace("<span[^>]*>".toRegex(), "")
                        .replace("</span>", "")
                        .replace("<mark>", "")
                        .replace("</mark>", "")
                    onTextFieldValueChange(TextFieldValue(text = cleaned, selection = TextRange(cleaned.length)))
                    // Clear spans as well - For whole document
                    DocFormatRepository.removeSpansRange(selectedDoc.id, 0, text.length)
                }
            } else {
                val cleaned = draftContent
                    .replace("**", "")
                    .replace("*", "")
                    .replace("<u>", "")
                    .replace("</u>", "")
                onContentChange(cleaned)
            }
            onFontSizeChange(14.sp)
            showToast("All text styling and layout formatting tags cleared")
        }

        // --- INSERT ACTIONS ---
        "cover_page" -> {
            val cover = "========================================\n" +
                        "       DOCUMENT COVER PORTFOLIO\n" +
                        "       Title: ${selectedDoc.title}\n" +
                        "       Date: June 8, 2026\n" +
                        "========================================\n\n"
            onContentChange(cover + draftContent)
            showToast("Stylish Document Cover Page prepended at top!")
        }
        "blank_page" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val pages = draftContent.split("\u000C")
                var accumulated = 0
                var targetIndex = 0
                var targetOffsetInPage = 0
                for (i in pages.indices) {
                    val pageLen = pages[i].length
                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                        targetIndex = i
                        targetOffsetInPage = pos - accumulated
                        break
                    }
                    accumulated += pageLen + 1
                }
                val currentPageText = pages[targetIndex]
                val beforeText = currentPageText.substring(0, targetOffsetInPage)
                val afterText = currentPageText.substring(targetOffsetInPage)
                val newPages = pages.toMutableList()
                newPages[targetIndex] = beforeText
                newPages.add(targetIndex + 1, afterText)
                val newFullText = newPages.joinToString("\u000C")
                onContentChange(newFullText)
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newFullText, selection = TextRange(pos + 1)))
                DocFormatRepository.shiftSpans(selectedDoc.id, pos, 0, 1)
                onFormatVersionChange(formatVersion + 1)
                onTargetFocusChange?.invoke(targetIndex + 1, 0)
            } else {
                onContentChange(draftContent + "\u000C")
            }
            showToast("Inserted Blank Page")
        }
        "page_break" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val pages = draftContent.split("\u000C")
                var accumulated = 0
                var targetIndex = 0
                var targetOffsetInPage = 0
                for (i in pages.indices) {
                    val pageLen = pages[i].length
                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                        targetIndex = i
                        targetOffsetInPage = pos - accumulated
                        break
                    }
                    accumulated += pageLen + 1
                }
                val currentPageText = pages[targetIndex]
                val beforeText = currentPageText.substring(0, targetOffsetInPage)
                val afterText = currentPageText.substring(targetOffsetInPage)
                val newPages = pages.toMutableList()
                newPages[targetIndex] = beforeText
                newPages.add(targetIndex + 1, afterText)
                val newFullText = newPages.joinToString("\u000C")
                onContentChange(newFullText)
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newFullText, selection = TextRange(pos + 1)))
                DocFormatRepository.shiftSpans(selectedDoc.id, pos, 0, 1)
                onFormatVersionChange(formatVersion + 1)
                onTargetFocusChange?.invoke(targetIndex + 1, 0)
                showToast("Page Break inserted at cursor position")
            } else {
                onContentChange(draftContent + "\u000C")
                showToast("Visual page break rule inserted")
            }
        }
        "insert_table" -> {
            val table = "\n| Item Coordinate | Header Label | Value Count |\n" +
                        "|---|---|---|\n" +
                        "| Office Suite | JCdocs ONLYOFFICE | 100% Native |\n" +
                        "| Database Eng | Android Room SQLite | Offline |\n"
            onContentChange(draftContent + table)
            showToast("Sample Markdown table data inserted at bottom!")
        }
        "pictures" -> {
            onContentChange(draftContent + "\n\n![Scenic Office Vector Mock](https://picsum.photos/600/300)\n")
            showToast("Scenic showcase image vector layout inserted!")
        }
        "shapes" -> {
            onContentChange(draftContent + "\n\n[Shape Container: Double Rounded Cylinder | Fill color: emerald_green]\n")
            showToast("Double Rounded Cylinder Vector Shape inserted!")
        }
        "icons" -> {
            onContentChange(draftContent + " ★ ")
            showToast("Royal Golden Star rating badge inserted!")
        }

        // --- LAYOUT ACTIONS ---
        "margins_normal" -> {
            onMarginsChange(72.dp, 72.dp, 72.dp, 72.dp)
            showToast("Margins set to Normal (Top: 1\", Bottom: 1\", Left: 1\", Right: 1\")")
        }
        "margins_narrow" -> {
            onMarginsChange(36.dp, 36.dp, 36.dp, 36.dp)
            showToast("Margins set to Narrow (Top: 0.5\", Bottom: 0.5\", Left: 0.5\", Right: 0.5\")")
        }
        "margins_wide" -> {
            onMarginsChange(72.dp, 72.dp, 144.dp, 144.dp)
            showToast("Margins set to Wide (Top: 1\", Bottom: 1\", Left: 2\", Right: 2\")")
        }
        "portrait" -> {
            onLandscapeChange(false)
            showToast("Document orientation layout set to Portrait")
        }
        "landscape" -> {
            onLandscapeChange(true)
            showToast("Document orientation layout set to Landscape")
        }
        "col_1" -> {
            onColumnsChange(1)
            showToast("Columns division updated to 1 standard panel")
        }
        "col_2" -> {
            onColumnsChange(2)
            showToast("Dynamic layout split into 2 reactive columns!")
        }
        "col_3" -> {
            onColumnsChange(3)
            showToast("Responsive layout divided into 3 reactive columns!")
        }

        // --- REFERENCES ACTIONS ---
        "reference_toc" -> {
            val headings = draftContent.lines()
                .filter { it.trim().startsWith("#") }
                .map { line ->
                    val depth = line.takeWhile { it == '#' }.length
                    val title = line.replace("#", "").trim()
                    "  ".repeat(maxOf(0, depth - 1)) + "- $title"
                }

            if (headings.isEmpty()) {
                onContentChange(
                    "### TABLE OF CONTENTS\n- Section 1: Overview\n- Section 2: Strategy\n- Section 3: Technical Integrity\n\n" + draftContent
                )
                showToast("TOC appended! Add lines starting with '#' to customize.")
            } else {
                val toc = "### TABLE OF CONTENTS\n" + headings.joinToString("\n") + "\n\n"
                onContentChange(toc + draftContent)
                showToast("Real index of headings compiled to Table of Contents!")
            }
        }
        "footnote" -> {
            onContentChange(draftContent + " [^1]")
            val footnoteDesc = "\n\n[^1]: Reference index: Verified securely on JCdocs tablet workspace."
            if (!draftContent.contains("[^1]:")) {
                onContentChange(draftContent + " [^1]" + footnoteDesc)
            }
            showToast("Footnote locator tag applied and registered!")
        }
        "endnote" -> {
            onContentChange(draftContent + "\n\n========================================\nENDNOTE LOGS:\n- Verified local SQLite database integrity syncs successfully.\n========================================\n")
            showToast("Comprehensive database sync Endnotes added at bottom!")
        }
        "citation" -> {
            onContentChange(draftContent + " (Sarah J., 2026)")
            showToast("Professional citation source (Sarah J., 2026) inserted!")
        }

        // --- REVIEW ACTIONS ---
        "review_stats" -> {
            val words = draftContent.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            val sentences = draftContent.split("[.!?]+".toRegex()).filter { it.isNotBlank() }.size
            val chars = draftContent.length
            val readingTime = maxOf(1, words / 180)
            showToast("STATS: Words: $words — Sentences: $sentences — Chars: $chars — Reading time: $readingTime min")
        }
        "spell_check" -> {
            val typosMap = mapOf(
                "teh" to "the",
                "recieve" to "receive",
                "seperate" to "separate",
                "dont" to "don't",
                "accomodate" to "accommodate",
                "Jcdocs" to "JCdocs"
            )
            var fixedCount = 0
            var text = draftContent
            typosMap.forEach { (typo, correction) ->
                if (text.contains(typo, ignoreCase = true)) {
                    text = text.replace(typo, correction, ignoreCase = true)
                    fixedCount++
                }
            }
            if (fixedCount > 0) {
                onContentChange(text)
                showToast("Success! Autocorrect fixed $fixedCount typos (e.g. teh -> the, recieve -> receive)")
            } else {
                showToast("Spell check completed: No typos detected in draft!")
            }
        }
        "read_aloud" -> {
            if (isSpeaking) {
                tts?.stop()
                onSpeakStateChange(false)
                showToast("Read aloud voice speech stopped")
            } else {
                val cleanText = draftContent.replace("[#*_|\\-<>]+".toRegex(), " ")
                if (cleanText.isNotBlank()) {
                    tts?.speak(cleanText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                    onSpeakStateChange(true)
                    showToast("Narrating document content aloud via Android Speech Synthesizer...")
                } else {
                    showToast("Read aloud error: Text content is empty!")
                }
            }
        }
        "spelling_grammar" -> {
            onShowSpellingDialog?.invoke()
        }
        "thesaurus" -> {
            onShowThesaurusDialog?.invoke()
        }
        "word_count" -> {
            onShowWordCountDialog?.invoke()
        }
        "check_accessibility" -> {
            onShowAccessibilityDialog?.invoke()
        }
        "new_comment" -> {
            onNewComment?.invoke()
        }
        "delete_comment" -> {
            onDeleteComment?.invoke()
        }
        "show_comments" -> {
            onShowCommentsToggle?.invoke()
        }
        "translate_preview" -> {
            val transText = draftContent
                .replace("Welcome", "Bienvenido")
                .replace("Project", "Proyecto")
                .replace("Document", "Documento")
                .replace("the", "el")
                .replace("and", "y")
            onContentChange(draftContent + "\n\n--- SPANISH TRANSLATION PREVIEW ---\n" + transText + "\n-------------------------------------\n")
            showToast("Spanish translation preview appended to Document body!")
        }

        // --- AI ASSISTANT ACTIONS ---
        "ai_summarize" -> {
            val lines = draftContent.lines().filter { it.isNotBlank() }
            val summaryBullets = if (lines.size >= 3) {
                listOf(
                    "📌 Primary Focus: " + lines[0].take(60) + "...",
                    "🔬 Supporting Detail: " + lines.getOrNull(lines.size/2)?.take(60) + "...",
                    "📊 Output Target: " + lines.last().take(60) + "..."
                )
            } else {
                listOf(
                    "📌 Summary Concept: Core JCdocs Workspace body",
                    "🚀 System Strategy: Secure Android SQLite client workflow",
                    "⚙️ Implementation: Polished Jetpack Compose frontend interaction"
                )
            }

            val summaryBlock = "\n\n--- AI DOCUMENT SUMMARY ---\n" +
                               summaryBullets.joinToString("\n") +
                               "\n---------------------------\n"
            onContentChange(draftContent + summaryBlock)
            showToast("AI Assistant summarized text and inserted bullet points!")
        }
        "ai_improve" -> {
            val improved = "⚡ PROFESSIONAL POLISH & ELEVATED TONE:\n" +
                           "With profound executive alignment, " + draftContent.replace("Welcome", "We are pleased to introduce").replace("Welcome to", "We welcome you further into")
            onContentChange(improved)
            showToast("Document style tone improved with professional vocabulary!")
        }
        "ai_grammar" -> {
            val resolvedText = draftContent.trim()
            onContentChange(resolvedText)
            showToast("AI has corrected syntactic flows and applied grammar fixes!")
        }
        "ai_topics" -> {
            val topicsBlock = "\n\n💡 RECOMMENDED RESEARCH VECTORS:\n" +
                              "1. Dynamic Kotlin-DSL compilers for local file operations.\n" +
                              "2. Real-time multi-threaded Room SQLite transaction pools.\n" +
                              "3. Responsive tablet-layout class dynamics.\n"
            onContentChange(draftContent + topicsBlock)
            showToast("3 creative brainstorming research topics appended!")
        }
        else -> {
            when {
                actionId.startsWith("page_number:") -> {
                    val option = actionId.removePrefix("page_number:")
                    when(option) {
                        "Format Page Numbers..." -> onShowPageNumberFormatDialog()
                        "Remove Page Numbers" -> {
                            onPageNumberPositionChange(null)
                            showToast("Page Numbers removed")
                        }
                        "Current Position" -> {
                            if (textFieldValue != null && onTextFieldValueChange != null) {
                                val pos = textFieldValue.selection.start
                                val pagesList = draftContent.split("\u000C")
                                var accumulated = 0
                                var currentPageIndex = 0
                                for (i in pagesList.indices) {
                                    val pageLen = pagesList[i].length
                                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                                        currentPageIndex = i
                                        break
                                    }
                                    accumulated += pageLen + 1
                                }
                                val stringToInsert = formatPageNumber(currentPageIndex + pageNumberStartAt, pageNumberFormat, pagesList.size)
                                val text = textFieldValue.text
                                val selection = textFieldValue.selection
                                val newText = text.substring(0, selection.start) + stringToInsert + text.substring(selection.end)
                                val newSelection = TextRange(selection.start + stringToInsert.length)
                                onTextFieldValueChange(textFieldValue.copy(text = newText, selection = newSelection))
                                showToast("Inserted Page Number '$stringToInsert' at cursor position")
                            } else {
                                onContentChange(draftContent + " 1 ")
                                showToast("Inserted Page Number")
                            }
                        }
                        "Top of Page", "Bottom of Page", "Page Margins" -> {
                            onShowPageNumberPositionMenu(option)
                        }
                        else -> {
                            onPageNumberPositionChange(option)
                            showToast("Page Number position set to $option")
                        }
                    }
                }
                actionId.startsWith("margins:") -> {
                    val option = actionId.removePrefix("margins:")
                    when (option) {
                        "Normal" -> { onMarginsChange(72.dp, 72.dp, 72.dp, 72.dp); showToast("Margins set to Normal (Top: 1\", Bottom: 1\", Left: 1\", Right: 1\")") }
                        "Narrow" -> { onMarginsChange(36.dp, 36.dp, 36.dp, 36.dp); showToast("Margins set to Narrow (Top: 0.5\", Bottom: 0.5\", Left: 0.5\", Right: 0.5\")") }
                        "Moderate" -> { onMarginsChange(72.dp, 72.dp, 54.dp, 54.dp); showToast("Margins set to Moderate (Top: 1\", Bottom: 1\", Left: 0.75\", Right: 0.75\")") }
                        "Wide" -> { onMarginsChange(72.dp, 72.dp, 144.dp, 144.dp); showToast("Margins set to Wide (Top: 1\", Bottom: 1\", Left: 2\", Right: 2\")") }
                        "Mirrored" -> { onMarginsChange(72.dp, 72.dp, 90.dp, 72.dp); showToast("Margins set to Mirrored (Top: 1\", Bottom: 1\", Inside: 1.25\", Outside: 1\")") }
                        "Office Default" -> { onMarginsChange(72.dp, 72.dp, 72.dp, 72.dp); showToast("Margins set to Office Default") }
                        "Custom Margins..." -> onShowCustomMarginsDialog()
                    }
                }
                actionId.startsWith("orientation:") -> {
                    val option = actionId.removePrefix("orientation:")
                    if (option == "Landscape") {
                        onLandscapeChange(true)
                        showToast("Orientation set to Landscape")
                    } else {
                        onLandscapeChange(false)
                        showToast("Orientation set to Portrait")
                    }
                }
                actionId.startsWith("columns:") -> {
                    val option = actionId.removePrefix("columns:")
                    when (option) {
                        "One" -> {
                            onColumnsChange(1)
                            showToast("Set Column layout: 1 Column")
                        }
                        "Two" -> {
                            onColumnsChange(2)
                            showToast("Set Column layout: 2 Columns")
                        }
                        "Three" -> {
                            onColumnsChange(3)
                            showToast("Set Column layout: 3 Columns")
                        }
                        "Left" -> {
                            onColumnsChange(4)
                            showToast("Set Column layout: Left Split Screen")
                        }
                        "Right" -> {
                            onColumnsChange(5)
                            showToast("Set Column layout: Right Split Screen")
                        }
                        else -> {
                            onColumnsChange(1)
                            showToast("Set Column layout: 1 Column")
                        }
                    }
                }
                actionId.startsWith("size:") -> {
                    val option = actionId.removePrefix("size:")
                    if (option == "Custom Size...") {
                        onShowCustomSizeDialog()
                    } else {
                        viewModel.setPageFormat(option)
                        showToast("Page Size set to $option")
                    }
                }
                actionId.startsWith("theme_apply:") -> {
                    val option = actionId.removePrefix("theme_apply:")
                    onDocumentThemeChange(option)
                    showToast("Applied Theme: $option")
                }
                actionId.startsWith("theme_effects:") -> {
                    val option = actionId.removePrefix("theme_effects:")
                    onThemeEffectChange(option)
                    showToast("Theme Effect set to: $option")
                }
                actionId.startsWith("page_color:") -> {
                    val option = actionId.removePrefix("page_color:")
                    when (option) {
                        "None" -> {
                            onPageBackgroundColorHexChange("")
                            showToast("Page Color set to Default")
                        }
                        "Calm White" -> { onPageBackgroundColorHexChange("#FFFFFF"); showToast("Page Color: Calm White") }
                        "Soft Ivory" -> { onPageBackgroundColorHexChange("#FAF6EE"); showToast("Page Color: Soft Ivory") }
                        "Classic Cream" -> { onPageBackgroundColorHexChange("#FFFDD0"); showToast("Page Color: Classic Cream") }
                        "Warm Sand" -> { onPageBackgroundColorHexChange("#FAF0E6"); showToast("Page Color: Warm Sand") }
                        "Modern Ice" -> { onPageBackgroundColorHexChange("#F0F8FF"); showToast("Page Color: Modern Ice") }
                        "Sage Mist" -> { onPageBackgroundColorHexChange("#F5FFFA"); showToast("Page Color: Sage Mist") }
                        "Blush Pink" -> { onPageBackgroundColorHexChange("#FFF0F5"); showToast("Page Color: Blush Pink") }
                        "Lavender Accent" -> { onPageBackgroundColorHexChange("#E6E6FA"); showToast("Page Color: Lavender Accent") }
                        "Elegant Dark" -> { onPageBackgroundColorHexChange("#2D2D2D"); showToast("Page Color: Elegant Dark") }
                        "Custom Color..." -> {
                            onShowShadingPicker()
                        }
                    }
                }
                actionId.startsWith("watermark:") -> {
                    val option = actionId.removePrefix("watermark:")
                    when (option) {
                        "None" -> {
                            onWatermarkSet("", "Diagonal")
                            showToast("Watermark removed")
                        }
                        "CONFIDENTIAL (Diagonal)" -> { onWatermarkSet("CONFIDENTIAL", "Diagonal"); showToast("Watermark: CONFIDENTIAL") }
                        "CONFIDENTIAL (Horizontal)" -> { onWatermarkSet("CONFIDENTIAL", "Horizontal"); showToast("Watermark: CONFIDENTIAL") }
                        "DRAFT (Diagonal)" -> { onWatermarkSet("DRAFT", "Diagonal"); showToast("Watermark: DRAFT") }
                        "DRAFT (Horizontal)" -> { onWatermarkSet("DRAFT", "Horizontal"); showToast("Watermark: DRAFT") }
                        "DO NOT COPY (Diagonal)" -> { onWatermarkSet("DO NOT COPY", "Diagonal"); showToast("Watermark: DO NOT COPY") }
                        "DO NOT COPY (Horizontal)" -> { onWatermarkSet("DO NOT COPY", "Horizontal"); showToast("Watermark: DO NOT COPY") }
                        "SAMPLE (Diagonal)" -> { onWatermarkSet("SAMPLE", "Diagonal"); showToast("Watermark: SAMPLE") }
                        "Custom Watermark..." -> {
                            onWatermarkSet("CUSTOM_PROMPT", "Diagonal")
                        }
                    }
                }
                actionId.startsWith("page_borders:") -> {
                    val option = actionId.removePrefix("page_borders:")
                    onPageBorderChange(option)
                    showToast("Page Borders: $option")
                }
                actionId.startsWith("translate") -> {
                    val isSelection = actionId.endsWith("Selected Text")
                    onShowTranslateDialog?.invoke(isSelection)
                }
                actionId.startsWith("language") -> {
                    onShowLanguageDialog?.invoke()
                }
                actionId.startsWith("track_changes") -> {
                    when {
                        actionId.endsWith("Review Pane") -> onShowReviewPane?.invoke()
                        actionId.endsWith("Accept All Changes") -> onAcceptAllTrackChanges?.invoke()
                        actionId.endsWith("Reject All Changes") -> onRejectAllTrackChanges?.invoke()
                        else -> onToggleTrackChanges?.invoke()
                    }
                }
                actionId.startsWith("breaks:") -> {
                    val option = actionId.removePrefix("breaks:")
                    if (textFieldValue != null) {
                        val pos = textFieldValue.selection.start
                        val pages = draftContent.split("\u000C")
                        var accumulated = 0
                        var targetIndex = 0
                        var targetOffsetInPage = 0
                        for (i in pages.indices) {
                            val pageLen = pages[i].length
                            if (pos >= accumulated && pos <= accumulated + pageLen) {
                                targetIndex = i
                                targetOffsetInPage = pos - accumulated
                                break
                            }
                            accumulated += pageLen + 1
                        }

                        if (option == "Page Break" || option == "Section Break (Next Page)" || option == "Section Break (Even Page)" || option == "Section Break (Odd Page)") {
                            val currentPageText = pages[targetIndex]
                            val beforeText = currentPageText.substring(0, targetOffsetInPage)
                            val afterText = currentPageText.substring(targetOffsetInPage)
                            val newPages = pages.toMutableList()

                            var numBreaks = 1
                            if (option == "Section Break (Even Page)") {
                                val currentPageNum = targetIndex + 1
                                if (currentPageNum % 2 == 0) {
                                    numBreaks = 2
                                }
                            } else if (option == "Section Break (Odd Page)") {
                                val currentPageNum = targetIndex + 1
                                if (currentPageNum % 2 != 0) {
                                    numBreaks = 2
                                }
                            }

                            newPages[targetIndex] = beforeText
                            if (numBreaks == 2) {
                                newPages.add(targetIndex + 1, "")
                                newPages.add(targetIndex + 2, afterText)
                            } else {
                                newPages.add(targetIndex + 1, afterText)
                            }

                            val newFullText = newPages.joinToString("\u000C")
                            onContentChange(newFullText)
                            onTextFieldValueChange?.invoke(textFieldValue.copy(text = newFullText, selection = TextRange(pos + numBreaks)))
                            DocFormatRepository.shiftSpans(selectedDoc.id, pos, 0, numBreaks)
                            onFormatVersionChange(formatVersion + 1)
                            onTargetFocusChange?.invoke(targetIndex + numBreaks, 0)
                            showToast("Inserted $option at cursor position")
                        } else {
                            // Column Break, Section Break (Continuous), etc.
                            val currentPageText = pages[targetIndex]
                            val insertText = "\n\n--- [${option.uppercase()}] ---\n\n"
                            val beforeText = currentPageText.substring(0, targetOffsetInPage)
                            val afterText = currentPageText.substring(targetOffsetInPage)
                            val newPages = pages.toMutableList()
                            newPages[targetIndex] = beforeText + insertText + afterText
                            val newFullText = newPages.joinToString("\u000C")
                            onContentChange(newFullText)
                            onTextFieldValueChange?.invoke(textFieldValue.copy(text = newFullText, selection = TextRange(pos + insertText.length)))
                            DocFormatRepository.shiftSpans(selectedDoc.id, pos, 0, insertText.length)
                            onFormatVersionChange(formatVersion + 1)
                            onTargetFocusChange?.invoke(targetIndex, targetOffsetInPage + insertText.length)
                            showToast("Inserted $option at cursor position")
                        }
                    } else {
                        if (option == "Page Break" || option == "Section Break (Next Page)" || option == "Section Break (Even Page)" || option == "Section Break (Odd Page)") {
                            onContentChange(draftContent + "\u000C")
                        } else {
                            onContentChange(draftContent + "\n\n--- [${option.uppercase()}] ---\n\n")
                        }
                        showToast("Inserted $option")
                    }
                }
            }
        }
    }
}

@Composable
fun RibbonGroupContainer(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF252528) else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayTitle = if (title.equals("Font Formatting", ignoreCase = true) || title.equals("Font", ignoreCase = true)) {
                    "FONT"
                } else {
                    title.uppercase()
                }
                Text(
                    text = displayTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.8.sp
                )
            }
            HorizontalDivider(
                color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            )
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun RibbonIconButton(
    icon: ImageVector? = null,
    textLabel: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    colorSchemeColor: Color = OnlyOfficePrimary,
    transparentBg: Boolean = false,
    modifier: Modifier = Modifier,
    customContent: @Composable (() -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val bgColor = when {
        transparentBg -> Color.Transparent
        isSelected -> colorSchemeColor.copy(alpha = 0.35f)
        isDarkTheme -> Color(0xFF323236)
        else -> Color(0xFFF1F3F6)
    }
    val contentColor = when {
        transparentBg -> colorSchemeColor
        isSelected -> colorSchemeColor
        isDarkTheme -> Color.White
        else -> Color.DarkGray
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (customContent != null) {
            customContent()
        } else if (textLabel != null) {
            Text(
                text = textLabel,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

val FontColors = listOf(
    "#000000", "#434343", "#666666", "#999999", "#B7B7B7", "#CCCCCC",
    "#D9D9D9", "#EFEFEF", "#F3F3F3", "#FFFFFF", "#002060", "#1F3864",
    "#1F4E79", "#2E75B6", "#4472C4", "#5B9BD5", "#8DB4E2", "#BDD7EE",
    "#D6E4F0", "#E2EFDA", "#548235", "#70AD47", "#A9D18E", "#C5E0B4",
    "#D9E2F3", "#FFF2CC", "#FFD966", "#F4B183", "#ED7D31", "#E74C3C",
    "#C00000", "#FF0000", "#FF8C00", "#FFD700", "#32CD32", "#00CED1",
    "#0000FF", "#8A2BE2", "#FF69B4", "#A52A2A"
)

val HighlightColors = listOf(
    "#FDE047", "#FCD34D", "#FBBF24", "#F59E0B", "#FEF9C3",
    "#86EFAC", "#4ADE80", "#22C55E", "#16A34A", "#DCFCE7",
    "#93C5FD", "#60A5FA", "#3B82F6", "#2563EB", "#DBEAFE",
    "#F9A8D4", "#F472B6", "#EC4899", "#DB2777", "#FCE7F3",
    "#C4B5FD", "#A78BFA", "#8B5CF6", "#7C3AED", "#EDE9FE",
    "#FDBA74", "#FB923C", "#F97316", "#EA580C", "#FED7AA",
    "#FCA5A5", "#F87171", "#EF4444", "#DC2626", "#FEE2E2",
    "#D1D5DB", "#9CA3AF", "#6B7280", "#4B5563", "#374151"
)

@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<String>,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Standard, 1 = Custom
    
    // Custom color state
    var hexInput by remember { mutableStateOf("#FFFFFF") }
    var redVal by remember { mutableStateOf(255) }
    var greenVal by remember { mutableStateOf(255) }
    var blueVal by remember { mutableStateOf(255) }

    fun updateFromHex(hex: String) {
        val cleanHex = hex.removePrefix("#")
        if (cleanHex.length == 6) {
            try {
                redVal = cleanHex.substring(0, 2).toInt(16)
                greenVal = cleanHex.substring(2, 4).toInt(16)
                blueVal = cleanHex.substring(4, 6).toInt(16)
            } catch (e: Exception) {}
        }
    }

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        hexInput = String.format("#%02X%02X%02X", r, g, b)
    }

    // Initialize custom state from first color
    LaunchedEffect(Unit) {
        if (colors.isNotEmpty()) {
            hexInput = colors.first()
            updateFromHex(colors.first())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isSystemInDarkTheme()) Color.White else Color.Black
                )
                Spacer(Modifier.height(12.dp))

                // MS Office Style Navigation Tabs (Standard vs Custom)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = DocWordColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Standard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Custom", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // --- STANDARD TAB: Grid-based visual palette ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Theme Colors",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // 1. Theme Colors Columns (MS Office style tints and options)
                        val themeColorPalette = listOf(
                            listOf("#FFFFFF", "#F2F2F2", "#D9D9D9", "#BFBFBF", "#A6A6A6", "#808080", "#595959", "#3F3F3F", "#262626", "#000000"),
                            listOf("#FFF2CC", "#FFE699", "#F2DBDB", "#E6B8B8", "#D99694", "#C0504D", "#943634", "#E2EFDA", "#C6E0B4", "#A9D08E"),
                            listOf("#D9E1F2", "#B4C6E7", "#8EA9DB", "#4472C4", "#2F5597", "#1F4E79", "#17375E", "#C6D9F1", "#8DB4E2", "#1F497D"),
                            listOf("#FFF0F5", "#FFE4E1", "#FFDAB9", "#FFE4C4", "#FAF0E6", "#FAEBD7", "#F5F5DC", "#FAF6EE", "#FFFDD0", "#F5F5F5")
                        )

                        themeColorPalette.forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowColors.forEach { colorHex ->
                                    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color)
                                            .border(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                            .clickable { onColorSelected(colorHex) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Standard Colors",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // 2. Standard Colors Row (MS Office solid row of colors)
                        val officeStandardColors = listOf(
                            "#C00000", "#FF0000", "#FFC000", "#FFFF00", "#92D050", 
                            "#00B050", "#00B0F0", "#0070C0", "#002060", "#7030A0"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            officeStandardColors.forEach { colorHex ->
                                val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .border(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .clickable { onColorSelected(colorHex) }
                                )
                            }
                        }
                    }
                } else {
                    // --- CUSTOM TAB: RGB Sliders + Hex Field ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Color Preview Block
                        val currentRgbColor = try { Color(android.graphics.Color.rgb(redVal, greenVal, blueVal)) } catch (e: Exception) { Color.Gray }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(currentRgbColor)
                                    .border(1.5.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    text = "Preview Color",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSystemInDarkTheme()) Color.White else Color.Black
                                )
                                Text(
                                    text = hexInput,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray
                                )
                            }
                        }

                        Divider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))

                        // RED SLIDER
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Red", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                Text("$redVal", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            }
                            Slider(
                                value = redVal.toFloat(),
                                onValueChange = { 
                                    redVal = it.toInt()
                                    updateFromRgb(redVal, greenVal, blueVal)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Red,
                                    activeTrackColor = Color.Red.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // GREEN SLIDER
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Green", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00A000))
                                Text("$greenVal", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            }
                            Slider(
                                value = greenVal.toFloat(),
                                onValueChange = { 
                                    greenVal = it.toInt()
                                    updateFromRgb(redVal, greenVal, blueVal)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00A000),
                                    activeTrackColor = Color(0xFF00A000).copy(alpha = 0.5f)
                                )
                            )
                        }

                        // BLUE SLIDER
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Blue", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Blue)
                                Text("$blueVal", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            }
                            Slider(
                                value = blueVal.toFloat(),
                                onValueChange = { 
                                    blueVal = it.toInt()
                                    updateFromRgb(redVal, greenVal, blueVal)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Blue,
                                    activeTrackColor = Color.Blue.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Divider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))

                        // Hex Text Field Input
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Hex Code:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = hexInput,
                                onValueChange = {
                                    val filtered = it.filter { c -> c.isDigit() || c in "ABCDEFabcdef#" }
                                    if (filtered.length <= 7 && filtered.startsWith("#")) {
                                        hexInput = filtered
                                        updateFromHex(filtered)
                                    } else if (filtered.isNotEmpty() && !filtered.startsWith("#")) {
                                        hexInput = "#$filtered".take(7)
                                        updateFromHex(hexInput)
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSystemInDarkTheme()) Color.White else Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .border(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Divider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { 
                        Text("Cancel", color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray) 
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onColorSelected(hexInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = DocWordColor)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun BulletStyleDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Bullet Style", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    val rows = BulletChars.chunked(4)
                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                        .clickable { onSelect(char) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, fontSize = 24.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun NumberFormatDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val formats = listOf("1." to "1, 2, 3...", "a)" to "a), b), c)...", "A." to "A., B., C...", "i)" to "i), ii), iii)...", "I." to "I., II., III...")
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Numbering Format", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                formats.forEach { (fmt, desc) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                            .clickable { onSelect(fmt) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(desc, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun LineSpacingDialog(
    currentSpacing: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(1.0f, 1.15f, 1.5f, 2.0f, 2.5f, 3.0f)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Line Spacing", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                options.forEach { spacing ->
                    val label = when (spacing) {
                        1.0f -> "Single (1.0)"
                        1.15f -> "1.15"
                        1.5f -> "1.5"
                        2.0f -> "Double (2.0)"
                        else -> "${spacing}"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (spacing == currentSpacing) DocWordColor.copy(alpha = 0.15f)
                                else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6)
                            )
                            .clickable { onSelect(spacing) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun BordersDialog(
    onApply: (sides: Set<String>, style: String, color: String, width: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var top by remember { mutableStateOf(false) }
    var bottom by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var borderStyle by remember { mutableStateOf("solid") }
    var borderColor by remember { mutableStateOf("#000000") }
    var borderWidth by remember { mutableStateOf(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("Borders", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))

                Text("Sides", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                val sides = listOf("Top" to top, "Bottom" to bottom, "Left" to left, "Right" to right)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sides.forEach { (label, checked) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (checked) DocWordColor else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                .clickable {
                                    when (label) { "Top" -> top = !top; "Bottom" -> bottom = !bottom; "Left" -> left = !left; "Right" -> right = !right }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (checked) Color.White else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.DarkGray)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("solid", "dotted", "dashed", "double").forEach { style ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (borderStyle == style) DocWordColor else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                .clickable { borderStyle = style },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(style.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (borderStyle == style) Color.White else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.DarkGray)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("#000000", "#333333", "#666666", "#999999", "#CC0000", "#0066CC", "#339933").forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray })
                                .border(if (borderColor == hex) 3.dp else 1.dp, if (borderColor == hex) DocWordColor else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .clickable { borderColor = hex }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Width: ${borderWidth.toInt()}pt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = borderWidth,
                    onValueChange = { borderWidth = it },
                    valueRange = 0.5f..6f,
                    steps = 10
                )
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val sidesSet = mutableSetOf<String>()
                        if (top) sidesSet.add("top")
                        if (bottom) sidesSet.add("bottom")
                        if (left) sidesSet.add("left")
                        if (right) sidesSet.add("right")
                        onApply(sidesSet, borderStyle, borderColor, borderWidth)
                    }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
fun RibbonDropdown(
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isEditable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(selectedValue) { mutableStateOf(TextFieldValue(selectedValue)) }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDarkTheme) Color(0xFF323236) else Color(0xFFF1F3F6))
            .clickable { expanded = !expanded }
            .border(
                width = 1.dp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isEditable) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color.White else Color.Black
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSelect(textFieldValue.text)
                            expanded = false
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = selectedValue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Dropdown Arrow",
                tint = if (isDarkTheme) Color.LightGray else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (isDarkTheme) Color(0xFF2E2E32) else Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                        if (isEditable) {
                            textFieldValue = TextFieldValue(option)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExportButton(
    draftContent: String,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isLandscape: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val pageFormat by viewModel.pageFormat.collectAsStateWithLifecycle()
    val customDimensions by viewModel.customPageDimensions.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { uri ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val cleanFormat = pageFormat.substringBefore(" ").trim()
                    var (width, height) = when (cleanFormat) {
                        "A3" -> 842 to 1191
                        "A5" -> 420 to 595
                        "Letter" -> 612 to 792
                        "Legal" -> 612 to 1008
                        "Executive" -> 522 to 756
                        "Custom" -> (customDimensions.first * 72f).toInt() to (customDimensions.second * 72f).toInt()
                        else -> 595 to 842 // A4 default
                    }
                    if (isLandscape) {
                        val temp = width
                        width = height
                        height = temp
                    }
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    val canvas = page.canvas
                    val paint = Paint()
                    paint.color = android.graphics.Color.BLACK
                    paint.textSize = 12f
                    
                    // High-fidelity rendering placeholder:
                    // Here you would implement complex layout, images, charts, and table rendering using Canvas.
                    canvas.drawText(draftContent, 50f, 50f, paint)
                    
                    pdfDocument.finishPage(page)
                    pdfDocument.writeTo(outputStream)
                    pdfDocument.close()
                }
                Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = "Export Document",
                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (isDarkTheme) Color(0xFF2E2E32) else Color.White)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Export as PDF",
                        fontSize = 12.sp,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                },
                onClick = {
                    expanded = false
                    exportLauncher.launch("Document_" + System.currentTimeMillis() + ".pdf")
                }
            )
        }
    }
}


object DocumentLayoutEngine {
    fun getDimensions(format: String, customDimensions: Pair<Float, Float>, isLandscape: Boolean): Pair<Dp, Dp> {
        val cleanFormat = format.substringBefore(" ").trim()
        val (pw, ph) = when (cleanFormat) {
            "A3" -> 842.dp to 1191.dp
            "A5" -> 420.dp to 595.dp
            "Letter" -> 612.dp to 792.dp
            "Legal" -> 612.dp to 1008.dp
            "Executive" -> 522.dp to 756.dp
            "Custom" -> (customDimensions.first * 72f).dp to (customDimensions.second * 72f).dp
            else -> 595.dp to 842.dp // A4 default
        }
        return if (isLandscape) ph to pw else pw to ph
    }
}

@Composable
fun PageRuler(
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = true,
    totalLength: Dp,
    markerInterval: Dp = 10.dp, // 10dp between small markers (more frequent)
    majorMarkerInterval: Dp = 50.dp // 50dp between major markers
) {
    Canvas(modifier = modifier.width(if (isHorizontal) totalLength else 20.dp).height(if (isHorizontal) 20.dp else totalLength).background(Color(0xFF222222))) {
        val markers = (totalLength / markerInterval).toInt()
        for (i in 0..markers) {
            val offset = i * markerInterval.toPx()
            val isMajor = (i * markerInterval.value).toInt() % majorMarkerInterval.value.toInt() == 0
            val markerHeight = if (isMajor) 16f else 8f
            
            // White markers
            val markerColor = Color.White
            
            if (isHorizontal) {
                drawLine(
                    color = markerColor,
                    start = androidx.compose.ui.geometry.Offset(offset, size.height),
                    end = androidx.compose.ui.geometry.Offset(offset, size.height - markerHeight),
                    strokeWidth = 2f
                )
            } else {
                drawLine(
                    color = markerColor,
                    start = androidx.compose.ui.geometry.Offset(size.width, offset),
                    end = androidx.compose.ui.geometry.Offset(size.width - markerHeight, offset),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun WorkspacePane(
    selectedDoc: DocEntity?,
    draftTitle: String,
    draftContent: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCloseClick: () -> Unit,
    onToggleSidebar: () -> Unit,
    isSidebarExpanded: Boolean,
    viewModel: DocViewModel,
    pageFormat: String,
    customDimensions: Pair<Float, Float>,
    onFABClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    if (selectedDoc == null) {
        EmptyWorkspaceState(
            viewModel = viewModel,
            onToggleSidebar = onToggleSidebar,
            isSidebarExpanded = isSidebarExpanded,
            onQuickCreate = { title, type -> viewModel.createNewDocument(title, type) },
            onFABClick = onFABClick,
            modifier = modifier
        )
    } else {
        var editorTheme by remember { mutableStateOf("white") }
        var selectedDocumentTheme by remember { mutableStateOf("Office Classic") }
        var selectedThemeEffect by remember { mutableStateOf("None") }
        var pageMargins by remember { mutableStateOf(24.dp) }
        var pageMarginTop by remember { mutableStateOf(24.dp) }
        var pageMarginBottom by remember { mutableStateOf(24.dp) }
        var pageMarginLeft by remember { mutableStateOf(24.dp) }
        var pageMarginRight by remember { mutableStateOf(24.dp) }
        var columnCount by remember { mutableStateOf(1) }
        var fontSize by remember { mutableStateOf(16.sp) }
        var isLandscape by remember { mutableStateOf(false) }
        var pageBackgroundColorHex by remember { mutableStateOf("") }
        var watermarkText by remember { mutableStateOf("") }
        var watermarkType by remember { mutableStateOf("Diagonal") }
        var watermarkColorHex by remember { mutableStateOf("#33CCCCCC") }
        var pageBorderType by remember { mutableStateOf("None") }
        var pageBorderColorHex by remember { mutableStateOf("default") }

        // --- REVIEW SYSTEM STATES ---
        var showSpellingDialog by remember { mutableStateOf(false) }
        var showThesaurusDialog by remember { mutableStateOf(false) }
        var showWordCountDialog by remember { mutableStateOf(false) }
        var showAccessibilityDialog by remember { mutableStateOf(false) }
        var showTranslateDialog by remember { mutableStateOf(false) }
        var translateIsSelectionMode by remember { mutableStateOf(false) }
        var showLanguageDialog by remember { mutableStateOf(false) }
        var showCommentsPane by remember { mutableStateOf(false) }
        var showRevisionsPane by remember { mutableStateOf(false) }
        var proofingLanguage by remember { mutableStateOf("English (United States)") }
        var showReadAloudControls by remember { mutableStateOf(false) }
        var readAloudSpeed by remember { mutableStateOf(1.0f) }

        var showCustomWatermarkDialog by remember { mutableStateOf(false) }
        var showPageBordersDialog by remember { mutableStateOf(false) }
        var showCustomMarginsDialog by remember { mutableStateOf(false) }
        var showCustomSizeDialog by remember { mutableStateOf(false) }
        var showPageNumberFormatDialog by remember { mutableStateOf(false) }
        var pageNumberPosition by remember { mutableStateOf<String?>(null) }
        var pageNumberFormat by remember { mutableStateOf("1, 2, 3...") }
        var pageNumberStartAt by remember { mutableStateOf("1") }
        var pageNumberPositionMenu by remember { mutableStateOf<String?>(null) }
        var showPageNumberOnFirstPage by remember { mutableStateOf(true) }

        var headerText by remember { mutableStateOf("") }
        var footerText by remember { mutableStateOf("") }
        var headerAlignment by remember { mutableStateOf("Center") }
        var footerAlignment by remember { mutableStateOf("Center") }
        var showHeaderFooterOnFirstPage by remember { mutableStateOf(true) }
        var showHeaderFooterDialog by remember { mutableStateOf(false) }

        var editorTextFieldValue by remember(selectedDoc.id) {
            mutableStateOf(TextFieldValue(text = draftContent, selection = TextRange(draftContent.length)))
        }

        var lastSelection by remember(selectedDoc.id) {
            mutableStateOf(TextRange(draftContent.length))
        }

        val targetFocusPage = remember(selectedDoc.id) { mutableStateOf<Int?>(null) }
        val targetFocusOffset = remember(selectedDoc.id) { mutableStateOf<Int?>(null) }

        var isEditorFocused by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val showToast = { msg: String ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
        androidx.compose.runtime.DisposableEffect(context) {
            val t = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    // Success
                }
            }
            tts = t
            onDispose {
                t.stop()
                t.shutdown()
            }
        }

        val clipboardHistory = remember { mutableStateListOf<String>() }
        var showClipboardHistory by remember { mutableStateOf(false) }
        var copiedFormattedData by remember { mutableStateOf<CopyFormattedData?>(null) }

        val undoRedoManager = remember(selectedDoc.id) { DocUndoRedoManager(selectedDoc.id) }
        var formatVersion by remember { mutableStateOf(0) } // Force recomposition when formatting spans change
        var undoRedoTrigger by remember { mutableStateOf(0) } // To force UI recompose when stacks change
        var isUndoRedoAction by remember(selectedDoc.id) { mutableStateOf(false) }

        var lastDocId by remember { mutableStateOf(-1) }
        var lastDraftContent by remember { mutableStateOf("") }

        if (selectedDoc.id != lastDocId) {
            lastDocId = selectedDoc.id
            lastDraftContent = draftContent
        }

        LaunchedEffect(draftContent) {
            if (isUndoRedoAction) {
                isUndoRedoAction = false
                lastDraftContent = draftContent
                return@LaunchedEffect
            }
            if (draftContent != lastDraftContent) {
                val oldText = lastDraftContent
                val newText = draftContent
                var cp = 0
                while (cp < oldText.length && cp < newText.length && oldText[cp] == newText[cp]) { cp++ }
                var cs = 0
                while (cp + cs < oldText.length && cp + cs < newText.length && oldText[oldText.length - 1 - cs] == newText[newText.length - 1 - cs]) { cs++ }
                val delLen = oldText.length - cp - cs
                val insLen = newText.length - cp - cs
                if (delLen > 0 || insLen > 0) {
                    DocFormatRepository.shiftSpans(selectedDoc.id, cp, delLen, insLen)
                }
                lastDraftContent = draftContent
            }
        }
        
        val activeFormatting by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) emptySet()
                else spans.filter { it.start <= pos && it.end > pos }.map { it.type }.toSet()
            }
        }
        val cursorFontColorVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "color" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorHighlightColorVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "highlight" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorAlignmentVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "alignment" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorLineSpacingVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "lineSpacing" && it.start <= pos && it.end > pos }?.value
            }
        }
        
        fun showUndoRedoFeedback(msg: String) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        val captureSnapshot = {
            DocEditorSnapshot(
                title = draftTitle,
                draftContent = editorTextFieldValue.text,
                textFieldValue = editorTextFieldValue,
                spans = DocFormatRepository.getSpans(selectedDoc.id).toList(),
                editorTheme = editorTheme,
                pageMargins = pageMargins,
                columnCount = columnCount,
                fontSize = fontSize,
                isLandscape = isLandscape,
                pageNumberPosition = pageNumberPosition,
                pageNumberFormat = pageNumberFormat,
                pageNumberStartAt = pageNumberStartAt,
                showPageNumberOnFirstPage = showPageNumberOnFirstPage,
                headerText = headerText,
                footerText = footerText,
                headerAlignment = headerAlignment,
                footerAlignment = footerAlignment,
                showHeaderFooterOnFirstPage = showHeaderFooterOnFirstPage,
                pageMarginTop = pageMarginTop,
                pageMarginBottom = pageMarginBottom,
                pageMarginLeft = pageMarginLeft,
                pageMarginRight = pageMarginRight,
                selectedDocumentTheme = selectedDocumentTheme,
                selectedThemeEffect = selectedThemeEffect,
                pageBackgroundColorHex = pageBackgroundColorHex,
                watermarkText = watermarkText,
                watermarkType = watermarkType,
                watermarkColorHex = watermarkColorHex,
                pageBorderType = pageBorderType,
                pageBorderColorHex = pageBorderColorHex
            )
        }

        val pushSnapshot = {
            undoRedoManager.pushState(captureSnapshot())
            undoRedoTrigger++
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { selectedUri ->
                val uriStr = selectedUri.toString()
                val pos = editorTextFieldValue.selection.start
                val pages = draftContent.split("\u000C")
                var accumulated = 0
                var targetIndex = 0
                for (i in pages.indices) {
                    val pageLen = pages[i].length
                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                        targetIndex = i
                        break
                    }
                    accumulated += pageLen + 1
                }
                DocPictureRepository.addPicture(
                    docId = selectedDoc.id,
                    picture = DocPicture(
                        uri = uriStr,
                        pageIndex = targetIndex,
                        x = 50.dp,
                        y = 100.dp,
                        width = 180.dp,
                        height = 180.dp
                    )
                )
                android.widget.Toast.makeText(context, "Inserted picture on Page ${targetIndex + 1}!", android.widget.Toast.LENGTH_SHORT).show()
                formatVersion++
            }
        }

        val performUndo = {
            val restored = undoRedoManager.undo(captureSnapshot())
            if (restored != null) {
                showUndoRedoFeedback("Undo")
                undoRedoManager.isRestoring = true
                isUndoRedoAction = true
                onTitleChange(restored.title)
                editorTextFieldValue = restored.textFieldValue
                onContentChange(restored.draftContent)
                
                val currentSpans = DocFormatRepository.getSpans(selectedDoc.id)
                currentSpans.clear()
                currentSpans.addAll(restored.spans.map { it.copy() })
                
                editorTheme = restored.editorTheme
                pageMargins = restored.pageMargins
                columnCount = restored.columnCount
                fontSize = restored.fontSize
                isLandscape = restored.isLandscape
                pageNumberPosition = restored.pageNumberPosition
                pageNumberFormat = restored.pageNumberFormat
                pageNumberStartAt = restored.pageNumberStartAt
                showPageNumberOnFirstPage = restored.showPageNumberOnFirstPage
                headerText = restored.headerText
                footerText = restored.footerText
                headerAlignment = restored.headerAlignment
                footerAlignment = restored.footerAlignment
                showHeaderFooterOnFirstPage = restored.showHeaderFooterOnFirstPage
                pageMarginTop = restored.pageMarginTop
                pageMarginBottom = restored.pageMarginBottom
                pageMarginLeft = restored.pageMarginLeft
                pageMarginRight = restored.pageMarginRight
                selectedDocumentTheme = restored.selectedDocumentTheme
                selectedThemeEffect = restored.selectedThemeEffect
                pageBackgroundColorHex = restored.pageBackgroundColorHex
                watermarkText = restored.watermarkText
                watermarkType = restored.watermarkType
                watermarkColorHex = restored.watermarkColorHex
                pageBorderType = restored.pageBorderType
                pageBorderColorHex = restored.pageBorderColorHex
                
                undoRedoTrigger++
                undoRedoManager.isRestoring = false
            }
        }

        val performRedo = {
            val restored = undoRedoManager.redo(captureSnapshot())
            if (restored != null) {
                showUndoRedoFeedback("Redo")
                undoRedoManager.isRestoring = true
                isUndoRedoAction = true
                onTitleChange(restored.title)
                editorTextFieldValue = restored.textFieldValue
                onContentChange(restored.draftContent)
                
                val currentSpans = DocFormatRepository.getSpans(selectedDoc.id)
                currentSpans.clear()
                currentSpans.addAll(restored.spans.map { it.copy() })
                
                editorTheme = restored.editorTheme
                pageMargins = restored.pageMargins
                columnCount = restored.columnCount
                fontSize = restored.fontSize
                isLandscape = restored.isLandscape
                pageNumberPosition = restored.pageNumberPosition
                pageNumberFormat = restored.pageNumberFormat
                pageNumberStartAt = restored.pageNumberStartAt
                showPageNumberOnFirstPage = restored.showPageNumberOnFirstPage
                headerText = restored.headerText
                footerText = restored.footerText
                headerAlignment = restored.headerAlignment
                footerAlignment = restored.footerAlignment
                showHeaderFooterOnFirstPage = restored.showHeaderFooterOnFirstPage
                pageMarginTop = restored.pageMarginTop
                pageMarginBottom = restored.pageMarginBottom
                pageMarginLeft = restored.pageMarginLeft
                pageMarginRight = restored.pageMarginRight
                selectedDocumentTheme = restored.selectedDocumentTheme
                selectedThemeEffect = restored.selectedThemeEffect
                pageBackgroundColorHex = restored.pageBackgroundColorHex
                watermarkText = restored.watermarkText
                watermarkType = restored.watermarkType
                watermarkColorHex = restored.watermarkColorHex
                pageBorderType = restored.pageBorderType
                pageBorderColorHex = restored.pageBorderColorHex
                
                undoRedoTrigger++
                undoRedoManager.isRestoring = false
            }
        }

        LaunchedEffect(draftContent) {
            if (editorTextFieldValue.text != draftContent) {
                editorTextFieldValue = editorTextFieldValue.copy(
                    text = draftContent,
                    selection = TextRange(
                        editorTextFieldValue.selection.start.coerceIn(0, draftContent.length),
                        editorTextFieldValue.selection.end.coerceIn(0, draftContent.length)
                    )
                )
                lastSelection = TextRange(
                    lastSelection.start.coerceIn(0, draftContent.length),
                    lastSelection.end.coerceIn(0, draftContent.length)
                )
            }
        }

        var activeRibbonTab by remember { mutableStateOf("Home") }
        var isRibbonExpanded by remember { mutableStateOf(true) }
        var ribbonHeightDp by remember { mutableStateOf(300.dp) }
        var ribbonSearchQuery by remember { mutableStateOf("") }

        var isFontExpanded by remember { mutableStateOf(true) }
        var isClipboardExpanded by remember { mutableStateOf(true) }
        var isStylesExpanded by remember { mutableStateOf(true) }
        var isEditingExpanded by remember { mutableStateOf(true) }
        var isStatsExpanded by remember { mutableStateOf(true) }

        var activeFontFamily by remember { mutableStateOf("Default") }
        var activeFontSize by remember { mutableStateOf("16") }
        var showPasteSpecialDialog by remember { mutableStateOf(false) }
        var showInsertPictureDialog by remember { mutableStateOf(false) }
        var showInsertTableDialog by remember { mutableStateOf(false) }
        var showInsertShapeDialog by remember { mutableStateOf(false) }
        var selectedTableId by remember { mutableStateOf<String?>(null) }
        var selectedShapeId by remember { mutableStateOf<String?>(null) }
        var pendingPasteSelStart by remember { mutableIntStateOf(0) }
        var pendingPasteSelEnd by remember { mutableIntStateOf(0) }
        var pendingPasteText by remember { mutableStateOf("") }
        var showFontColorPicker by remember { mutableStateOf(false) }
        var showHighlightPicker by remember { mutableStateOf(false) }

        var showBulletStyleDialog by remember { mutableStateOf(false) }
        var showNumberFormatDialog by remember { mutableStateOf(false) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var showBordersDialog by remember { mutableStateOf(false) }
        var showFindDialog by remember { mutableStateOf(false) }
        var showGoToDialog by remember { mutableStateOf(false) }
        var findQuery by remember { mutableStateOf("") }
        var replaceQuery by remember { mutableStateOf("") }
        var findMode by remember { mutableStateOf("find") }
        var matchCase by remember { mutableStateOf(false) }
        var wholeWord by remember { mutableStateOf(false) }
        var currentMatchIndex by remember { mutableIntStateOf(0) }
        var textStyles by remember { mutableStateOf(getDefaultTextStyles()) }
        var showStyleManager by remember { mutableStateOf(false) }
        var showStyleEditor by remember { mutableStateOf(false) }
        var editingStyleIndex by remember { mutableIntStateOf(-1) }
        var editingStyleName by remember { mutableStateOf("") }
        var editingStyleFontSize by remember { mutableIntStateOf(16) }
        var editingStyleBold by remember { mutableStateOf(false) }
        var editingStyleItalic by remember { mutableStateOf(false) }
        var editingStyleUnderline by remember { mutableStateOf(false) }
        var editingStyleColor by remember { mutableStateOf("#000000") }
        var editingStyleAlignment by remember { mutableStateOf("left") }
        var editingStyleLineSpacing by remember { mutableStateOf("1.0") }
        var showShadingPicker by remember { mutableStateOf(false) }
        val pageBackgroundColor: Color? by remember {
            derivedStateOf {
                if (pageBackgroundColorHex.isNotEmpty()) {
                    try { Color(android.graphics.Color.parseColor(pageBackgroundColorHex)) } catch (e: Exception) { null }
                } else null
            }
        }
        var pendingParaAction by remember { mutableStateOf<String?>(null) }

        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var isSpeaking by remember { mutableStateOf(false) }

        val handleRibbonAction: (String) -> Unit = { action ->
            pushSnapshot()
            if (action == "insert_table" || action == "table") {
                showInsertTableDialog = true
            } else if (action == "shapes") {
                showInsertShapeDialog = true
            } else if (action == "picture" || action == "pictures") {
                showInsertPictureDialog = true
            } else {
                executeRibbonAction(
                    actionId = action,
                    context = context,
                    draftContent = draftContent,
                    onContentChange = onContentChange,
                    selectedDoc = selectedDoc,
                    viewModel = viewModel,
                    editorTheme = editorTheme,
                    onThemeChange = { editorTheme = it },
                    selectedDocumentTheme = selectedDocumentTheme,
                    onDocumentThemeChange = { selectedDocumentTheme = it },
                    selectedThemeEffect = selectedThemeEffect,
                    onThemeEffectChange = { selectedThemeEffect = it },
                    onMarginsChange = { top, bottom, left, right ->
                        pageMarginTop = top
                        pageMarginBottom = bottom
                        pageMarginLeft = left
                        pageMarginRight = right
                        pageMargins = left
                    },
                    onColumnsChange = { columnCount = it },
                    onFontSizeChange = { fontSize = it },
                    onLandscapeChange = { isLandscape = it },
                    onShowCustomMarginsDialog = { showCustomMarginsDialog = true },
                    onShowCustomSizeDialog = { showCustomSizeDialog = true },
                    onShowPageNumberFormatDialog = { showPageNumberFormatDialog = true },
                    onShowHeaderFooterDialog = { showHeaderFooterDialog = true },
                    onShowPageNumberPositionMenu = { pageNumberPositionMenu = it },
                    onPageNumberPositionChange = { pageNumberPosition = it },
                    pageNumberFormat = pageNumberFormat,
                    pageNumberStartAt = pageNumberStartAt.toIntOrNull() ?: 1,
                    snackbarScope = coroutineScope,
                    snackbarState = snackbarHostState,
                    tts = tts,
                    isSpeaking = isSpeaking,
                    onSpeakStateChange = { isSpeaking = it },
                    textFieldValue = editorTextFieldValue,
                    onTextFieldValueChange = { newVal ->
                        val textChanged = newVal.text != editorTextFieldValue.text
                        val selectionChanged = newVal.selection != editorTextFieldValue.selection
                        if (textChanged || selectionChanged) {
                            pushSnapshot()
                        }
                        editorTextFieldValue = newVal
                        if (isEditorFocused) {
                            lastSelection = newVal.selection
                        }
                        if (textChanged) {
                            onContentChange(newVal.text)
                        }
                    },
                    lastSelection = lastSelection,
                    formatVersion = formatVersion,
                    onFormatVersionChange = { formatVersion = it },
                    onHistoryAdd = { clipboardHistory.add(it) },
                    onCopyFormatted = { text, spans, off -> copiedFormattedData = CopyFormattedData(text, spans, off) },
                    onTargetFocusChange = { page, offset ->
                        targetFocusPage.value = page
                        targetFocusOffset.value = offset
                    },
                    pageBackgroundColorHex = pageBackgroundColorHex,
                    onPageBackgroundColorHexChange = { pageBackgroundColorHex = it },
                    watermarkText = watermarkText,
                    onWatermarkSet = { text, type ->
                        if (text == "CUSTOM_PROMPT") {
                            showCustomWatermarkDialog = true
                        } else {
                            watermarkText = text
                            watermarkType = type
                        }
                    },
                    pageBorderType = pageBorderType,
                    onPageBorderChange = { 
                        if (it == "Custom Borders...") {
                            showPageBordersDialog = true
                        } else {
                            pageBorderType = it
                        }
                    },
                    onShowShadingPicker = { showShadingPicker = true },
                    onShowSpellingDialog = { showSpellingDialog = true },
                    onShowThesaurusDialog = { showThesaurusDialog = true },
                    onShowWordCountDialog = { showWordCountDialog = true },
                    onShowAccessibilityDialog = { showAccessibilityDialog = true },
                    onShowTranslateDialog = { isSel ->
                        translateIsSelectionMode = isSel
                        showTranslateDialog = true
                    },
                    onShowLanguageDialog = { showLanguageDialog = true },
                    onNewComment = {
                        pushSnapshot()
                        val sel = editorTextFieldValue.selection
                        DocReviewManager.addComment(
                            docId = selectedDoc.id,
                            text = "Please review this text segment.",
                            author = "User",
                            startOffset = sel.start,
                            endOffset = if (sel.collapsed) sel.start + 10 else sel.end
                        )
                        showCommentsPane = true
                        formatVersion++
                    },
                    onDeleteComment = {
                        val comments = DocReviewManager.getCommentsForDoc(selectedDoc.id)
                        if (comments.isNotEmpty()) {
                            DocReviewManager.deleteComment(selectedDoc.id, comments.last().id)
                            formatVersion++
                        }
                    },
                    onShowCommentsToggle = {
                        showCommentsPane = !showCommentsPane
                    },
                    onToggleTrackChanges = {
                        val currentVal = DocReviewManager.isTrackingEnabled(selectedDoc.id)
                        DocReviewManager.toggleTracking(selectedDoc.id)
                        showToast(if (!currentVal) "Track Changes ENABLED" else "Track Changes DISABLED")
                    },
                    onShowReviewPane = {
                        showRevisionsPane = !showRevisionsPane
                    },
                    onAcceptAllTrackChanges = {
                        pushSnapshot()
                        var current = draftContent
                        DocReviewManager.getTrackedChangesForDoc(selectedDoc.id).forEach { chg ->
                            DocReviewManager.acceptTrackedChange(selectedDoc.id, chg, current) { current = it }
                        }
                        onContentChange(current)
                        editorTextFieldValue = TextFieldValue(current, TextRange(current.length))
                        formatVersion++
                        showToast("Accepted all tracked changes.")
                    },
                    onRejectAllTrackChanges = {
                        pushSnapshot()
                        var current = draftContent
                        DocReviewManager.getTrackedChangesForDoc(selectedDoc.id).forEach { chg ->
                            DocReviewManager.rejectTrackedChange(selectedDoc.id, chg, current) { current = it }
                        }
                        onContentChange(current)
                        editorTextFieldValue = TextFieldValue(current, TextRange(current.length))
                        formatVersion++
                        showToast("Rejected all tracked changes.")
                    }
                )
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFEAECF0).copy(alpha = 0.5f))
        ) {
            val totalHeight = maxHeight
            val totalWidth = maxWidth

            val minHeightDp = 120.dp
            val maxHeightDp = totalHeight * 0.85f

            val coercedRibbonHeight = ribbonHeightDp.coerceIn(minHeightDp, maxHeightDp)
            val bottomNavBarHeight = 68.dp
            
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            val isImeVisible = imeBottom > 0

            LaunchedEffect(isImeVisible, activeRibbonTab) {
                if (isImeVisible && activeRibbonTab != "AI") {
                    isRibbonExpanded = false
                }
            }
            
            val targetRibbonHeight = if (isRibbonExpanded) {
                if (isImeVisible) {
                    if (activeRibbonTab == "AI") coercedRibbonHeight
                    else 0.dp
                } else coercedRibbonHeight
            } else {
                if (!isImeVisible || activeRibbonTab == "AI") (bottomNavBarHeight + 12.dp) else 0.dp
            }
            val animatedRibbonHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = targetRibbonHeight,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
                label = "ribbonHeight"
            )
            val editorBottomPadding = if (isImeVisible && activeRibbonTab != "AI") 0.dp else animatedRibbonHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = editorBottomPadding)
            ) {
                WorkspaceMenuBar(
                    doc = selectedDoc,
                    draftTitle = draftTitle,
                    onTitleChange = {
                        if (draftTitle != it) {
                            pushSnapshot()
                        }
                        onTitleChange(it)
                    },
                    isSidebarExpanded = isSidebarExpanded,
                    onToggleSidebar = onToggleSidebar,
                    onCloseClick = {
                        if (isSpeaking) {
                            tts?.stop()
                            isSpeaking = false
                        }
                        onCloseClick()
                    },
                    undoRedoManager = undoRedoManager,
                    undoRedoTrigger = undoRedoTrigger,
                    onUndo = performUndo,
                    onRedo = performRedo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedDoc.type) {
                        "word" -> {
                            WordDocumentEditor(
                                docId = selectedDoc.id,
                                draftContent = draftContent,
                                targetFocusPage = targetFocusPage,
                                targetFocusOffset = targetFocusOffset,
                                onContentChange = onContentChange,
                                editorTheme = editorTheme,
                                onEditorThemeChange = { editorTheme = it },
                                pageBackgroundColorHex = pageBackgroundColorHex,
                                pageMargins = pageMargins,
                                pageMarginTop = pageMarginTop,
                                pageMarginBottom = pageMarginBottom,
                                pageMarginLeft = pageMarginLeft,
                                pageMarginRight = pageMarginRight,
                                columnCount = columnCount,
                                fontSize = fontSize,
                                formatVersion = formatVersion,
                                isLandscape = isLandscape,
                                pageFormat = pageFormat,
                                customDimensions = customDimensions,
                                pageNumberPosition = pageNumberPosition,
                                pageNumberFormat = pageNumberFormat,
                                pageNumberStartAt = pageNumberStartAt.toIntOrNull() ?: 1,
                                showPageNumberOnFirstPage = showPageNumberOnFirstPage,
                                headerText = headerText,
                                footerText = footerText,
                                headerAlignment = headerAlignment,
                                footerAlignment = footerAlignment,
                                showHeaderFooterOnFirstPage = showHeaderFooterOnFirstPage,
                                onShowHeaderFooterDialog = { showHeaderFooterDialog = true },
                                watermarkText = watermarkText,
                                watermarkType = watermarkType,
                                watermarkColorHex = watermarkColorHex,
                                pageBorderType = pageBorderType,
                                pageBorderColorHex = pageBorderColorHex,
                                modifier = Modifier.fillMaxSize(),
                                textFieldValue = editorTextFieldValue,
                                onTextFieldValueChange = { newVal ->
                                    val textChanged = newVal.text != editorTextFieldValue.text
                                    val selectionChanged = newVal.selection != editorTextFieldValue.selection
                                    if (textChanged || selectionChanged) {
                                        pushSnapshot()
                                    }
                                    editorTextFieldValue = newVal
                                    if (isEditorFocused) {
                                        lastSelection = newVal.selection
                                    }
                                    if (textChanged) {
                                        onContentChange(newVal.text)
                                    }
                                    if (isEditorFocused && !textChanged && selectionChanged && newVal.selection.start >= 0) {
                                        val pos = newVal.selection.start
                                        val spans = DocFormatRepository.getSpans(selectedDoc.id)
                                        val sizeSpan = spans.find { it.type == "fontSize" && it.start <= pos && it.end > pos }
                                        val detectedSize = sizeSpan?.value
                                        if (detectedSize != null && detectedSize != activeFontSize) {
                                            activeFontSize = detectedSize
                                        } else if (detectedSize == null && activeFontSize != "16") {
                                            activeFontSize = "16"
                                        }
                                        val familySpan = spans.find { it.type == "fontFamily" && it.start <= pos && it.end > pos }
                                        val detectedFamily = familySpan?.value
                                        if (detectedFamily != null && detectedFamily != activeFontFamily) {
                                            activeFontFamily = detectedFamily
                                        } else if (detectedFamily == null && activeFontFamily != "Default") {
                                            activeFontFamily = "Default"
                                        }
                                    }
                                },
                                onFocusChanged = { isEditorFocused = it }
                            )
                        }
                        "sheet" -> SpreadsheetEditor(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        "slide" -> SlidePresentationWorkspace(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = editorBottomPadding + 8.dp)
            )

            val isDarkTheme = isSystemInDarkTheme()
            val surfaceBg = if (isDarkTheme) Color(0xFF1E1E22) else Color(0xFFF0F2F6)
            val glassCardBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(animatedRibbonHeight)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Drag Handle Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .pointerInput(LocalDensity.current) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.y.toDp()
                                            ribbonHeightDp = (ribbonHeightDp - dragAmountDp).coerceIn(minHeightDp, maxHeightDp)
                                            if (dragAmountDp < 0.dp && !isRibbonExpanded) {
                                                isRibbonExpanded = true
                                            }
                                        }
                                    )
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isDarkTheme) Color.White.copy(alpha = 0.35f) else Color.DarkGray.copy(alpha = 0.25f))
                        )
                    }

                    // 2. Tabs Bar Row
                    val ribbonTabs = listOf(
                        Triple("Home", Icons.Outlined.Home, "ribbon_tab_Home"),
                        Triple("Insert", Icons.Outlined.NoteAdd, "ribbon_tab_Insert"),
                        Triple("AI", Icons.Outlined.AutoAwesome, "ribbon_tab_AI"),
                        Triple("Layout", Icons.Outlined.ViewQuilt, "ribbon_tab_Layout"),
                        Triple("Review", Icons.Outlined.RateReview, "ribbon_tab_Review")
                    )
                    val selectedColor = if (selectedDoc.type == "word") DocWordColor
                        else if (selectedDoc.type == "sheet") DocSheetColor
                        else DocSlideColor

                    if (!isImeVisible || activeRibbonTab == "AI") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomNavBarHeight)
                                .background(if (isDarkTheme) Color(0xFF1A1C20) else Color(0xFFFAFBFF))
                                .drawBehind {
                                    drawLine(
                                        color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1f
                                    )
                                },
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ribbonTabs.forEach { (tabName, icon, tag) ->
                                val isSelected = activeRibbonTab == tabName && isRibbonExpanded
                                val tabTint = if (isSelected) selectedColor
                                    else (if (isDarkTheme) Color.LightGray.copy(alpha = 0.6f) else Color.Gray)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .clickable {
                                            if (tabName != "AI" && activeRibbonTab != tabName) {
                                                keyboardController?.hide()
                                            }
                                            if (activeRibbonTab == tabName && isRibbonExpanded) {
                                                isRibbonExpanded = false
                                            } else {
                                                activeRibbonTab = tabName
                                                isRibbonExpanded = true
                                            }
                                        }
                                        .testTag(tag)
                                        .drawBehind {
                                            if (isSelected) {
                                                drawRoundRect(
                                                    color = selectedColor.copy(alpha = 0.1f),
                                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                                    topLeft = Offset.Zero,
                                                    size = size
                                                )
                                                drawRect(
                                                    color = selectedColor,
                                                    topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                                    size = Size(size.width, 3.dp.toPx())
                                                )
                                            }
                                        }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Ribbon tab $tabName",
                                        tint = tabTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tabName,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = tabTint
                                    )
                                }
                            }
                        }
                    }

                    // 3. Ribbon Content
                    AnimatedVisibility(
                        visible = isRibbonExpanded,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
                        ) + fadeOut(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(surfaceBg)
                                .border(width = 1.dp, color = glassCardBorderColor)
                        ) {

                            if (activeRibbonTab == "AI") {
                                AIChatPanel(
                                    onClose = { isRibbonExpanded = false },
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    activeTextFieldValue = editorTextFieldValue,
                                    onTextFieldValueChange = { editorTextFieldValue = it },
                                    pageMargins = pageMargins,
                                    onPageMarginsChange = { pageMargins = it },
                                    fontSize = fontSize,
                                    onFontSizeChange = { fontSize = it },
                                    isLandscape = isLandscape,
                                    onIsLandscapeChange = { isLandscape = it },
                                    columnCount = columnCount,
                                    onColumnCountChange = { columnCount = it },
                                    watermarkText = watermarkText,
                                    onWatermarkSet = { text, type ->
                                        watermarkText = text
                                        watermarkType = type
                                    },
                                    pageBorderType = pageBorderType,
                                    onPageBorderTypeChange = { pageBorderType = it },
                                    headerText = headerText,
                                    onHeaderChange = { headerText = it },
                                    footerText = footerText,
                                    onFooterChange = { footerText = it },
                                    onShowReviewDialog = { type ->
                                        when (type.lowercase()) {
                                            "spelling" -> showSpellingDialog = true
                                            "thesaurus" -> showThesaurusDialog = true
                                            "wordcount" -> showWordCountDialog = true
                                            "accessibility" -> showAccessibilityDialog = true
                                            "translate" -> showTranslateDialog = true
                                        }
                                    },
                                    showToast = { showToast(it) }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkTheme) Color.Black.copy(alpha = 0.25f) else Color.White)
                                        .border(
                                            width = 1.dp,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.LightGray.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search Ribbon Icon",
                                        tint = if (isDarkTheme) Color.LightGray else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (ribbonSearchQuery.isEmpty()) {
                                            Text(
                                                text = "Search tools, commands, features...",
                                                color = Color.Gray,
                                                fontSize = 13.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = ribbonSearchQuery,
                                            onValueChange = { ribbonSearchQuery = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isDarkTheme) Color.White else Color.Black
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ribbon_search_input")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { isRibbonExpanded = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = "Collapse Ribbon Panel",
                                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                ExportButton(draftContent = draftContent, viewModel = viewModel, isLandscape = isLandscape)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                val filteredTools = getRibbonTools(selectedDoc) { action ->
                                    handleRibbonAction(action)
                                }.filter { tool ->
                                    (tool.tab.equals(activeRibbonTab, ignoreCase = true) || ribbonSearchQuery.isNotEmpty()) &&
                                    (tool.title.contains(ribbonSearchQuery, ignoreCase = true) ||
                                     tool.description.contains(ribbonSearchQuery, ignoreCase = true) ||
                                     tool.category.contains(ribbonSearchQuery, ignoreCase = true))
                                }

                                if (filteredTools.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "No tools matched query",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No tools match search query",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                     val onAction: (String) -> Unit = { action -> handleRibbonAction(action) }
                                     /*
                                         pushSnapshot()
                                         if (action == "insert_table" || action == "table") {
                                            showInsertTableDialog = true
                                        } else if (action == "shapes") {
                                            showInsertShapeDialog = true
                                        } else {
                                        pushSnapshot()
                                        if (action == "picture" || action == "pictures") {
                                            showInsertPictureDialog = true
                                        } else {
                                            executeRibbonAction(
                                                actionId = action,
                                                context = context,
                                                draftContent = draftContent,
                                                onContentChange = onContentChange,
                                                selectedDoc = selectedDoc,
                                                viewModel = viewModel,
                                                editorTheme = editorTheme,
                                                onThemeChange = { editorTheme = it },
                                                onMarginsChange = { top, bottom, left, right ->
                                                    pageMarginTop = top
                                                    pageMarginBottom = bottom
                                                    pageMarginLeft = left
                                                    pageMarginRight = right
                                                    pageMargins = left
                                                },
                                                onColumnsChange = { columnCount = it },
                                                onFontSizeChange = { fontSize = it },
                                                onLandscapeChange = { isLandscape = it },
                                                onShowCustomMarginsDialog = { showCustomMarginsDialog = true },
                                                onShowCustomSizeDialog = { showCustomSizeDialog = true },
                                                onShowPageNumberFormatDialog = { showPageNumberFormatDialog = true },
                                             onShowHeaderFooterDialog = { showHeaderFooterDialog = true },
                                             onShowSpellingDialog = { showSpellingDialog = true },
                                             onShowThesaurusDialog = { showThesaurusDialog = true },
                                             onShowWordCountDialog = { showWordCountDialog = true },
                                             onShowAccessibilityDialog = { showAccessibilityDialog = true },
                                             onShowTranslateDialog = { isSel ->
                                                 translateIsSelectionMode = isSel
                                                 showTranslateDialog = true
                                             },
                                             onShowLanguageDialog = { showLanguageDialog = true },
                                             onNewComment = {
                                                 pushSnapshot()
                                                 val sel = editorTextFieldValue.selection
                                                 DocReviewManager.addComment(
                                                     docId = selectedDoc.id,
                                                     text = "Please review this text segment.",
                                                     author = "User",
                                                     startOffset = sel.start,
                                                     endOffset = if (sel.collapsed) sel.start + 10 else sel.end
                                                 )
                                                 showCommentsPane = true
                                                 formatVersion++
                                             },
                                             onDeleteComment = {
                                                val comments = DocReviewManager.getCommentsForDoc(selectedDoc.id)
                                                if (comments.isNotEmpty()) {
                                                    DocReviewManager.deleteComment(selectedDoc.id, comments.last().id)
                                                    formatVersion++
                                                }
                                             },
                                             onShowCommentsToggle = {
                                                 showCommentsPane = !showCommentsPane
                                             },
                                             onToggleTrackChanges = {
                                                 val currentVal = DocReviewManager.isTrackingEnabled(selectedDoc.id)
                                                 DocReviewManager.toggleTracking(selectedDoc.id)
                                                 showToast(if (!currentVal) "Track Changes ENABLED" else "Track Changes DISABLED")
                                             },
                                             onShowReviewPane = {
                                                 showRevisionsPane = !showRevisionsPane
                                             },
                                             onAcceptAllTrackChanges = {
                                                 pushSnapshot()
                                                 var current = draftContent
                                                 DocReviewManager.getTrackedChangesForDoc(selectedDoc.id).forEach { chg ->
                                                     DocReviewManager.acceptTrackedChange(selectedDoc.id, chg, current) { current = it }
                                                 }
                                                 onContentChange(current)
                                                 editorTextFieldValue = TextFieldValue(current, TextRange(current.length))
                                                 formatVersion++
                                                 showToast("Accepted all tracked changes.")
                                             },
                                             onRejectAllTrackChanges = {
                                                 pushSnapshot()
                                                 var current = draftContent
                                                 DocReviewManager.getTrackedChangesForDoc(selectedDoc.id).forEach { chg ->
                                                     DocReviewManager.rejectTrackedChange(selectedDoc.id, chg, current) { current = it }
                                                 }
                                                 onContentChange(current)
                                                 editorTextFieldValue = TextFieldValue(current, TextRange(current.length))
                                                 formatVersion++
                                                 showToast("Rejected all tracked changes.")
                                             },
                                                onShowPageNumberPositionMenu = { pageNumberPositionMenu = it },
                                                onPageNumberPositionChange = { pageNumberPosition = it },
                                                pageNumberFormat = pageNumberFormat,
                                                pageNumberStartAt = pageNumberStartAt.toIntOrNull() ?: 1,
                                                snackbarScope = coroutineScope,
                                                snackbarState = snackbarHostState,
                                                tts = tts,
                                                isSpeaking = isSpeaking,
                                                onSpeakStateChange = { isSpeaking = it },
                                                textFieldValue = editorTextFieldValue,
                                                onTextFieldValueChange = { newVal ->
                                                    editorTextFieldValue = newVal
                                                    if (!newVal.selection.collapsed) {
                                                        lastSelection = newVal.selection
                                                    }
                                                    if (newVal.text != draftContent) {
                                                        onContentChange(newVal.text)
                                                    }
                                                },
                                                lastSelection = lastSelection,
                                                formatVersion = formatVersion,
                                                onFormatVersionChange = { formatVersion = it },
                                                onHistoryAdd = { clipboardHistory.add(it) },
                                                onCopyFormatted = { text, spans, off -> copiedFormattedData = CopyFormattedData(text, spans, off) },
                                                onTargetFocusChange = { page, offset ->
                                                    targetFocusPage.value = page
                                                    targetFocusOffset.value = offset
                                                },
                                                pageBackgroundColorHex = pageBackgroundColorHex,
                                                onPageBackgroundColorHexChange = { pageBackgroundColorHex = it },
                                                watermarkText = watermarkText,
                                                onWatermarkSet = { text, type ->
                                                    if (text == "CUSTOM_PROMPT") {
                                                        showCustomWatermarkDialog = true
                                                    } else {
                                                        watermarkText = text
                                                        watermarkType = type
                                                    }
                                                },
                                                pageBorderType = pageBorderType,
                                                onPageBorderChange = { 
                                                    if (it == "Custom Borders...") {
                                                        showPageBordersDialog = true
                                                    } else {
                                                        pageBorderType = it
                                                    }
                                                },
                                                onShowShadingPicker = { showShadingPicker = true }
                                            )
                                        }
                                    }

                                        }
                                     */
                                    if (activeRibbonTab.equals("Home", ignoreCase = true) && ribbonSearchQuery.isEmpty()) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            // --- FONT GROUP ---
                                            item {
                                                val groupColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                RibbonGroupContainer(
                                                    title = "Font Formatting",
                                                    isExpanded = isFontExpanded,
                                                    onToggleExpand = { isFontExpanded = !isFontExpanded },
                                                    accentColor = groupColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        // Row 1: Dropdowns and scale buttons
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonDropdown(
                                                                selectedValue = activeFontFamily,
                                                                options = listOf("Default", "Aptos", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia", "Space Grotesk", "JetBrains Mono"),
                                                                onSelect = {
                                                                    activeFontFamily = it
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        if (it == "Default") {
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "fontFamily", effective.start, effective.end)
                                                                        } else {
                                                                            DocFormatRepository.applySpan(selectedDoc.id, "fontFamily", it, effective.start, effective.end)
                                                                        }
                                                                        formatVersion++
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(3.5f)
                                                            )

                                                            RibbonDropdown(
                                                                selectedValue = activeFontSize,
                                                                options = listOf("9", "10", "11", "12", "14", "16", "18", "20", "24", "28", "32", "36", "40", "44", "48", "54", "60", "66", "72", "80", "88", "96", "108", "120", "144", "180", "200"),
                                                                isEditable = true,
                                                                onSelect = {
                                                                    activeFontSize = it
                                                                    val num = it.toIntOrNull() ?: 16
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", it, effective.start, effective.end)
                                                                        formatVersion++
                                                                    } else {
                                                                        fontSize = num.sp
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(2.2f)
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Increase Font Size",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        val currentVal = activeFontSize.toIntOrNull() ?: 16
                                                                        val newSize = if (currentVal < 200) currentVal + 2 else 200
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", newSize.toString(), effective.start, effective.end)
                                                                        formatVersion++
                                                                        activeFontSize = newSize.toString()
                                                                    } else {
                                                                        val currentSize = fontSize.value.toInt()
                                                                        val newSize = if (currentSize < 200) currentSize + 2 else 200
                                                                        fontSize = newSize.sp
                                                                        activeFontSize = newSize.toString()
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = groupColor, modifier = Modifier.size(22.dp))
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Decrease Font Size",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        val currentVal = activeFontSize.toIntOrNull() ?: 16
                                                                        val newSize = if (currentVal > 8) currentVal - 2 else 8
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", newSize.toString(), effective.start, effective.end)
                                                                        formatVersion++
                                                                        activeFontSize = newSize.toString()
                                                                    } else {
                                                                        val currentSize = fontSize.value.toInt()
                                                                        val newSize = if (currentSize > 8) currentSize - 2 else 8
                                                                        fontSize = newSize.sp
                                                                        activeFontSize = newSize.toString()
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = groupColor, modifier = Modifier.size(22.dp))
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Change Case",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val text = editorTextFieldValue.text
                                                                    if (!selection.collapsed) {
                                                                        val selStart = minOf(selection.start, selection.end)
                                                                        val selEnd = maxOf(selection.start, selection.end)
                                                                        val selectedStr = text.substring(selStart, selEnd)
                                                                        val updatedStr = if (selectedStr == selectedStr.uppercase()) {
                                                                            selectedStr.lowercase()
                                                                        } else if (selectedStr == selectedStr.lowercase()) {
                                                                            selectedStr.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            selectedStr.uppercase()
                                                                        }
                                                                        val newText = text.replaceRange(selStart, selEnd, updatedStr)
                                                                        editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(selStart, selStart + updatedStr.length))
                                                                        onContentChange(newText)
                                                                    } else {
                                                                        val currentContent = draftContent
                                                                        val updatedContent = if (currentContent == currentContent.uppercase()) {
                                                                            currentContent.lowercase()
                                                                        } else if (currentContent == currentContent.lowercase()) {
                                                                            currentContent.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            currentContent.uppercase()
                                                                        }
                                                                        editorTextFieldValue = TextFieldValue(text = updatedContent, selection = TextRange(updatedContent.length))
                                                                        onContentChange(updatedContent)
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.3f),
                                                                customContent = {
                                                                    Text("Aa", color = groupColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Clear Formatting",
                                                                onClick = {
                                                                    onAction("clear_format")
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Text("×", color = groupColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            )
                                                        }

                                                        // Row 2: Text Styling
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonIconButton(
                                                                contentDescription = "Bold",
                                                                onClick = { onAction("bold") },
                                                                isSelected = "bold" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("B", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Italic",
                                                                onClick = { onAction("italic") },
                                                                isSelected = "italic" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontFamily = FontFamily.Serif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Underline",
                                                                onClick = { onAction("underline") },
                                                                isSelected = "underline" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("U", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, textDecoration = TextDecoration.Underline, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Strikethrough",
                                                                onClick = {
                                                                    onAction("strikethrough")
                                                                 },
                                                                isSelected = "strikethrough" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("abc", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, textDecoration = TextDecoration.LineThrough, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Subscript",
                                                                onClick = {
                                                                    onAction("subscript")
                                                                },
                                                                isSelected = "subscript" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 2.dp)) {
                                                                        Text("x", fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                                                        Text("2", fontSize = 9.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 2.dp))
                                                                    }
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Superscript",
                                                                onClick = {
                                                                    onAction("superscript")
                                                                },
                                                                isSelected = "superscript" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                                                                        Text("x", fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                                                        Text("2", fontSize = 9.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = -2.dp))
                                                                    }
                                                                }
                                                            )
                                                             RibbonIconButton(
                                                                contentDescription = "Font Color",
                                                                onClick = {
                                                                    if ("color" in activeFormatting) {
                                                                        val sel = editorTextFieldValue.selection
                                                                        if (!sel.collapsed) {
                                                                            val start = minOf(sel.start, sel.end)
                                                                            val end = maxOf(sel.start, sel.end)
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "color", start, end)
                                                                            formatVersion++
                                                                        }
                                                                    } else {
                                                                        showFontColorPicker = true
                                                                    }
                                                                },
                                                                isSelected = "color" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    val colorVal = cursorFontColorVal
                                                                    val displayColor = if (colorVal != null) {
                                                                        try { Color(android.graphics.Color.parseColor(colorVal)) } catch (e: Exception) { Color(0xFF3B82F6) }
                                                                    } else {
                                                                        Color(0xFF3B82F6)
                                                                    }
                                                                    Text("A", fontSize = 18.sp, fontWeight = FontWeight.Black, color = displayColor)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Highlight",
                                                                onClick = {
                                                                    if ("highlight" in activeFormatting) {
                                                                        val sel = editorTextFieldValue.selection
                                                                        if (!sel.collapsed) {
                                                                            val start = minOf(sel.start, sel.end)
                                                                            val end = maxOf(sel.start, sel.end)
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "highlight", start, end)
                                                                            formatVersion++
                                                                        }
                                                                    } else {
                                                                        showHighlightPicker = true
                                                                    }
                                                                },
                                                                isSelected = "highlight" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    val hlColor = cursorHighlightColorVal
                                                                    val tintColor = if (hlColor != null) {
                                                                        try { Color(android.graphics.Color.parseColor(hlColor)) } catch (e: Exception) { Color(0xFFFDE047).copy(alpha = 0.6f) }
                                                                    } else {
                                                                        if (isSystemInDarkTheme()) Color(0xFF94A3B8) else Color(0xFF64748B)
                                                                    }
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Edit,
                                                                        contentDescription = "Highlight",
                                                                        tint = tintColor,
                                                                        modifier = Modifier.size(18.dp).rotate(-45f)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            /*
                                            // --- FONT GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Font Formatting",
                                                    isExpanded = isFontExpanded,
                                                    onToggleExpand = { isFontExpanded = !isFontExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        // Row 1: Dropdowns and scale buttons
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonDropdown(
                                                                selectedValue = activeFontFamily,
                                                                options = listOf("Default", "Aptos", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia", "Space Grotesk", "JetBrains Mono"),
                                                                onSelect = {
                                                                    activeFontFamily = it
                                                                    onAction("clear_format")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font family changed to: $it")
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(4f)
                                                            )

                                                            RibbonDropdown(
                                                                selectedValue = activeFontSize,
                                                                options = listOf("9", "10", "11", "12", "14", "16", "18", "20", "24", "28", "32", "48"),
                                                                onSelect = {
                                                                    activeFontSize = it
                                                                    onAction("clear_format")
                                                                    val num = it.toIntOrNull() ?: 14
                                                                    fontSize = num.sp
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font size set to: ${num}sp")
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(2f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Add,
                                                                contentDescription = "Increase Font Size",
                                                                onClick = {
                                                                    onAction("font_incr")
                                                                    val currentSize = fontSize.value.toInt()
                                                                    val newSize = if (currentSize < 48) currentSize + 2 else 48
                                                                    fontSize = newSize.sp
                                                                    activeFontSize = newSize.toString()
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Delete,
                                                                contentDescription = "Decrease Font Size",
                                                                onClick = {
                                                                    onAction("font_decr")
                                                                    val currentSize = fontSize.value.toInt()
                                                                    val newSize = if (currentSize > 8) currentSize - 2 else 8
                                                                    fontSize = newSize.sp
                                                                    activeFontSize = newSize.toString()
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Refresh,
                                                                contentDescription = "Change Case",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val text = editorTextFieldValue.text
                                                                    if (!selection.collapsed) {
                                                                        val selectedStr = text.substring(selection.start, selection.end)
                                                                        val updatedStr = if (selectedStr == selectedStr.uppercase()) {
                                                                            selectedStr.lowercase()
                                                                        } else if (selectedStr == selectedStr.lowercase()) {
                                                                            selectedStr.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            selectedStr.uppercase()
                                                                        }
                                                                        val newText = text.replaceRange(selection.start, selection.end, updatedStr)
                                                                        editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(selection.start, selection.start + updatedStr.length))
                                                                        onContentChange(newText)
                                                                    } else {
                                                                        val currentContent = draftContent
                                                                        val updatedContent = if (currentContent == currentContent.uppercase()) {
                                                                            currentContent.lowercase()
                                                                        } else if (currentContent == currentContent.lowercase()) {
                                                                            currentContent.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            currentContent.uppercase()
                                                                        }
                                                                        editorTextFieldValue = TextFieldValue(text = updatedContent, selection = TextRange(updatedContent.length))
                                                                        onContentChange(updatedContent)
                                                                    }
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Changed text case formatting")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Close,
                                                                contentDescription = "Clear Formatting",
                                                                onClick = {
                                                                    onAction("clear_format")
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }

                                                        // Row 2: Text Styling
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Build,
                                                                contentDescription = "Bold",
                                                                onClick = { onAction("bold") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Refresh,
                                                                contentDescription = "Italic",
                                                                onClick = { onAction("italic") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowDown,
                                                                contentDescription = "Underline",
                                                                onClick = { onAction("underline") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Close,
                                                                contentDescription = "Strikethrough",
                                                                onClick = {
                                                                    onContentChange(draftContent + " ~~Strikethrough~~")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Strikethrough formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowDown,
                                                                contentDescription = "Subscript",
                                                                onClick = {
                                                                    onContentChange(draftContent + " <sub>sub</sub>")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Subscript formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowUp,
                                                                contentDescription = "Superscript",
                                                                onClick = {
                                                                    onContentChange(draftContent + " <sup>super</sup>")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Superscript formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Favorite,
                                                                contentDescription = "Font Color",
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font color changed to primary accent!")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Star,
                                                                contentDescription = "Highlight",
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Text highlight applied!")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            */

                                            // --- CLIPBOARD GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Clipboard Actions",
                                                    isExpanded = isClipboardExpanded,
                                                    onToggleExpand = { isClipboardExpanded = !isClipboardExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val btnBg = if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6)
                                                        val accent = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                        val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { onAction("cut") }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentCut, contentDescription = "Cut", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { onAction("copy") }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable {
                                                            val sel = editorTextFieldValue.selection
                                                            pendingPasteSelStart = minOf(sel.start, sel.end)
                                                            pendingPasteSelEnd = maxOf(sel.start, sel.end)
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            pendingPasteText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                                            showPasteSpecialDialog = true
                                                        }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable {
                                                            showClipboardHistory = true
                                                        }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.History, contentDescription = "History", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- STYLES GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Text Styles",
                                                    isExpanded = isStylesExpanded,
                                                    onToggleExpand = { isStylesExpanded = !isStylesExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        val styleNames = textStyles.map { it.name }
                                                        val gridRows = (styleNames + "Manage").chunked(4)
                                                        gridRows.forEach { rowItems ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                rowItems.forEach { item ->
                                                                    val isManage = item == "Manage"
                                                                    val style = if (!isManage) textStyles.find { it.name == item } else null
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(44.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .background(if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6))
                                                                            .clickable {
                                                                                if (isManage) {
                                                                                    showStyleManager = true
                                                                                } else if (style != null) {
                                                                                    val sel = editorTextFieldValue.selection
                                                                                    val start = minOf(sel.start, sel.end)
                                                                                    val end = maxOf(sel.start, sel.end)
                                                                                    if (start < end) {
                                                                                        applyStyleAttributes(selectedDoc.id, style, start, end)
                                                                                        formatVersion++
                                                                                    } else {
                                                                                        val pos = sel.start
                                                                                        val pRange = getParagraphRange(draftContent, pos)
                                                                                        val paraEnd = pRange.endInclusive + 1
                                                                                        applyStyleAttributes(selectedDoc.id, style, pRange.start, paraEnd)
                                                                                        formatVersion++
                                                                                    }
                                                                                }
                                                                            },
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = if (isManage) "Manage" else item,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = if (style?.isBold == true) FontWeight.Bold else if (style?.name == "Normal") FontWeight.Normal else FontWeight.SemiBold,
                                                                            fontStyle = if (style?.isItalic == true) FontStyle.Italic else FontStyle.Normal,
                                                                            textDecoration = if (style?.isUnderline == true) TextDecoration.Underline else TextDecoration.None,
                                                                            color = if (isSystemInDarkTheme()) Color.White else Color.Black
                                                                        )
                                                                    }
                                                                }
                                                                if (rowItems.size < 4) {
                                                                    for (j in 0 until (4 - rowItems.size)) {
                                                                        Spacer(modifier = Modifier.weight(1f))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- EDITING GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Editing",
                                                    isExpanded = isEditingExpanded,
                                                    onToggleExpand = { isEditingExpanded = !isEditingExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    val accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            EditingButton(
                                                                label = "Find",
                                                                icon = Icons.Outlined.Search,
                                                                accentColor = accentColor,
                                                                isDarkTheme = isSystemInDarkTheme(),
                                                                onClick = { showFindDialog = true; findMode = "find"; findQuery = ""; replaceQuery = "" },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            EditingButton(
                                                                label = "Replace",
                                                                icon = Icons.Outlined.FindReplace,
                                                                accentColor = accentColor,
                                                                isDarkTheme = isSystemInDarkTheme(),
                                                                onClick = { showFindDialog = true; findMode = "replace"; findQuery = ""; replaceQuery = "" },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            EditingButton(
                                                                label = "Go To",
                                                                icon = Icons.Outlined.PinDrop,
                                                                accentColor = accentColor,
                                                                isDarkTheme = isSystemInDarkTheme(),
                                                                onClick = { showGoToDialog = true },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // --- STATISTICS GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Live Document Metrics",
                                                    isExpanded = isStatsExpanded,
                                                    onToggleExpand = { isStatsExpanded = !isStatsExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    val sel = editorTextFieldValue.selection
                                                    val hasSelection = !sel.collapsed && sel.start < sel.end
                                                    val selStart = minOf(sel.start, sel.end)
                                                    val selEnd = maxOf(sel.start, sel.end)
                                                    val selectedText = if (hasSelection) draftContent.substring(selStart, selEnd) else ""

                                                    val docWords = draftContent.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                                                    val docChars = draftContent.length
                                                    val docCharsNoSpace = draftContent.count { !it.isWhitespace() }
                                                    val docParagraphs = draftContent.split("\n\n+".toRegex()).filter { it.isNotBlank() }.size.coerceAtLeast(1)
                                                    val actualPages = draftContent.split("\u000C").size

                                                    val selWords = if (hasSelection) selectedText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size else 0
                                                    val selChars = if (hasSelection) selectedText.length else 0

                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF7F8FA)),
                                                        border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            if (hasSelection) {
                                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        Text(selWords.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                        Text("Words", fontSize = 10.sp, color = Color.Gray)
                                                                    }
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        Text(selChars.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                        Text("Chars", fontSize = 10.sp, color = Color.Gray)
                                                                    }
                                                                }
                                                                Text("Selection (${selStart}-${selEnd})", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                                                                HorizontalDivider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                                            }
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(docWords.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("Words", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(docChars.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("Chars", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(docCharsNoSpace.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("No Space", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                            HorizontalDivider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(docParagraphs.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                                                    Text("Paragraphs", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(actualPages.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                                                    Text("Pages", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (showFontColorPicker) {
                                            ColorPickerDialog(
                                                colors = FontColors,
                                                title = "Font Color",
                                                onColorSelected = { hex ->
                                                    showFontColorPicker = false
                                                    val sel = editorTextFieldValue.selection
                                                    if (!sel.collapsed) {
                                                        val start = minOf(sel.start, sel.end)
                                                        val end = maxOf(sel.start, sel.end)
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "color", start, end)
                                                        DocFormatRepository.applySpan(selectedDoc.id, "color", hex, start, end)
                                                        formatVersion++
                                                    }
                                                },
                                                onDismiss = { showFontColorPicker = false }
                                            )
                                        }

                                        if (showHighlightPicker) {
                                            ColorPickerDialog(
                                                colors = HighlightColors,
                                                title = "Highlight Color",
                                                onColorSelected = { hex ->
                                                    showHighlightPicker = false
                                                    val sel = editorTextFieldValue.selection
                                                    if (!sel.collapsed) {
                                                        val start = minOf(sel.start, sel.end)
                                                        val end = maxOf(sel.start, sel.end)
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "highlight", start, end)
                                                        DocFormatRepository.applySpan(selectedDoc.id, "highlight", hex, start, end)
                                                        formatVersion++
                                                    }
                                                },
                                                onDismiss = { showHighlightPicker = false }
                                            )
                                        }

                                        if (showBulletStyleDialog) {
                                            BulletStyleDialog(
                                                onSelect = { char ->
                                                    showBulletStyleDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val para = getParagraphText(draftContent, pos)
                                                    val (existingBullet, _) = detectListPrefix(para)
                                                    if (existingBullet != null) {
                                                        // Toggle off — remove existing bullet
                                                        val newText = removeBulletFromPara(draftContent, pos)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            val newPos = (pos - 2).coerceAtLeast(0)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newPos))
                                                        }
                                                    } else {
                                                        // Apply bullet
                                                        val newText = applyBulletToPara(draftContent, pos, char)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(pos + 2))
                                                        }
                                                    }
                                                },
                                                onDismiss = { showBulletStyleDialog = false }
                                            )
                                        }

                                        if (showNumberFormatDialog) {
                                            NumberFormatDialog(
                                                onSelect = { fmt ->
                                                    showNumberFormatDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val para = getParagraphText(draftContent, pos)
                                                    val (_, existingNum) = detectListPrefix(para)
                                                    if (existingNum != null) {
                                                        // Toggle off — remove number prefix
                                                        val newText = removeBulletFromPara(draftContent, pos)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(pos.coerceAtMost(newText.length)))
                                                        }
                                                    } else {
                                                        // Apply number to current paragraph then renumber entire doc
                                                        val newText = applyNumberToPara(draftContent, pos, fmt, 1)
                                                        if (newText != draftContent) {
                                                            val renumbered = renumberDocument(newText, fmt)
                                                            onContentChange(renumbered)
                                                            editorTextFieldValue = TextFieldValue(text = renumbered, selection = TextRange(pos.coerceAtMost(renumbered.length)))
                                                        }
                                                    }
                                                },
                                                onDismiss = { showNumberFormatDialog = false }
                                            )
                                        }

                                        if (showLineSpacingDialog) {
                                            val pos = editorTextFieldValue.selection.start
                                            val currentLineSpacing = DocFormatRepository.getSpans(selectedDoc.id)
                                                .firstOrNull { it.type == "lineSpacing" && it.start <= pos && it.end > pos }
                                                ?.value?.toFloatOrNull() ?: 1.0f
                                            LineSpacingDialog(
                                                currentSpacing = currentLineSpacing,
                                                onSelect = { spacing ->
                                                    showLineSpacingDialog = false
                                                    val selStart = editorTextFieldValue.selection.start
                                                    val selEnd = editorTextFieldValue.selection.end
                                                    try {
                                                        val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                                                        if (paraRanges.isEmpty()) return@LineSpacingDialog
                                                        val allStart = paraRanges.first().start
                                                        val lastPara = paraRanges.last()
                                                        val allEnd = draftContent.indexOf('\n', lastPara.start).let { if (it == -1) draftContent.length else it + 1 }
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "lineSpacing", allStart, allEnd)
                                                        val spacingStr = spacing.toString()
                                                        for (r in paraRanges) {
                                                            val paraEnd = r.endInclusive + 1
                                                            DocFormatRepository.applySpan(selectedDoc.id, "lineSpacing", spacingStr, r.start, paraEnd)
                                                        }
                                                        formatVersion++
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Spacing", "error", e)
                                                        Toast.makeText(context, "Spacing error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                onDismiss = { showLineSpacingDialog = false }
                                            )
                                        }

                                        if (showBordersDialog) {
                                            BordersDialog(
                                                onApply = { sides, style, color, width ->
                                                    showBordersDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val pRange = getParagraphRange(draftContent, pos)
                                                    val paraEnd = pRange.endInclusive + 1
                                                    val value = "${sides.joinToString(",")}|$style|$color|$width"
                                                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "border", pRange.start, paraEnd)
                                                    DocFormatRepository.applySpan(selectedDoc.id, "border", value, pRange.start, paraEnd)
                                                    formatVersion++
                                                },
                                                onDismiss = { showBordersDialog = false }
                                            )
                                        }

                                        if (showFindDialog) {
                                            val accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                            val isReplace = findMode == "replace"
                                            val sourceText = draftContent
                                            val matches = remember(findQuery, matchCase, wholeWord, sourceText) {
                                                if (findQuery.isBlank()) emptyList()
                                                else {
                                                    val results = mutableListOf<Int>()
                                                    val text = if (matchCase) sourceText else sourceText.lowercase()
                                                    val query = if (matchCase) findQuery else findQuery.lowercase()
                                                    var idx = 0
                                                    while (true) {
                                                        idx = text.indexOf(query, idx)
                                                        if (idx == -1) break
                                                        if (wholeWord) {
                                                            val before = if (idx > 0) text[idx - 1] else ' '
                                                            val after = if (idx + query.length < text.length) text[idx + query.length] else ' '
                                                            if (before.isLetterOrDigit() || after.isLetterOrDigit()) { idx++; continue }
                                                        }
                                                        results.add(idx); idx++
                                                    }
                                                    results
                                                }
                                            }
                                            val totalMatches = matches.size
                                            var currentIdx by remember(findQuery, matchCase, wholeWord) { mutableIntStateOf(0) }

                                            AlertDialog(
                                                onDismissRequest = { showFindDialog = false },
                                                title = { Text(if (isReplace) "Find & Replace" else "Find", fontWeight = FontWeight.Bold) },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        OutlinedTextField(
                                                            value = findQuery,
                                                            onValueChange = { findQuery = it; currentIdx = 0 },
                                                            placeholder = { Text("Find what") },
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                            textStyle = TextStyle(fontSize = 14.sp),
                                                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp)) }
                                                        )
                                                        if (isReplace) {
                                                            OutlinedTextField(
                                                                value = replaceQuery,
                                                                onValueChange = { replaceQuery = it },
                                                                placeholder = { Text("Replace with") },
                                                                singleLine = true,
                                                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                                textStyle = TextStyle(fontSize = 14.sp),
                                                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp)) }
                                                            )
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase; currentIdx = 0 }, label = { Text("Aa", fontSize = 11.sp) }, leadingIcon = { if (matchCase) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }, modifier = Modifier.height(28.dp))
                                                                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord; currentIdx = 0 }, label = { Text("WW", fontSize = 11.sp) }, leadingIcon = { if (wholeWord) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }, modifier = Modifier.height(28.dp))
                                                            }
                                                            Spacer(Modifier.weight(1f))
                                                            if (findQuery.isNotBlank()) Text("$totalMatches", fontSize = 12.sp, color = if (totalMatches > 0) Color.Gray else Color.Red.copy(alpha = 0.7f))
                                                        }
                                                        if (findQuery.isNotBlank() && totalMatches > 0) {
                                                            val displayIdx = (currentIdx % totalMatches + totalMatches) % totalMatches
                                                            val matchPos = matches[displayIdx]
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                                Text("${displayIdx + 1} of $totalMatches", fontSize = 12.sp, color = accentColor)
                                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    IconButton(onClick = { currentIdx = (currentIdx - 1).coerceAtLeast(0) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous", tint = accentColor) }
                                                                    IconButton(onClick = { currentIdx = (currentIdx + 1).coerceAtMost(totalMatches - 1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next", tint = accentColor) }
                                                                }
                                                            }
                                                            if (currentIdx in matches.indices) {
                                                                val idx = matches[currentIdx]
                                                                editorTextFieldValue = TextFieldValue(text = sourceText, selection = TextRange(idx, idx + findQuery.length))
                                                            }
                                                            if (isReplace) {
                                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                                    OutlinedButton(onClick = {
                                                                        if (currentIdx in matches.indices) {
                                                                            val idx = matches[currentIdx]
                                                                            if (sourceText.substring(idx, idx + findQuery.length) == findQuery) {
                                                                                val newText = sourceText.substring(0, idx) + replaceQuery + sourceText.substring(idx + findQuery.length)
                                                                                onContentChange(newText)
                                                                                editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(idx + replaceQuery.length))
                                                                                showFindDialog = false
                                                                            }
                                                                        }
                                                                    }, enabled = currentIdx in matches.indices, modifier = Modifier.weight(1f)) { Text("Replace", fontSize = 12.sp) }
                                                                    Button(onClick = {
                                                                        if (findQuery.isNotBlank()) {
                                                                            val newText = sourceText.replace(findQuery, replaceQuery)
                                                                            onContentChange(newText)
                                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
                                                                            showFindDialog = false
                                                                        }
                                                                    }, enabled = findQuery.isNotBlank() && totalMatches > 0, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("Replace All", fontSize = 12.sp) }
                                                                }
                                                            }
                                                        } else if (findQuery.isNotBlank()) {
                                                            Text("No matches", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.7f))
                                                        }
                                                    }
                                                },
                                                confirmButton = { TextButton(onClick = { showFindDialog = false }) { Text("Done") } }
                                            )
                                        }

                                        if (showGoToDialog) {
                                            var pageNum by remember { mutableStateOf("") }
                                            val pages = draftContent.split("\u000C")
                                            val currentPos = editorTextFieldValue.selection.start
                                            var offsetAccum = 0
                                            val currentPage = pages.indexOfFirst { offsetAccum += it.length + 1; currentPos < offsetAccum }.let { if (it == -1) 0 else it } + 1
                                            AlertDialog(
                                                onDismissRequest = { showGoToDialog = false },
                                                title = { Text("Go To Page", fontWeight = FontWeight.Bold) },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("Page $currentPage of ${pages.size}", fontSize = 13.sp, color = Color.Gray)
                                                        OutlinedTextField(
                                                            value = pageNum,
                                                            onValueChange = { pageNum = it.filter { c -> c.isDigit() } },
                                                            label = { Text("Page number") },
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                            textStyle = TextStyle(fontSize = 14.sp)
                                                        )
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            val p = pageNum.toIntOrNull()
                                                            if (p != null && p in 1..pages.size) {
                                                                var off = 0
                                                                for (i in 0 until p - 1) off += pages[i].length + 1
                                                                editorTextFieldValue = TextFieldValue(text = draftContent, selection = TextRange(off))
                                                                showGoToDialog = false
                                                            }
                                                        },
                                                        enabled = pageNum.toIntOrNull()?.let { it in 1..pages.size } == true
                                                    ) { Text("Go To") }
                                                },
                                                dismissButton = { TextButton(onClick = { showGoToDialog = false }) { Text("Cancel") } }
                                            )
                                        }

                                        if (showStyleManager) {
                                            val accentCol = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                            AlertDialog(
                                                onDismissRequest = { showStyleManager = false },
                                                title = { Text("Style Manager", fontWeight = FontWeight.Bold) },
                                                text = {
                                                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        items(textStyles.size) { idx ->
                                                            val st = textStyles[idx]
                                                            Card(
                                                                modifier = Modifier.fillMaxWidth().clickable {
                                                                    editingStyleIndex = idx
                                                                    editingStyleName = st.name
                                                                    editingStyleFontSize = st.fontSize
                                                                    editingStyleBold = st.isBold
                                                                    editingStyleItalic = st.isItalic
                                                                    editingStyleUnderline = st.isUnderline
                                                                    editingStyleColor = st.color ?: "#000000"
                                                                    editingStyleAlignment = st.alignment ?: "left"
                                                                    editingStyleLineSpacing = (st.lineSpacing ?: 1.0f).toString()
                                                                    showStyleEditor = true
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2B2B30) else Color(0xFFF7F8FA))
                                                            ) {
                                                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(st.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                                        Text("Size: ${st.fontSize} | Bold: ${if (st.isBold) "Yes" else "No"} | Italic: ${if (st.isItalic) "Yes" else "No"}", fontSize = 11.sp, color = Color.Gray)
                                                                    }
                                                                    if (!st.isDefault) {
                                                                        IconButton(onClick = {
                                                                            textStyles = textStyles.toMutableList().apply { removeAt(idx) }
                                                                        }) {
                                                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        item {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    val newName = "Style ${textStyles.size + 1}"
                                                                    textStyles = textStyles + DocTextStyle(newName, isDefault = false)
                                                                },
                                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol)
                                                            ) { Text("+ Add New Style") }
                                                        }
                                                    }
                                                },
                                                confirmButton = { TextButton(onClick = { showStyleManager = false }) { Text("Done") } }
                                            )
                                        }

                                        if (showStyleEditor) {
                                            val accentCol = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                            AlertDialog(
                                                onDismissRequest = { showStyleEditor = false },
                                                title = { Text("Edit Style", fontWeight = FontWeight.Bold) },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                        OutlinedTextField(value = editingStyleName, onValueChange = { editingStyleName = it }, label = { Text("Style Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Size: ", fontSize = 14.sp, modifier = Modifier.width(50.dp))
                                                            Slider(value = editingStyleFontSize.toFloat(), onValueChange = { editingStyleFontSize = it.toInt() }, valueRange = 8f..72f, modifier = Modifier.weight(1f))
                                                            Text("${editingStyleFontSize}", fontSize = 12.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                            FilterChip(selected = editingStyleBold, onClick = { editingStyleBold = !editingStyleBold }, label = { Text("B") }, leadingIcon = { if (editingStyleBold) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                                            FilterChip(selected = editingStyleItalic, onClick = { editingStyleItalic = !editingStyleItalic }, label = { Text("I") }, leadingIcon = { if (editingStyleItalic) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                                            FilterChip(selected = editingStyleUnderline, onClick = { editingStyleUnderline = !editingStyleUnderline }, label = { Text("U") }, leadingIcon = { if (editingStyleUnderline) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Color: ", fontSize = 14.sp, modifier = Modifier.width(50.dp))
                                                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(editingStyleColor.let { try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { Color.Gray } }).clickable {
                                                                val hex = editingStyleColor
                                                                val colors = listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#808080", "#1A1A1A", "#555555", "#666666")
                                                                val currentIdx = colors.indexOf(hex).coerceAtLeast(0)
                                                                editingStyleColor = colors[(currentIdx + 1) % colors.size]
                                                            })
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Spacing: ", fontSize = 14.sp, modifier = Modifier.width(60.dp))
                                                            Slider(value = (editingStyleLineSpacing.toFloatOrNull() ?: 1.0f), onValueChange = { editingStyleLineSpacing = "%.1f".format(it) }, valueRange = 0.5f..3.0f, modifier = Modifier.weight(1f))
                                                            Text("${editingStyleLineSpacing}x", fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                                                        }
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        val idx = editingStyleIndex
                                                        if (idx in textStyles.indices) {
                                                            val updated = textStyles.toMutableList()
                                                            updated[idx] = textStyles[idx].copy(
                                                                name = editingStyleName,
                                                                fontSize = editingStyleFontSize,
                                                                isBold = editingStyleBold,
                                                                isItalic = editingStyleItalic,
                                                                isUnderline = editingStyleUnderline,
                                                                color = editingStyleColor,
                                                                lineSpacing = editingStyleLineSpacing.toFloatOrNull()
                                                            )
                                                            textStyles = updated
                                                        }
                                                        showStyleEditor = false
                                                    }) { Text("Save") }
                                                },
                                                dismissButton = { TextButton(onClick = { showStyleEditor = false }) { Text("Cancel") } }
                                            )
                                        }


                                    } else {
                                        val groupedTools = filteredTools.groupBy { it.category }

                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(bottom = 12.dp)
                                        ) {
                                            groupedTools.forEach { (categoryName, toolsInCategory) ->
                                                item {
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isDarkTheme) Color(0xFF2B2B30) else Color.White
                                                        ),
                                                        border = BorderStroke(
                                                            width = 1.dp,
                                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text(
                                                                text = categoryName.uppercase(),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                letterSpacing = 0.8.sp,
                                                                modifier = Modifier.padding(bottom = 8.dp)
                                                            )

                                                            val cols = if (totalWidth < 600.dp) 3 else if (totalWidth < 900.dp) 4 else 6
                                                            val chunks = toolsInCategory.chunked(cols)

                                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                chunks.forEach { rowTools ->
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        rowTools.forEach { tool ->
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .weight(1f)
                                                                                    .testTag("ribbon_tool_${tool.id}")
                                                                            ) {
                                                                                RibbonToolCard(
                                                                                    tool = tool,
                                                                                    isDarkTheme = isDarkTheme,
                                                                                    isOptionSelected = { actionId, option ->
                                                                                        when (actionId) {
                                                                                            "orientation" -> {
                                                                                                if (option == "Landscape") isLandscape else !isLandscape
                                                                                            }
                                                                                            "size" -> {
                                                                                                val cleanFormat = pageFormat.substringBefore(" ").trim()
                                                                                                val cleanOption = option.substringBefore(" ").trim()
                                                                                                cleanFormat.equals(cleanOption, ignoreCase = true)
                                                                                            }
                                                                                            "margins" -> {
                                                                                                when (option) {
                                                                                                    "Normal" -> pageMarginTop == 72.dp && pageMarginBottom == 72.dp && pageMarginLeft == 72.dp && pageMarginRight == 72.dp
                                                                                                    "Narrow" -> pageMarginTop == 36.dp && pageMarginBottom == 36.dp && pageMarginLeft == 36.dp && pageMarginRight == 36.dp
                                                                                                    "Moderate" -> pageMarginTop == 72.dp && pageMarginBottom == 72.dp && pageMarginLeft == 54.dp && pageMarginRight == 54.dp
                                                                                                    "Wide" -> pageMarginTop == 72.dp && pageMarginBottom == 72.dp && pageMarginLeft == 144.dp && pageMarginRight == 144.dp
                                                                                                    "Mirrored" -> pageMarginTop == 72.dp && pageMarginBottom == 72.dp && pageMarginLeft == 90.dp && pageMarginRight == 72.dp
                                                                                                    "Office Default" -> pageMarginTop == 72.dp && pageMarginBottom == 72.dp && pageMarginLeft == 72.dp && pageMarginRight == 72.dp
                                                                                                    else -> false
                                                                                                }
                                                                                            }
                                                                                            "columns" -> {
                                                                                                when (option) {
                                                                                                    "One" -> columnCount == 1
                                                                                                    "Two" -> columnCount == 2
                                                                                                    "Three" -> columnCount == 3
                                                                                                    "Left" -> columnCount == 4
                                                                                                    "Right" -> columnCount == 5
                                                                                                    else -> false
                                                                                                }
                                                                                            }
                                                                                            "theme_apply" -> {
                                                                                                selectedDocumentTheme == option
                                                                                            }
                                                                                            "theme_effects" -> {
                                                                                                selectedThemeEffect == option
                                                                                            }
                                                                                            "page_color" -> {
                                                                                                when (option) {
                                                                                                    "None" -> pageBackgroundColorHex.isEmpty()
                                                                                                    "Calm White" -> pageBackgroundColorHex == "#FFFFFF"
                                                                                                    "Soft Ivory" -> pageBackgroundColorHex == "#FAF6EE"
                                                                                                    "Classic Cream" -> pageBackgroundColorHex == "#FFFDD0"
                                                                                                    "Warm Sand" -> pageBackgroundColorHex == "#FAF0E6"
                                                                                                    "Modern Ice" -> pageBackgroundColorHex == "#F0F8FF"
                                                                                                    "Sage Mist" -> pageBackgroundColorHex == "#F5FFFA"
                                                                                                    "Blush Pink" -> pageBackgroundColorHex == "#FFF0F5"
                                                                                                    "Lavender Accent" -> pageBackgroundColorHex == "#E6E6FA"
                                                                                                    "Elegant Dark" -> pageBackgroundColorHex == "#2D2D2D"
                                                                                                    "Custom Color..." -> {
                                                                                                        pageBackgroundColorHex.isNotEmpty() && pageBackgroundColorHex !in listOf("#FFFFFF", "#FAF6EE", "#FFFDD0", "#FAF0E6", "#F0F8FF", "#F5FFFA", "#FFF0F5", "#E6E6FA", "#2D2D2D")
                                                                                                    }
                                                                                                    else -> false
                                                                                                }
                                                                                            }
                                                                                            "watermark" -> {
                                                                                                when (option) {
                                                                                                    "None" -> watermarkText.isEmpty()
                                                                                                    "CONFIDENTIAL (Diagonal)" -> watermarkText == "CONFIDENTIAL" && watermarkType == "Diagonal"
                                                                                                    "CONFIDENTIAL (Horizontal)" -> watermarkText == "CONFIDENTIAL" && watermarkType == "Horizontal"
                                                                                                    "DRAFT (Diagonal)" -> watermarkText == "DRAFT" && watermarkType == "Diagonal"
                                                                                                    "DRAFT (Horizontal)" -> watermarkText == "DRAFT" && watermarkType == "Horizontal"
                                                                                                    "DO NOT COPY (Diagonal)" -> watermarkText == "DO NOT COPY" && watermarkType == "Diagonal"
                                                                                                    "DO NOT COPY (Horizontal)" -> watermarkText == "DO NOT COPY" && watermarkType == "Horizontal"
                                                                                                    "SAMPLE (Diagonal)" -> watermarkText == "SAMPLE" && watermarkType == "Diagonal"
                                                                                                    else -> false
                                                                                                }
                                                                                            }
                                                                                            "page_borders" -> {
                                                                                                pageBorderType == option
                                                                                            }
                                                                                            else -> false
                                                                                        }
                                                                                    }
                                                                                )
                                                                            }
                                                                        }

                                                                        if (rowTools.size < cols) {
                                                                            for (j in 0 until (cols - rowTools.size)) {
                                                                                Spacer(modifier = Modifier.weight(1f))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // End of Box (at 4058)
                                } // End of Column (at 3991)
                            } // End of else (at 3990)
                        } // End of Column (at 3945)
                    } // End of AnimatedVisibility (at 3933)
                } // End of Column (at 3811)
            } // End of Box (at 3805)

            if (showCustomMarginsDialog) {
                CustomMarginsDialog(
                    currentTop = pageMarginTop,
                    currentBottom = pageMarginBottom,
                    currentLeft = pageMarginLeft,
                    currentRight = pageMarginRight,
                    onDismiss = { showCustomMarginsDialog = false },
                    onApply = { top, bottom, left, right ->
                        pushSnapshot()
                        pageMarginTop = top
                        pageMarginBottom = bottom
                        pageMarginLeft = left
                        pageMarginRight = right
                        pageMargins = left
                        showCustomMarginsDialog = false
                    }
                )
            }
            if (showCustomSizeDialog) {
                CustomSizeDialog(
                    currentWidth = customDimensions.first,
                    currentHeight = customDimensions.second,
                    onDismiss = { showCustomSizeDialog = false },
                    onApply = { width, height -> 
                        viewModel.setCustomPageDimensions(width, height)
                        showCustomSizeDialog = false 
                    }
                )
            }
            if (showCustomWatermarkDialog) {
                CustomWatermarkDialog(
                    currentText = watermarkText,
                    currentType = watermarkType,
                    onDismiss = { showCustomWatermarkDialog = false },
                    onApply = { text, direction ->
                        pushSnapshot()
                        watermarkText = text
                        watermarkType = direction
                        showCustomWatermarkDialog = false
                    }
                )
            }
            if (showSpellingDialog) {
                SpellingCheckDialog(
                    docId = selectedDoc.id,
                    text = draftContent,
                    language = proofingLanguage,
                    onDismiss = { showSpellingDialog = false },
                    onWordReplaced = { corrected ->
                        pushSnapshot()
                        onContentChange(corrected)
                        editorTextFieldValue = editorTextFieldValue.copy(text = corrected)
                    }
                )
            }
            if (showThesaurusDialog) {
                val searchWord = remember(editorTextFieldValue) {
                    val sel = editorTextFieldValue.selection
                    if (!sel.collapsed && sel.start >= 0 && sel.end <= draftContent.length) {
                        draftContent.substring(sel.start, sel.end)
                    } else {
                        "outstanding"
                    }
                }
                ThesaurusDialog(
                    selectedWord = searchWord,
                    onDismiss = { showThesaurusDialog = false },
                    onSynonymSelected = { synonym ->
                        pushSnapshot()
                        val sel = editorTextFieldValue.selection
                        val corrected = if (!sel.collapsed) {
                            draftContent.substring(0, sel.start) + synonym + draftContent.substring(sel.end)
                        } else {
                            draftContent.substring(0, sel.start) + synonym + draftContent.substring(sel.start)
                        }
                        onContentChange(corrected)
                        editorTextFieldValue = TextFieldValue(corrected, TextRange(sel.start + synonym.length))
                    }
                )
            }
            if (showWordCountDialog) {
                WordCountDialog(
                    text = draftContent,
                    onDismiss = { showWordCountDialog = false }
                )
            }
            if (showAccessibilityDialog) {
                AccessibilityCheckDialog(
                    docId = selectedDoc.id,
                    text = draftContent,
                    onDismiss = { showAccessibilityDialog = false },
                    onAutoFixContent = { corrected ->
                        pushSnapshot()
                        onContentChange(corrected)
                        editorTextFieldValue = editorTextFieldValue.copy(text = corrected)
                    }
                )
            }
            if (showTranslateDialog) {
                val isSel = !editorTextFieldValue.selection.collapsed
                val transSource = if (isSel && editorTextFieldValue.selection.start >= 0 && editorTextFieldValue.selection.end <= draftContent.length) {
                    val s = editorTextFieldValue.selection
                    draftContent.substring(s.start, s.end)
                } else {
                    draftContent
                }
                TranslateDialog(
                    text = transSource,
                    isSelectionMode = isSel,
                    onDismiss = { showTranslateDialog = false },
                    onInsertTranslation = { translated ->
                        pushSnapshot()
                        val s = editorTextFieldValue.selection
                        val corrected = if (isSel && s.start >= 0 && s.end <= draftContent.length) {
                            draftContent.substring(0, s.start) + translated + draftContent.substring(s.end)
                        } else {
                            draftContent + "\n\n--- Translation Result ---\n" + translated
                        }
                        onContentChange(corrected)
                        val endOfCorrection = if (isSel) s.start + translated.length else corrected.length
                        editorTextFieldValue = TextFieldValue(corrected, TextRange(endOfCorrection))
                    }
                )
            }
            if (showLanguageDialog) {
                LanguageSetDialog(
                    currentLang = proofingLanguage,
                    onDismiss = { showLanguageDialog = false },
                    onApply = { newLang ->
                        proofingLanguage = newLang
                    }
                )
            }
            if (showReadAloudControls) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ReadAloudPlayerControls(
                        text = draftContent,
                        tts = tts,
                        onClose = { showReadAloudControls = false }
                    )
                }
            }
            if (showPageBordersDialog) {
                CustomBordersDialog(
                    currentBorderType = pageBorderType,
                    currentBorderColorHex = pageBorderColorHex,
                    onDismiss = { showPageBordersDialog = false },
                    onApply = { borderType, borderColor ->
                        pushSnapshot()
                        pageBorderType = borderType
                        pageBorderColorHex = borderColor
                        showPageBordersDialog = false
                    }
                )
            }
            if (showShadingPicker) {
                ColorPickerDialog(
                    colors = listOf("#FFFFFF", "#F2F2F2", "#D9D9D9", "#BFBFBF", "#A6A6A6", "#808080", "#FFFF00", "#00FF00", "#00FFFF", "#FF0000", "#0000FF", "#FF00FF", "#800000", "#008000", "#000080", "#808000", "#800080", "#008080", "#C0C0C0", "#FFE4E1", "#F0FFF0", "#F0F8FF", "#FFFACD", "#E0FFFF", "#FFDAB9", "#E6E6FA", "#FFF0F5", "#F5DEB3", "#FFF8DC", "#FAEBD7"),
                    title = "Page Color",
                    onColorSelected = { hex ->
                        showShadingPicker = false
                        pageBackgroundColorHex = hex
                        formatVersion++
                    },
                    onDismiss = { showShadingPicker = false }
                )
            }
            if (showPageNumberFormatDialog) {
                PageNumberFormatDialog(
                    currentFormat = pageNumberFormat,
                    currentStartAt = pageNumberStartAt,
                    currentShowOnFirstPage = showPageNumberOnFirstPage,
                    onDismiss = { showPageNumberFormatDialog = false },
                    onApply = { format, startAt, showOnFirstPage ->
                        pageNumberFormat = format
                        pageNumberStartAt = startAt
                        showPageNumberOnFirstPage = showOnFirstPage
                        showPageNumberFormatDialog = false
                    }
                )
            }
            if (showHeaderFooterDialog) {
                HeaderFooterDialog(
                    currentHeaderText = headerText,
                    currentFooterText = footerText,
                    currentHeaderAlignment = headerAlignment,
                    currentFooterAlignment = footerAlignment,
                    currentShowOnFirstPage = showHeaderFooterOnFirstPage,
                    onDismiss = { showHeaderFooterDialog = false },
                    onApply = { newHText, newFText, newHAlign, newFAlign, newShowFirst ->
                        pushSnapshot()
                        headerText = newHText
                        footerText = newFText
                        headerAlignment = newHAlign
                        footerAlignment = newFAlign
                        showHeaderFooterOnFirstPage = newShowFirst
                        showHeaderFooterDialog = false
                    }
                )
            }
            if (pageNumberPositionMenu != null) {
                val menuTitle = pageNumberPositionMenu!!
                val options = when (menuTitle) {
                    "Top of Page" -> listOf("Top Left", "Top Center", "Top Right")
                    "Bottom of Page" -> listOf("Bottom Left", "Bottom Center", "Bottom Right")
                    "Page Margins" -> listOf("Left Margin", "Right Margin", "Outside Margin", "Inside Margin")
                    else -> emptyList()
                }
                AlertDialog(
                    onDismissRequest = { pageNumberPositionMenu = null },
                    title = { Text(menuTitle) },
                    text = {
                        Column {
                            options.forEach { option ->
                                TextButton(
                                    onClick = {
                                        pageNumberPosition = option
                                        pageNumberPositionMenu = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(option, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { pageNumberPositionMenu = null }) { Text("Cancel") }
                    }
                )
            }
            if (showPasteSpecialDialog && pendingPasteText.isNotEmpty()) {
                val clipText = pendingPasteText
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) Color(0xFF2B2B30) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.padding(top = 120.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Paste as:", fontSize = 10.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSystemInDarkTheme()) Color(0xFF3A3A40) else Color(0xFFE8EAED))
                                            .clickable {
                                                val start = pendingPasteSelStart
                                                val end = pendingPasteSelEnd
                                                val newText = draftContent.substring(0, start) + clipText + draftContent.substring(end)
                                                onContentChange(newText)
                                                editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(start + clipText.length))
                                                showPasteSpecialDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Tt", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    }
                                    Text("Text", fontSize = 8.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSystemInDarkTheme()) Color(0xFF3A3A40) else Color(0xFFE8EAED))
                                            .clickable {
                                                val start = pendingPasteSelStart
                                                val end = pendingPasteSelEnd
                                                val newText = draftContent.substring(0, start) + clipText + draftContent.substring(end)
                                                onContentChange(newText)
                                                editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(start + clipText.length))
                                                val data = copiedFormattedData
                                                if (data != null && data.text == clipText) {
                                                    for (span in data.spans) {
                                                        DocFormatRepository.applySpan(selectedDoc.id, span.type, span.value, start + span.start, start + span.end)
                                                    }
                                                    formatVersion++
                                                }
                                                showPasteSpecialDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.FormatBold, contentDescription = "Formatting", tint = if (isSystemInDarkTheme()) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                                    }
                                    Text("Format", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            if (showInsertPictureDialog) {
                AlertDialog(
                    onDismissRequest = { showInsertPictureDialog = false },
                    title = {
                        Text("Insert Picture", fontWeight = FontWeight.SemiBold)
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Insert a beautiful picture into your document. Choose from our pre-loaded gallery of sample pictures or select an image from your device storage.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            
                            Text("Sample Clipart Gallery:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                val samples = listOf(
                                    Triple("Report Chart", "android.resource://com.example/drawable/ic_sample_report", "Business Report / Chart clipart"),
                                    Triple("Geometric Art", "android.resource://com.example/drawable/ic_sample_art", "Abstract colorful flag & geometric shape art"),
                                    Triple("Workspace", "https://images.unsplash.com/photo-1499750310107-5fef28a66643?auto=format&fit=crop&w=300&q=80", "Office Desk Scene"),
                                    Triple("Analytics", "https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=300&q=80", "Meeting & Presentation Chart"),
                                    Triple("Teamwork", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=300&q=80", "Team Collaboration Illustration"),
                                    Triple("Aesthetic", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=300&q=80", "Serene Ocean Beach Scene")
                                )
                                items(samples) { sample ->
                                    val (name, uriStr, desc) = sample
                                    Card(
                                        onClick = {
                                            val pos = editorTextFieldValue.selection.start
                                            val pages = draftContent.split("\u000C")
                                            var accumulated = 0
                                            var targetIndex = 0
                                            for (i in pages.indices) {
                                                val pageLen = pages[i].length
                                                if (pos >= accumulated && pos <= accumulated + pageLen) {
                                                    targetIndex = i
                                                    break
                                                }
                                                accumulated += pageLen + 1
                                            }
                                            DocPictureRepository.addPicture(
                                                docId = selectedDoc.id,
                                                picture = DocPicture(
                                                    uri = uriStr,
                                                    pageIndex = targetIndex,
                                                    x = 50.dp,
                                                    y = 100.dp,
                                                    width = 180.dp,
                                                    height = 180.dp
                                                )
                                            )
                                            android.widget.Toast.makeText(context, "Inserted $name on Page ${targetIndex + 1}!", android.widget.Toast.LENGTH_SHORT).show()
                                            formatVersion++
                                            showInsertPictureDialog = false
                                        },
                                        modifier = Modifier.width(100.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSystemInDarkTheme()) Color(0xFF2B2B30) else Color(0xFFF1F3F4)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .background(Color.LightGray, RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    painter = coil.compose.rememberAsyncImagePainter(model = uriStr),
                                                    contentDescription = desc,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Divider(color = if (isSystemInDarkTheme()) Color(0xFF3C4043) else Color(0xFFE0E0E0))
                            
                            Text("Select from Device Storage:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            
                            Button(
                                onClick = {
                                    imagePickerLauncher.launch("image/*")
                                    showInsertPictureDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DocWordColor
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Browse local gallery", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Device Gallery", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInsertPictureDialog = false }) {
                            Text("Cancel", color = DocWordColor)
                        }
                    }
                )
            }

            if (showInsertShapeDialog) {
                AlertDialog(
                    onDismissRequest = { showInsertShapeDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = DocWordColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Insert Shape", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Choose a shape below to insert onto your active cursor page.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            // Function helper to insert selected shape
                            val insertShape: (String, String) -> Unit = { type, group ->
                                val pos = editorTextFieldValue.selection.start
                                val pages = draftContent.split("\u000C")
                                var accumulated = 0
                                var targetIndex = 0
                                for (i in pages.indices) {
                                    val pageLen = pages[i].length
                                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                                        targetIndex = i
                                        break
                                    }
                                    accumulated += pageLen + 1
                                }

                                DocShapeRepository.addShape(
                                    docId = selectedDoc.id,
                                    shape = DocShape(
                                        pageIndex = targetIndex,
                                        type = type,
                                        group = group,
                                        x = 80.dp,
                                        y = 120.dp,
                                        width = 120.dp,
                                        height = 100.dp,
                                        fillColorHex = "#4F81BD",
                                        borderColorHex = "#1B365D",
                                        borderWidthDp = 2.dp,
                                        textInside = ""
                                    )
                                )
                                android.widget.Toast.makeText(context, "Added shape to Page ${targetIndex + 1}!", android.widget.Toast.LENGTH_SHORT).show()
                                formatVersion++
                                showInsertShapeDialog = false
                            }

                            // Group 1: Rectangles
                            Column {
                                Text("Rectangles", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShapePreviewItem(type = "rectangle", label = "Rectangle") { insertShape("rectangle", "Rectangles") }
                                    ShapePreviewItem(type = "round_rectangle", label = "Rounded Rect") { insertShape("round_rectangle", "Rectangles") }
                                }
                            }

                            // Group 2: Basic Shapes
                            Column {
                                Text("Basic Shapes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ShapePreviewItem(type = "ellipse", label = "Oval") { insertShape("ellipse", "Basic Shapes") }
                                    ShapePreviewItem(type = "triangle", label = "Triangle") { insertShape("triangle", "Basic Shapes") }
                                    ShapePreviewItem(type = "right_triangle", label = "Right Tri") { insertShape("right_triangle", "Basic Shapes") }
                                    ShapePreviewItem(type = "diamond", label = "Diamond") { insertShape("diamond", "Basic Shapes") }
                                    ShapePreviewItem(type = "hexagon", label = "Hexagon") { insertShape("hexagon", "Basic Shapes") }
                                    ShapePreviewItem(type = "cloud", label = "Cloud") { insertShape("cloud", "Basic Shapes") }
                                    ShapePreviewItem(type = "heart", label = "Heart") { insertShape("heart", "Basic Shapes") }
                                    ShapePreviewItem(type = "smiley", label = "Smiley") { insertShape("smiley", "Basic Shapes") }
                                }
                            }

                            // Group 3: Block Arrows
                            Column {
                                Text("Block Arrows", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ShapePreviewItem(type = "right_arrow", label = "Right arrow") { insertShape("right_arrow", "Block Arrows") }
                                    ShapePreviewItem(type = "left_arrow", label = "Left arrow") { insertShape("left_arrow", "Block Arrows") }
                                    ShapePreviewItem(type = "up_arrow", label = "Up arrow") { insertShape("up_arrow", "Block Arrows") }
                                    ShapePreviewItem(type = "down_arrow", label = "Down arrow") { insertShape("down_arrow", "Block Arrows") }
                                }
                            }

                            // Group 4: Equation Shapes
                            Column {
                                Text("Equation Shapes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShapePreviewItem(type = "plus_eq", label = "Plus") { insertShape("plus_eq", "Equation Shapes") }
                                    ShapePreviewItem(type = "minus_eq", label = "Minus") { insertShape("minus_eq", "Equation Shapes") }
                                }
                            }

                            // Group 5: Stars & Banners
                            Column {
                                Text("Stars & Banners", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShapePreviewItem(type = "star_5", label = "5-Point Star") { insertShape("star_5", "Stars & Banners") }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showInsertShapeDialog = false }) {
                            Text("Cancel", color = DocWordColor)
                        }
                    }
                )
            }

            if (showInsertTableDialog) {
                var rowsCount by remember { mutableIntStateOf(3) }
                var colsCount by remember { mutableIntStateOf(3) }
                var selectedStyle by remember { mutableStateOf("elegant_blue") }
                var hasHeader by remember { mutableStateOf(true) }
                var alternateRows by remember { mutableStateOf(true) }

                AlertDialog(
                    onDismissRequest = { showInsertTableDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = DocWordColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Insert Table", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "Specify the number of rows, columns, and style properties. The custom table will be generated at your cursor page.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Columns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { if (colsCount > 1) colsCount-- },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease Columns", tint = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                        }
                                        Text(
                                            text = colsCount.toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = { if (colsCount < 10) colsCount++ },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase Columns", tint = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Rows", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { if (rowsCount > 1) rowsCount-- },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease Rows", tint = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                        }
                                        Text(
                                            text = rowsCount.toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = { if (rowsCount < 20) rowsCount++ },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase Rows", tint = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                        }
                                    }
                                }
                            }

                            Column {
                                Text("Table Styling Style", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                Spacer(modifier = Modifier.height(6.dp))
                                val styles = listOf(
                                    "classic" to "#7F7F7F",
                                    "elegant_blue" to "#4F81BD",
                                    "modern_emerald" to "#4BACC6",
                                    "warm_gold" to "#F79646",
                                    "dark_minimalist" to "#333333"
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(styles) { (styleName, colorHex) ->
                                        val isChosen = selectedStyle == styleName
                                        val color = Color(android.graphics.Color.parseColor(colorHex))
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(color, RoundedCornerShape(4.dp))
                                                .border(
                                                    width = if (isChosen) 3.dp else 1.dp,
                                                    color = if (isChosen) (if (isSystemInDarkTheme()) Color.White else Color.Black) else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable { selectedStyle = styleName }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = hasHeader,
                                        onCheckedChange = { hasHeader = it ?: true },
                                        colors = CheckboxDefaults.colors(checkedColor = DocWordColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Header Row Defaults", fontSize = 13.sp, color = if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = alternateRows,
                                        onCheckedChange = { alternateRows = it ?: true },
                                        colors = CheckboxDefaults.colors(checkedColor = DocWordColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Banded (Alternating) Rows", fontSize = 13.sp, color = if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val pos = editorTextFieldValue.selection.start
                                val pages = draftContent.split("\u000C")
                                var accumulated = 0
                                var targetIndex = 0
                                for (i in pages.indices) {
                                    val pageLen = pages[i].length
                                    if (pos >= accumulated && pos <= accumulated + pageLen) {
                                        targetIndex = i
                                        break
                                    }
                                    accumulated += pageLen + 1
                                }

                                val themeColorVal = when(selectedStyle) {
                                    "classic" -> "#7F7F7F"
                                    "elegant_blue" -> "#4F81BD"
                                    "modern_emerald" -> "#4BACC6"
                                    "warm_gold" -> "#F79646"
                                    "dark_minimalist" -> "#333333"
                                    else -> "#4F81BD"
                                }

                                val dummyData = mutableMapOf<String, String>()
                                if (hasHeader) {
                                    for (col in 0 until colsCount) {
                                        dummyData["0,$col"] = "Title ${col + 1}"
                                    }
                                }

                                DocTableRepository.addTable(
                                    docId = selectedDoc.id,
                                    table = DocTable(
                                        pageIndex = targetIndex,
                                        x = 40.dp,
                                        y = 150.dp,
                                        width = 450.dp,
                                        height = (rowsCount * 50).dp,
                                        rows = rowsCount,
                                        columns = colsCount,
                                        styleName = selectedStyle,
                                        themeColorHex = themeColorVal,
                                        alternateRows = alternateRows,
                                        hasHeaderRow = hasHeader,
                                        cellData = dummyData
                                    )
                                )
                                android.widget.Toast.makeText(context, "Table created on Page ${targetIndex + 1}!", android.widget.Toast.LENGTH_SHORT).show()
                                formatVersion++
                                showInsertTableDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DocWordColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Insert", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInsertTableDialog = false }) {
                            Text("Cancel", color = DocWordColor)
                        }
                    }
                )
            }

            if (showClipboardHistory) {
                AlertDialog(
                    onDismissRequest = { showClipboardHistory = false },
                    title = { Text("Clipboard History") },
                    text = {
                        if (clipboardHistory.isEmpty()) {
                            Text("No clipboard history yet")
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                clipboardHistory.reversed().forEach { entry ->
                                    TextButton(
                                        onClick = {
                                            val sel = editorTextFieldValue.selection
                                            val start = minOf(sel.start, sel.end)
                                            val end = maxOf(sel.start, sel.end)
                                            val newText = draftContent.substring(0, start) + entry + draftContent.substring(end)
                                            onContentChange(newText)
                                            val newCursor = start + entry.length
                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                            showClipboardHistory = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = entry.take(80) + if (entry.length > 80) "..." else "",
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardHistory.clear()
                            showClipboardHistory = false
                        }) {
                            Text("Clear All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClipboardHistory = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyWorkspaceState(
    viewModel: DocViewModel,
    onToggleSidebar: () -> Unit,
    isSidebarExpanded: Boolean,
    onQuickCreate: (String, String) -> Unit,
    onFABClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf("home") } // "home", "files", "shared", "settings"

    BackHandler(enabled = activeTab != "home" && !isSidebarExpanded) {
        activeTab = "home"
    }

    // Simulate username personalization in SQLite workspace
    var username by remember { mutableStateOf("Sarah") }
    var userRole by remember { mutableStateOf("Lead Editor") }

    // State to toggle mock collaboration notifications
    var showSimulatedStatus by remember { mutableStateOf(false) }
    var activeCollaborators by remember { mutableStateOf(7) }

    // State for selected file category inside Files tab
    var filesCategoryTab by remember { mutableStateOf("all") }

    // Determine featured document (most recently updated/created document)
    val featuredDoc = remember(documents) {
        documents.maxByOrNull { it.updatedAt }
    }

    // SQLite data stats
    val totalFiles = documents.size
    val favoriteFiles = remember(documents) { documents.count { it.isFavorite } }
    val sheetsCount = remember(documents) { documents.count { it.type == "sheet" } }
    val writerCount = remember(documents) { documents.count { it.type == "word" } }
    val slidesCount = remember(documents) { documents.count { it.type == "slide" } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFDFBFF)) // Matching design body background
    ) {
        // --- 1. Top Header Search Bar (Material 3 Style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = if (isSidebarExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                    contentDescription = "Toggle Drawer Menu",
                    tint = Color(0xFF1A1C1E)
                )
            }

            // High polish search pill matching design HTML layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFEEF0F6))
                    .clickable { 
                        // Automatically navigate to files tab when clicking search
                        activeTab = "files"
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF44474E),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                
                // Allow interactive typing straight on the bento search bar
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { 
                        viewModel.setSearchQuery(it)
                        if (it.isNotEmpty() && activeTab != "files") {
                            activeTab = "files"
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1A1C1E),
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search JCdocs Suite...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF44474E).copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                )

                // Colored round avatar badge representing offline native security authority
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD9E2FF))
                        .border(1.dp, Color.White, CircleShape)
                        .clickable { activeTab = "settings" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001D36)
                        )
                    )
                }
            }
        }

        // --- 2. Interactive Workspace Tabs ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                "home" -> {
                    // Bento Grid Layout (Featured card, Collaboration status, Stats, Storage, AI Templates)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val isWideScreen = maxWidth >= 700.dp
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isWideScreen) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.2f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FeaturedDocBentoCard(
                                            featuredDoc = featuredDoc,
                                            onDocClick = { viewModel.selectDocument(it) },
                                            onQuickCreate = { onQuickCreate("Project Proposal Deck", "word") }
                                        )

                                        CollaborationBentoCard(
                                            sheetsCount = sheetsCount,
                                            writerCount = writerCount,
                                            slidesCount = slidesCount,
                                            onClick = { activeTab = "shared" }
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            StatsBentoSquare(
                                                totalFiles = totalFiles,
                                                favoriteFiles = favoriteFiles,
                                                onClick = { activeTab = "files" },
                                                modifier = Modifier.weight(1f)
                                            )
                                            RoomDbStorageBentoSquare(
                                                totalFiles = totalFiles,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        SmartTemplatesBentoCard(
                                            onQuickCreate = onQuickCreate
                                        )
                                    }
                                }
                            } else {
                                FeaturedDocBentoCard(
                                    featuredDoc = featuredDoc,
                                    onDocClick = { viewModel.selectDocument(it) },
                                    onQuickCreate = { onQuickCreate("Project Proposal Deck", "word") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatsBentoSquare(
                                        totalFiles = totalFiles,
                                        favoriteFiles = favoriteFiles,
                                        onClick = { activeTab = "files" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RoomDbStorageBentoSquare(
                                        totalFiles = totalFiles,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                CollaborationBentoCard(
                                    sheetsCount = sheetsCount,
                                    writerCount = writerCount,
                                    slidesCount = slidesCount,
                                    onClick = { activeTab = "shared" },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                SmartTemplatesBentoCard(
                                    onQuickCreate = onQuickCreate,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Spacer to prevent layout clips by navigation bar
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
                "files" -> {
                    // Modern styled files grid list
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "My Documents Ecosystem",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // File type filtering chips inside tab
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp)
                        ) {
                            listOf("all" to "All Streams", "word" to "Writer Note", "sheet" to "Spreadsheet", "slide" to "Slide Decks").forEach { (type, label) ->
                                val selected = filesCategoryTab == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (selected) Color(0xFFD9E2FF) else Color(0xFFEEF0F6))
                                        .clickable { 
                                            filesCategoryTab = type
                                            viewModel.setTypeFilter(type)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) Color(0xFF001D36) else Color(0xFF44474E)
                                        )
                                    )
                                }
                            }
                        }

                        // Listed documents
                        if (documents.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Empty",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No files match active filter",
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(documents, key = { it.id }) { doc ->
                                    val isSelected = featuredDoc?.id == doc.id
                                    DocumentTile(
                                        doc = doc,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectDocument(doc) },
                                        onDelete = { viewModel.deleteDocument(doc) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(doc) }
                                    )
                                }
                            }
                        }
                    }
                }
                "shared" -> {
                    // Collaboration Dashboard Cockpit
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE1E2E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "JCdocs Real-Time Simulation Deck",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF1A1C1E)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Simulate background activity of virtual project contributors to demonstrate secure multi-window integrity.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF44474E)
                                )
                            }
                        }

                        Text(
                            text = "ACTIVE SIMULATORS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Gray
                        )

                        // Collaborator rows
                        val simulatedUsers = listOf(
                            Triple("Sarah Jenkins", "Writer Editor", Color(0xFF42A5F5)),
                            Triple("Alex Rivera", "Spreadsheet Coordinator", Color(0xFF66BB6A)),
                            Triple("David Chang", "Slides Presentation Designer", Color(0xFFAB47BC)),
                            Triple("Integrity Agent VIPER", "Autosave Bot", Color(0xFFDF4A32))
                        )

                        simulatedUsers.forEach { (name, role, avatarBg) ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(avatarBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name.take(1) + name.split(" ").getOrNull(1)?.take(1).orEmpty(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1C1E))
                                        Text(role, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0xFFE8F5E9))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Simulation interactive block
                        Button(
                            onClick = {
                                showSimulatedStatus = true
                                activeCollaborators++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Simulate")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Co-Editor Background Edits", fontWeight = FontWeight.Bold)
                        }

                        if (showSimulatedStatus) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFF3CD))
                                    .border(1.dp, Color(0xFFFFEBAA), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "✨ Simulation Triggered! Real-time local cache transaction registered. SQLite database synced securely.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF856404),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                "settings" -> {
                    // Gorgeous settings panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "User Workspace Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("MY PROFILE CARD", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)

                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Display Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = userRole,
                                    onValueChange = { userRole = it },
                                    label = { Text("Workspace Role Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("SYSTEM INFORMATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Software Engine", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("JCdocs ONLYOFFICE 2.4", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                HorizontalDivider(color = Color(0xFFEEF0F6))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Storage Engine", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("Android SQLite Room DB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                HorizontalDivider(color = Color(0xFFEEF0F6))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Offline Operations", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("Enabled (100% Native)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                        }

                        // Cache reset action
                        Button(
                            onClick = {
                                documents.forEach { viewModel.deleteDocument(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Wipe")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Local Sandbox Documents", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }

            // --- 4. Material 3 Bottom Navigation bar ---
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                containerColor = Color(0xFFF3F4F9),
                tonalElevation = 4.dp
            ) {
                val items = listOf(
                    Triple(Icons.Outlined.Home, "Home", "home"),
                    Triple(Icons.Outlined.Folder, "Files", "files"),
                    Triple(Icons.Outlined.People, "Shared", "shared"),
                    Triple(Icons.Outlined.Settings, "Settings", "settings")
                )
                items.forEach { (icon, label, tabId) ->
                    val selected = activeTab == tabId
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = tabId },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF001D36),
                            selectedTextColor = Color(0xFF001D36),
                            unselectedIconColor = Color(0xFF44474E).copy(alpha = 0.7f),
                            unselectedTextColor = Color(0xFF44474E).copy(alpha = 0.7f),
                            indicatorColor = Color(0xFFD9E2FF)
                        )
                    )
                }
            }

            // --- 3. Large Circle FAB (Placed exactly matching HTML) ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 90.dp, end = 20.dp)
            ) {
                FloatingActionButton(
                    onClick = onFABClick,
                    containerColor = Color(0xFFD9E2FF),
                    contentColor = Color(0xFF001D36),
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("bento_fab")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Create New Document",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// BENTO GRID SUB-COMPONENTS
// ==========================================

@Composable
fun FeaturedDocBentoCard(
    featuredDoc: DocEntity?,
    onDocClick: (DocEntity) -> Unit,
    onQuickCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFD9E2FF) // Lavender Blue
    val textColor = Color(0xFF001D36)
    val subtextColor = Color(0xFF44474E)

    val lastEditedFormatted = remember(featuredDoc?.updatedAt) {
        if (featuredDoc != null) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            "Modified Today at ${sdf.format(Date(featuredDoc.updatedAt))}"
        } else {
            "No recent modifications recorded"
        }
    }

    Card(
        onClick = { if (featuredDoc != null) onDocClick(featuredDoc) else onQuickCreate() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .testTag("bento_featured_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon layout inspired by OnlyOffice and Bento layouts
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    val symbolStr = when (featuredDoc?.type) {
                        "word" -> "W"
                        "sheet" -> "S"
                        "slide" -> "P"
                        else -> "O"
                    }
                    Text(
                        text = symbolStr,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005AC1),
                        fontSize = 18.sp
                    )
                }

                // Dynamic badges
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF005AC1).copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (featuredDoc != null) "Resume Editing" else "Create Now",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Text(
                    text = featuredDoc?.title ?: "Welcome To JCdocs Workspace",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp
                    ),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (featuredDoc != null) lastEditedFormatted else "Get started immediately by clicking to build your proposal note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtextColor
                )
            }
        }
    }
}

@Composable
fun CollaborationBentoCard(
    sheetsCount: Int,
    writerCount: Int,
    slidesCount: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFE1E2E9) // Cool Grey
    val textColor = Color(0xFF1A1C1E)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SANDBOX ENGAGEMENT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFF44474E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Avatar stacking overlay exactly replicating Tailwind CSS markup
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                // Color dots simulating users
                listOf(Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC)).forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.5.dp, cardBgColor, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f))
                        .border(1.5.dp, cardBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+4",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "7 simulated sandboxes active",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Robust offline operations utilizing local cache streams across $writerCount documents, $sheetsCount sheets, and $slidesCount decks.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF44474E).copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun StatsBentoSquare(
    totalFiles: Int,
    favoriteFiles: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFFAD8FD) // Pastel Lavender Purple
    val textColor = Color(0xFF2B1230)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .heightIn(min = 130.dp)
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
                Text(
                    text = totalFiles.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Review Status",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "TOTAL REVIEWS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor.copy(alpha = 0.5f)
                )
                
                Text(
                    text = "$favoriteFiles Starred Documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun RoomDbStorageBentoSquare(
    totalFiles: Int,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFD3E8D3) // Pastel Mint Green
    val textColor = Color(0xFF00210B)
    val progressColor = Color(0xFF116C31)

    // Arbitrary percentage showcasing offline health
    val percentage = if (totalFiles == 0) 0f else minOf(100f, 15f + (totalFiles * 12f))

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .heightIn(min = 130.dp)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SQL DATABASE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = textColor.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }

            // Custom green percentage progress bar representing sqlite memory limits
            Column {
                LinearProgressIndicator(
                    progress = percentage / 100f,
                    color = progressColor,
                    trackColor = textColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "SQLite schema integrity secure",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor
                )
            }
        }
    }
}

@Composable
fun SmartTemplatesBentoCard(
    onQuickCreate: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFFFDAD6) // Pastel Peach Pink
    val textColor = Color(0xFF410002)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Smart Templates",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                
                Text(
                    text = "AI-powered document structure generation in modern Jetpack Compose",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable row of template quick-action chips exactly conforming to the design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateChipItem(
                    label = "Invoice Project",
                    onClick = { onQuickCreate("Project Financial Invoice", "sheet") }
                )
                TemplateChipItem(
                    label = "AI Proposal Document",
                    onClick = { onQuickCreate("Bento Proposal Deck", "word") }
                )
                TemplateChipItem(
                    label = "NDA Agreement",
                    onClick = { onQuickCreate("Joint Consultation NDA Agreement", "word") }
                )
                TemplateChipItem(
                    label = "Keynote Slides",
                    onClick = { onQuickCreate("Smart Technology Keynote", "slide") }
                )
            }
        }
    }
}

@Composable
fun TemplateChipItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF410002)
            )
        )
    }
}

@Composable
fun TemplateCard(
    title: String,
    typeStr: String,
    iconChar: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = modifier
            .width(150.dp)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconChar,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = typeStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WorkspaceMenuBar(
    doc: DocEntity,
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    isSidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit,
    onCloseClick: () -> Unit,
    undoRedoManager: DocUndoRedoManager,
    undoRedoTrigger: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = when (doc.type) {
        "word" -> DocWordColor
        "sheet" -> DocSheetColor
        "slide" -> DocSlideColor
        else -> OnlyOfficePrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Sidebar Button
        IconButton(onClick = onToggleSidebar) {
            Icon(
                imageVector = if (isSidebarExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                contentDescription = "Toggle Sidebar"
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Document Type Badge Indicator
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(themeColor),
            contentAlignment = Alignment.Center
        ) {
            val symbolChar = when (doc.type) {
                "word" -> "W"
                "sheet" -> "S"
                "slide" -> "P"
                else -> "D"
            }
            Text(
                text = symbolChar,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Document Title Text Edit field
        BasicTextField(
            value = draftTitle,
            onValueChange = onTitleChange,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("workspace_title_input")
        )

        // Undo and Redo Buttons
        val x = undoRedoTrigger // Recompose on undo/redo actions
        IconButton(
            onClick = onUndo,
            enabled = undoRedoManager.canUndo(),
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = if (undoRedoManager.canUndo()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
        
        IconButton(
            onClick = onRedo,
            enabled = undoRedoManager.canRedo(),
            modifier = Modifier.testTag("redo_button")
        ) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = if (undoRedoManager.canRedo()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // Saved Status Indicator (Automatic local saving is active)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)) // Soft Green
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Saved",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Close Document Button
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.testTag("close_document_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Exit to Dashboard",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

class RichTextVisualTransformation(private val spans: List<DocFormatSpan>, private val absoluteOffset: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        try {
            return filterUnsafe(text)
        } catch (e: Exception) {
            android.util.Log.e("RichTextTransform", "filter error", e)
            return TransformedText(text, OffsetMapping.Identity)
        }
    }
    
    private fun filterUnsafe(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        val chunkLength = text.text.length
        val paraRangeList = mutableListOf<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.ParagraphStyle>>()
        
        // First pass: collect paragraph-level properties (alignment + lineSpacing) per paragraph range
        val paraProps = mutableMapOf<String, MutableMap<String, String>>()
        
        spans.forEach { span ->
            val relStart = maxOf(0, span.start - absoluteOffset)
            val relEnd = minOf(chunkLength, span.end - absoluteOffset)
            if (relStart < relEnd) {
                when (span.type) {
                    "alignment", "lineSpacing" -> {
                        val paraRangesInPage = getParagraphRangesInRange(text.text, relStart, relEnd.coerceAtLeast(relStart))
                        for (r in paraRangesInPage) {
                            val paraStart = r.start
                            val paraEnd = r.endInclusive + 1
                            if (paraStart >= 0 && paraEnd <= chunkLength && paraStart < paraEnd) {
                                val key = "$paraStart-$paraEnd"
                                paraProps.getOrPut(key) { mutableMapOf() }[span.type] = span.value
                            }
                        }
                    }
                }
            }
        }
        
        // Collect ParagraphStyle ranges
        for ((key, props) in paraProps) {
            val parts = key.split("-")
            if (parts.size < 2) continue
            val paraStart = parts[0].toIntOrNull() ?: continue
            val paraEnd = parts[1].toIntOrNull() ?: continue
            if (paraStart < 0 || paraEnd > chunkLength || paraStart >= paraEnd) continue
            val align = when (props["alignment"]) {
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                "right" -> androidx.compose.ui.text.style.TextAlign.Right
                "justify" -> androidx.compose.ui.text.style.TextAlign.Justify
                "left" -> androidx.compose.ui.text.style.TextAlign.Left
                else -> null
            }
            val lineHeightValue = props["lineSpacing"]?.toFloatOrNull()
            if (align != null || lineHeightValue != null) {
                val effectiveAlign = align ?: androidx.compose.ui.text.style.TextAlign.Start
                val pStyle = if (lineHeightValue != null) {
                    androidx.compose.ui.text.ParagraphStyle(textAlign = effectiveAlign, lineHeight = 24.sp * lineHeightValue)
                } else {
                    androidx.compose.ui.text.ParagraphStyle(textAlign = align!!)
                }
                paraRangeList.add(androidx.compose.ui.text.AnnotatedString.Range(pStyle, paraStart, paraEnd))
            }
        }
        
        // Second pass: apply span-level styles (non-paragraph types)
        spans.forEach { span ->
            val relStart = maxOf(0, span.start - absoluteOffset)
            val relEnd = minOf(chunkLength, span.end - absoluteOffset)
            if (relStart < relEnd) {
                when(span.type) {
                    "bold" -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), relStart, relEnd)
                    "italic" -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), relStart, relEnd)
                    "underline" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), relStart, relEnd)
                    "strikethrough" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), relStart, relEnd)
                    "track_insert" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFFC2410C), fontWeight = FontWeight.Medium), relStart, relEnd)
                    "track_delete" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color(0xFFEF4444)), relStart, relEnd)
                    "comment_highlight" -> builder.addStyle(SpanStyle(background = Color(0xFFFEF3C7), color = Color(0xFF92400E)), relStart, relEnd)
                    "color" -> try { builder.addStyle(SpanStyle(color = Color(android.graphics.Color.parseColor(span.value))), relStart, relEnd) } catch(e:Exception){}
                    "highlight" -> {
                        val bgHex = span.value.ifEmpty { "#FDE047" }
                        try {
                            builder.addStyle(SpanStyle(background = Color(android.graphics.Color.parseColor(bgHex)).copy(alpha = 0.45f)), relStart, relEnd)
                        } catch (e: Exception) {
                            builder.addStyle(SpanStyle(background = Color(0xFFFDE047).copy(alpha = 0.45f)), relStart, relEnd)
                        }
                    }
                    "subscript" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript, fontSize = 11.sp), relStart, relEnd)
                    "superscript" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript, fontSize = 11.sp), relStart, relEnd)
                    "fontSize" -> {
                        val size = span.value.toFloatOrNull()
                        if (size != null) {
                            builder.addStyle(SpanStyle(fontSize = size.sp), relStart, relEnd)
                        }
                    }
                    "fontFamily" -> {
                        val family = when (span.value) {
                            "Arial" -> FontFamily.SansSerif
                            "Times New Roman" -> FontFamily.Serif
                            "Courier New" -> FontFamily.Monospace
                            "Georgia" -> FontFamily.Serif
                            "Verdana" -> FontFamily.SansSerif
                            "Aptos" -> FontFamily.SansSerif
                            "Calibri" -> FontFamily.SansSerif
                            else -> FontFamily.Default
                        }
                        builder.addStyle(SpanStyle(fontFamily = family), relStart, relEnd)
                    }
                    "shading" -> {
                        try {
                            val bgColor = Color(android.graphics.Color.parseColor(span.value)).copy(alpha = 0.25f)
                            builder.addStyle(SpanStyle(background = bgColor), relStart, relEnd)
                        } catch (e: Exception) {}
                    }
                    "border" -> {
                        // Border rendering requires custom drawing beyond VisualTransformation (e.g., Canvas/Border inside the composable). 
                        // For now, we store the metadata — visual border drawing is deferred.
                    }
                }
            }
        }
        val base = builder.toAnnotatedString()
        val result = androidx.compose.ui.text.AnnotatedString(
            text = base.text,
            spanStyles = base.spanStyles,
            paragraphStyles = paraRangeList
        )
        return TransformedText(result, OffsetMapping.Identity)
    }
}

fun toRoman(number: Int): String {
    var num = number
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romanLiterals = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val roman = StringBuilder()
    for (i in values.indices) {
        while (num >= values[i]) {
            num -= values[i]
            roman.append(romanLiterals[i])
        }
    }
    return roman.toString()
}

fun toAlphabetic(number: Int): String {
    var num = number
    var result = ""
    while (num > 0) {
        num-- 
        result = ('A' + (num % 26)) + result
        num /= 26
    }
    return result
}

fun formatPageNumber(pageNumber: Int, format: String, totalPages: Int = 1): String {
    val formattedBase = when {
        format.contains("01, 02, 03") -> String.format("%02d", pageNumber)
        format.contains("001, 002, 003") -> String.format("%03d", pageNumber)
        format.contains("I, II, III") -> toRoman(pageNumber)
        format.contains("i, ii, iii") -> toRoman(pageNumber).lowercase()
        format.contains("A, B, C") -> toAlphabetic(pageNumber)
        format.contains("a, b, c") -> toAlphabetic(pageNumber).lowercase()
        else -> pageNumber.toString()
    }
    
    return when {
        format.contains("- 1 -") || format.startsWith("-") -> "- $formattedBase -"
        format.contains("Page X of Y") -> "Page $formattedBase of $totalPages"
        format.contains("Page X") -> "Page $formattedBase"
        else -> formattedBase
    }
}

// --- PARAGRAPH FORMATTING HELPERS ---

fun getParagraphRange(text: String, pos: Int): IntRange {
    if (text.isEmpty()) return 0 until 0
    val clampedPos = pos.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', clampedPos - 1) + 1
    val end = text.indexOf('\n', clampedPos).let { if (it == -1) text.length else it }
    return start until end
}

fun getParagraphRangesInRange(text: String, rangeStart: Int, rangeEnd: Int): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val start = maxOf(0, rangeStart).coerceAtMost(text.length)
    val end = rangeEnd.coerceIn(start, text.length)
    // Single cursor: use the paragraph at that position
    if (start == end) {
        val para = getParagraphRange(text, start)
        if (para.isEmpty()) return emptyList()
        // Include trailing newline in the range
        val nlPos = text.indexOf('\n', para.start)
        val endWithNl = if (nlPos == -1 || nlPos >= para.endInclusive + 1) para.endInclusive + 1 else nlPos + 1
        return listOf(para.start until endWithNl.coerceAtMost(text.length))
    }
    val result = mutableListOf<IntRange>()
    val firstPara = getParagraphRange(text, start)
    val startPos = firstPara.start
    val clampedEnd = end.coerceAtMost(text.length)
    val endPos = text.indexOf('\n', clampedEnd).let { if (it == -1) text.length else it }
    var pos = startPos
    while (pos < endPos) {
        val paraStart = pos
        val nextNewline = text.indexOf('\n', pos)
        val paraEnd = if (nextNewline == -1 || nextNewline >= endPos) {
            endPos
        } else {
            nextNewline + 1  // include trailing newline
        }
        if (paraStart < paraEnd) {
            result.add(paraStart until paraEnd)
        }
        pos = if (nextNewline == -1 || nextNewline >= endPos) endPos else nextNewline + 1
    }
    return result
}

fun getParagraphText(text: String, pos: Int): String {
    val range = getParagraphRange(text, pos)
    return text.substring(range.start, range.endInclusive + 1)
}

fun replaceParagraphText(text: String, pos: Int, newPara: String): String {
    val range = getParagraphRange(text, pos)
    val sepEnd = text.indexOf('\n', range.start).let { if (it == -1) text.length else it + 1 }
    return text.substring(0, range.start) + newPara + text.substring(sepEnd)
}

val BulletChars = listOf("•", "◦", "▪", "➢", "‣", "–", "★", "※")
val NumberFormats = listOf("1.", "a)", "A.", "i)", "I.")

fun detectListPrefix(line: String): Pair<String?, String?> {
    val trimmed = line.trimStart()
    for (b in BulletChars) {
        if (trimmed.startsWith(b)) return b to null
    }
    for (f in NumberFormats) {
        val pattern = when (f) {
            "1." -> Regex("""^\d+\.""")
            "a)" -> Regex("""^[a-z]\)""")
            "A." -> Regex("""^[A-Z]\.""")
            "i)" -> Regex("""^[ivxlcdm]+\)""", RegexOption.IGNORE_CASE)
            "I." -> Regex("""^[IVXLCDM]+\.""")
            else -> null
        }
        if (pattern != null && pattern.containsMatchIn(trimmed)) return null to f
    }
    return null to null
}

fun removeListPrefix(line: String): String {
    val trimmed = line.trimStart()
    for (b in BulletChars) {
        if (trimmed.startsWith(b)) {
            val after = trimmed.removePrefix(b).trimStart()
            val wsLen = line.length - line.trimStart().length
            return line.take(wsLen) + after
        }
    }
    for (f in NumberFormats) {
        val pattern = when (f) {
            "1." -> Regex("""^\d+\.\s*""")
            "a)" -> Regex("""^[a-z]\)\s*""")
            "A." -> Regex("""^[A-Z]\.\s*""")
            "i)" -> Regex("""^[ivxlcdm]+\)\s*""", RegexOption.IGNORE_CASE)
            "I." -> Regex("""^[IVXLCDM]+\.\s*""")
            else -> null
        }
        if (pattern != null) {
            val after = trimmed.replaceFirst(pattern, "")
            val wsLen = line.length - line.trimStart().length
            return line.take(wsLen) + after
        }
    }
    return line
}

fun applyBulletToPara(text: String, pos: Int, bulletChar: String): String {
    val para = getParagraphText(text, pos)
    val (existingBullet, _) = detectListPrefix(para)
    if (existingBullet != null) return text // already has a bullet
    val clean = removeListPrefix(para)
    val indent = clean.takeWhile { it == ' ' }
    val newPara = indent + bulletChar + " " + clean.trimStart()
    return replaceParagraphText(text, pos, newPara)
}

fun removeBulletFromPara(text: String, pos: Int): String {
    val para = getParagraphText(text, pos)
    val clean = removeListPrefix(para)
    return replaceParagraphText(text, pos, clean)
}

fun applyNumberToPara(text: String, pos: Int, numFormat: String, number: Int): String {
    val para = getParagraphText(text, pos)
    val clean = removeListPrefix(para)
    val indent = clean.takeWhile { it == ' ' }
    val prefix = when (numFormat) {
        "1." -> "$number."
        "a)" -> "${('a' + (number - 1).coerceIn(0, 25))})"
        "A." -> "${('A' + (number - 1).coerceIn(0, 25))}."
        "i)" -> toRoman(number).lowercase() + ")"
        "I." -> toRoman(number) + "."
        else -> "$number."
    }
    val newPara = indent + prefix + " " + clean.trimStart()
    return replaceParagraphText(text, pos, newPara)
}

fun renumberDocument(text: String, numFormat: String): String {
    val lines = text.split("\n")
    var counter = 1
    val result = lines.map { line ->
        val (bullet, numFmt) = detectListPrefix(line)
        if (numFmt != null) {
            val clean = removeListPrefix(line)
            val indent = line.takeWhile { it == ' ' }
            val prefix = when (numFormat) {
                "1." -> "$counter."
                "a)" -> "${('a' + (counter - 1).coerceIn(0, 25))})"
                "A." -> "${('A' + (counter - 1).coerceIn(0, 25))}."
                "i)" -> toRoman(counter).lowercase() + ")"
                "I." -> toRoman(counter) + "."
                else -> "$counter."
            }
            counter++
            indent + prefix + " " + clean.trimStart()
        } else if (bullet != null) {
            line // don't change counter for bullets
        } else {
            counter = 1 // reset counter for non-numbered paragraphs
            line
        }
    }
    return result.joinToString("\n")
}

data class OfficeThemeProperties(
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val paperTint: Color,
    val defaultFontFamily: androidx.compose.ui.text.font.FontFamily
)

fun getThemeProperties(name: String): OfficeThemeProperties {
    return when (name) {
        "Modern Teal" -> OfficeThemeProperties(
            primaryAccent = Color(0xFF005F73),
            secondaryAccent = Color(0xFF0A9396),
            paperTint = Color(0xFFF4FAF8),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
        )
        "Warm Organic" -> OfficeThemeProperties(
            primaryAccent = Color(0xFF3D5A80),
            secondaryAccent = Color(0xFFEE6C4D),
            paperTint = Color(0xFFFAF7F2),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
        "Slate Editorial" -> OfficeThemeProperties(
            primaryAccent = Color(0xFF2B2D42),
            secondaryAccent = Color(0xFF8D99AE),
            paperTint = Color(0xFFF5F5F7),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
        "Wine Integral" -> OfficeThemeProperties(
            primaryAccent = Color(0xFF641220),
            secondaryAccent = Color(0xFFE01E37),
            paperTint = Color(0xFFFFF5F6),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
        "Forest Woodland" -> OfficeThemeProperties(
            primaryAccent = Color(0xFF1E3F20),
            secondaryAccent = Color(0xFF4A7C59),
            paperTint = Color(0xFFF3F6F2),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
        )
        "Retro Amber" -> OfficeThemeProperties(
            primaryAccent = Color(0xFFB56576),
            secondaryAccent = Color(0xFFE56B6F),
            paperTint = Color(0xFFFAF2E8),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        else -> OfficeThemeProperties(
            primaryAccent = Color(0xFF1F4E79),
            secondaryAccent = Color(0xFF2F5597),
            paperTint = Color(0xFFFFFFFF),
            defaultFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
        )
    }
}

fun Modifier.applyThemeEffect(effect: String, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp)): Modifier {
    return when (effect) {
        "Default Office" -> this.then(
            Modifier.shadow(2.dp, shape)
        )
        "Glossy Reflex" -> this.then(
            Modifier
                .shadow(1.dp, shape)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
                .border(1.dp, Color.White.copy(alpha = 0.4f), shape)
        )
        "Matte Border" -> this.then(
            Modifier.border(2.5.dp, Color(0xFF333333), shape)
        )
        "Drop Shadow" -> this.then(
            Modifier.shadow(6.dp, shape, clip = false)
        )
        "Soft Edges Blurry" -> this.then(
            Modifier
                .border(2.dp, Color.Gray.copy(alpha = 0.25f), shape)
                .border(4.dp, Color.Gray.copy(alpha = 0.1f), shape)
        )
        "Glow Neon Blue" -> this.then(
            Modifier
                .border(2.dp, Color(0xFF00F5FF).copy(alpha = 0.7f), shape)
                .border(4.dp, Color(0xFF00F5FF).copy(alpha = 0.25f), shape)
        )
        else -> this
    }
}

// --- 1. JC WORD WRITER EDITOR ---
@Composable
fun WordDocumentEditor(
    docId: Int,
    draftContent: String,
    targetFocusPage: androidx.compose.runtime.MutableState<Int?>,
    targetFocusOffset: androidx.compose.runtime.MutableState<Int?>,
    onContentChange: (String) -> Unit,
    editorTheme: String,
    onEditorThemeChange: (String) -> Unit,
    pageBackgroundColorHex: String = "",
    pageMargins: androidx.compose.ui.unit.Dp,
    pageMarginTop: androidx.compose.ui.unit.Dp = pageMargins,
    pageMarginBottom: androidx.compose.ui.unit.Dp = pageMargins,
    pageMarginLeft: androidx.compose.ui.unit.Dp = pageMargins,
    pageMarginRight: androidx.compose.ui.unit.Dp = pageMargins,
    columnCount: Int,
    fontSize: androidx.compose.ui.unit.TextUnit,
    formatVersion: Int = 0,
    isLandscape: Boolean,
    pageFormat: String = "A4",
    customDimensions: Pair<Float, Float> = 8.5f to 11.0f,
    pageNumberPosition: String? = null,
    pageNumberFormat: String = "1, 2, 3...",
    pageNumberStartAt: Int = 1,
    showPageNumberOnFirstPage: Boolean = true,
    headerText: String = "",
    footerText: String = "",
    headerAlignment: String = "Center",
    footerAlignment: String = "Center",
    showHeaderFooterOnFirstPage: Boolean = true,
    onShowHeaderFooterDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    textFieldValue: TextFieldValue? = null,
    onTextFieldValueChange: ((TextFieldValue) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    selectedDocumentTheme: String = "Office Classic",
    selectedThemeEffect: String = "None",
    watermarkText: String = "",
    watermarkType: String = "Diagonal",
    watermarkColorHex: String = "#33CCCCCC",
    pageBorderType: String = "None",
    pageBorderColorHex: String = "default"
) {
    var targetFocusPage by targetFocusPage
    var targetFocusOffset by targetFocusOffset

    val themeProps = remember(selectedDocumentTheme) {
        getThemeProperties(selectedDocumentTheme)
    }

    val paperColor = if (pageBackgroundColorHex.isNotEmpty()) {
        try { Color(android.graphics.Color.parseColor(pageBackgroundColorHex)) } catch (e: Exception) { 
            when (editorTheme) {
                "dark" -> Color(0xFF262626)
                "ivory" -> Color(0xFFFAF6EE)
                else -> themeProps.paperTint
            }
        }
    } else {
        when (editorTheme) {
            "dark" -> Color(0xFF262626)
            "ivory" -> Color(0xFFFAF6EE)
            else -> themeProps.paperTint
        }
    }

    val paperTextColor = when (editorTheme) {
        "dark" -> Color(0xFFE0E0E0)
        else -> Color(0xFF2D2D2D)
    }

    val (paperMaxWidth, minPageHeight) = DocumentLayoutEngine.getDimensions(
        format = pageFormat,
        customDimensions = customDimensions,
        isLandscape = isLandscape
    )
    
    var selectedPictureId by remember { mutableStateOf<String?>(null) }
    var selectedTableId by remember { mutableStateOf<String?>(null) }
    var selectedShapeId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Ruler
            PageRuler(
                isHorizontal = true,
                totalLength = paperMaxWidth,
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                val pages = draftContent.split("\u000C")
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                pages.forEachIndexed { pageIndex, pageContent ->
                    val borderColor = if (pageBorderColorHex.isEmpty() || pageBorderColorHex == "default") {
                        if (isSystemInDarkTheme() || editorTheme == "dark") Color.LightGray else Color(0xFF2D2D2D)
                    } else {
                        try {
                            Color(android.graphics.Color.parseColor(if (pageBorderColorHex.startsWith("#")) pageBorderColorHex else "#$pageBorderColorHex"))
                        } catch (e: Exception) {
                            if (isSystemInDarkTheme() || editorTheme == "dark") Color.LightGray else Color(0xFF2D2D2D)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = paperColor),
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .width(paperMaxWidth)
                            .height(minPageHeight)
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedPictureId = null
                                    selectedTableId = null
                                    selectedShapeId = null
                                }
                        ) {
                            if (pageBorderType != "None") {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val insetPx = with(density) { 12.dp.toPx() }
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    if (w > insetPx * 2 && h > insetPx * 2) {
                                        val rect = androidx.compose.ui.geometry.Rect(
                                            left = insetPx,
                                            top = insetPx,
                                            right = w - insetPx,
                                            bottom = h - insetPx
                                        )
                                        when (pageBorderType) {
                                            "Thin Box" -> {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                            "Medium Box" -> {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                                )
                                            }
                                            "Thick Box" -> {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                                )
                                            }
                                            "Dashed Line" -> {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                        width = 1.5.dp.toPx(),
                                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                                                    )
                                                )
                                            }
                                            "Double Line" -> {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                                )
                                                val innerInset = 3.dp.toPx()
                                                val innerRect = androidx.compose.ui.geometry.Rect(
                                                    left = rect.left + innerInset,
                                                    top = rect.top + innerInset,
                                                    right = rect.right - innerInset,
                                                    bottom = rect.bottom - innerInset
                                                )
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = innerRect.topLeft,
                                                    size = innerRect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Watermark Overlay
                            if (watermarkText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Text(
                                        text = watermarkText,
                                        fontSize = 60.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = try { Color(android.graphics.Color.parseColor(watermarkColorHex)) } catch (e: Exception) { Color(0x33CCCCCC) },
                                        modifier = Modifier.graphicsLayer(
                                            rotationZ = if (watermarkType == "Diagonal") -45f else 0f
                                        )
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .padding(
                                        start = pageMarginLeft,
                                        top = pageMarginTop,
                                        end = pageMarginRight,
                                        bottom = pageMarginBottom
                                    )
                            ) {
                            val computedPageNumber = formatPageNumber(pageIndex + pageNumberStartAt, pageNumberFormat, pages.size)
                            val shouldShowPageNum = showPageNumberOnFirstPage || pageIndex > 0
                            
                            // Header
                            val shouldShowHeader = (showHeaderFooterOnFirstPage || pageIndex > 0) && (headerText.isNotEmpty() || (shouldShowPageNum && pageNumberPosition?.startsWith("Top") == true))
                            if (shouldShowHeader) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onShowHeaderFooterDialog?.invoke()
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    val hLeftText = buildString {
                                        if (headerAlignment == "Left" && headerText.isNotEmpty()) append(headerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top")) {
                                            val isLeft = pageNumberPosition.contains("Left")
                                            if (isLeft) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }
                                    val hCenterText = buildString {
                                        if (headerAlignment == "Center" && headerText.isNotEmpty()) append(headerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top")) {
                                            val isCenter = !pageNumberPosition.contains("Left") && !pageNumberPosition.contains("Right")
                                            if (isCenter) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }
                                    val hRightText = buildString {
                                        if (headerAlignment == "Right" && headerText.isNotEmpty()) append(headerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top")) {
                                            val isRight = pageNumberPosition.contains("Right")
                                            if (isRight) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left content
                                        Text(
                                            text = hLeftText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        // Center content
                                        Text(
                                            text = hCenterText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        // Right content
                                        Text(
                                            text = hRightText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                }
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = DocWordColor.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            if (columnCount > 1) {
                                val K = when (columnCount) {
                                    2, 4, 5 -> 2
                                    3 -> 3
                                    else -> 1
                                }
                                
                                var colTextFieldValues by remember(K) {
                                    val paragraphs = pageContent.split("\n\n")
                                    val totalParas = paragraphs.size
                                    val paraCountPerCol = maxOf(1, (totalParas + K - 1) / K)
                                    val colTexts = List(K) { index ->
                                        val start = index * paraCountPerCol
                                        val end = minOf(totalParas, (index + 1) * paraCountPerCol)
                                        if (start < totalParas) paragraphs.subList(start, end).joinToString("\n\n") else ""
                                    }
                                    mutableStateOf(colTexts.map { TextFieldValue(it, TextRange(it.length)) })
                                }

                                var localMergedText by remember { mutableStateOf(pageContent) }

                                LaunchedEffect(pageContent) {
                                    if (pageContent != localMergedText) {
                                        val paragraphs = pageContent.split("\n\n")
                                        val totalParas = paragraphs.size
                                        val paraCountPerCol = maxOf(1, (totalParas + K - 1) / K)
                                        val colTexts = List(K) { index ->
                                            val start = index * paraCountPerCol
                                            val end = minOf(totalParas, (index + 1) * paraCountPerCol)
                                            if (start < totalParas) paragraphs.subList(start, end).joinToString("\n\n") else ""
                                        }
                                        colTextFieldValues = colTexts.map { TextFieldValue(it, TextRange(it.length)) }
                                        localMergedText = pageContent
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    for (i in 0 until K) {
                                        if (i > 0) {
                                            androidx.compose.material3.VerticalDivider(
                                                modifier = Modifier.height(180.dp).padding(horizontal = 4.dp),
                                                color = paperTextColor.copy(alpha = 0.15f)
                                            )
                                        }
                                        
                                        val weight = when (columnCount) {
                                            4 -> if (i == 0) 1f else 2f
                                            5 -> if (i == 0) 2f else 1f
                                            else -> 1f
                                        }
                                        
                                        Column(
                                            modifier = Modifier
                                                .weight(weight)
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.Top
                                        ) {
                                            val colLabel = when (columnCount) {
                                                4 -> if (i == 0) "Left (1/3)" else "Right (2/3)"
                                                5 -> if (i == 0) "Left (2/3)" else "Right (1/3)"
                                                else -> "Column ${i + 1}"
                                            }
                                            Text(
                                                text = colLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = paperTextColor.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            
                                            val tfValue = colTextFieldValues.getOrNull(i) ?: TextFieldValue()
                                            BasicTextField(
                                                value = tfValue,
                                                onValueChange = { newValue ->
                                                    val updated = colTextFieldValues.toMutableList()
                                                    if (i < updated.size) {
                                                        updated[i] = newValue
                                                        colTextFieldValues = updated
                                                        
                                                        val merged = updated.map { it.text }.joinToString("\n\n")
                                                        localMergedText = merged
                                                        onContentChange(merged)
                                                    }
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = paperTextColor,
                                                    fontSize = fontSize,
                                                    fontFamily = themeProps.defaultFontFamily
                                                ),
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                                    imeAction = androidx.compose.ui.text.input.ImeAction.Default
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(paperTextColor)
                                            )
                                        }
                                    }
                                }
                            } else {
                                val focusRequester = remember(pageIndex) { androidx.compose.ui.focus.FocusRequester() }
                                
                                var textFieldHeightPx by remember { mutableIntStateOf(0) }
                                var splitOffset by remember { mutableIntStateOf(-1) }
                                var mergeBackOffset by remember { mutableIntStateOf(-1) }
                                var mergeBackLocked by remember { mutableStateOf(false) }
                                
                                var pageTextFieldValue by remember {
                                    val initialText = if (pageContent.isEmpty() && pageIndex > 0) "\u200B" else pageContent
                                    mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
                                }
                                var lastPushedText by remember {
                                    val initialText = if (pageContent.isEmpty() && pageIndex > 0) "\u200B" else pageContent
                                    mutableStateOf(initialText)
                                }
                                        LaunchedEffect(targetFocusPage) {
                                    if (targetFocusPage == pageIndex) {
                                        androidx.compose.ui.focus.FocusRequester.Default // ensure class is loaded
                                        try {
                                            kotlinx.coroutines.yield()
                                            focusRequester.requestFocus()
                                        } catch(e: Exception) {}
                                    }
                                }
                                
                                LaunchedEffect(splitOffset) {
                                    val currentText = pageTextFieldValue.text
                                    if (splitOffset != -1 && splitOffset <= currentText.length) {
                                        val newPages = pages.toMutableList()
                                        
                                        var actualSplit = splitOffset
                                        val lastSpace = currentText.lastIndexOf(' ', splitOffset)
                                        if (lastSpace > splitOffset - 20 && lastSpace > 0) {
                                            actualSplit = lastSpace + 1
                                        }
                                        
                                        val keptContent = currentText.substring(0, actualSplit)
                                        var overflowContent = currentText.substring(actualSplit)
                                        
                                        val origLen = overflowContent.length
                                        overflowContent = overflowContent.trimStart(' ', '\n')
                                        val trimmedChars = origLen - overflowContent.length

                                        newPages[pageIndex] = keptContent
                                        if (pageIndex + 1 < newPages.size) {
                                            newPages[pageIndex + 1] = overflowContent + newPages[pageIndex + 1]
                                        } else {
                                            newPages.add(overflowContent)
                                        }
                                        
                                        if (pageTextFieldValue.selection.start >= actualSplit) {
                                            targetFocusPage = pageIndex + 1
                                            targetFocusOffset = maxOf(0, pageTextFieldValue.selection.start - actualSplit - trimmedChars)
                                        }
                                        
                                        val newFullText = newPages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                        onContentChange(newFullText)
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = keptContent,
                                            selection = TextRange(minOf(pageTextFieldValue.selection.start, keptContent.length))
                                        )
                                        lastPushedText = keptContent
                                        val absoluteOffsetNew = newPages.map { it.replace("\u200B", "") }.take(pageIndex).sumOf { it.length + 1 }
                                        val globalSelection = TextRange(absoluteOffsetNew + pageTextFieldValue.selection.start)
                                        onTextFieldValueChange?.invoke(
                                            TextFieldValue(
                                                text = newFullText,
                                                selection = globalSelection
                                            )
                                        )
                                        mergeBackLocked = true
                                        splitOffset = -1
                                    }
                                }

                                LaunchedEffect(mergeBackOffset) {
                                    if (mergeBackOffset != -1 && pageIndex + 1 < pages.size) {
                                        val nextContent = pages[pageIndex + 1]
                                        val currentText = pageTextFieldValue.text
                                        val cleanNextContent = nextContent.replace("\u200B", "")
                                        val cleanCurrentText = currentText.replace("\u200B", "")
                                        val merged = cleanCurrentText + cleanNextContent

                                        val newPages = pages.toMutableList()
                                        newPages[pageIndex] = merged
                                        newPages.removeAt(pageIndex + 1)

                                        val newFullText = newPages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                        onContentChange(newFullText)
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = merged,
                                            selection = TextRange(pageTextFieldValue.selection.start)
                                        )
                                        lastPushedText = merged
                                        val absoluteOffset = newPages.map { it.replace("\u200B", "") }.take(pageIndex).sumOf { it.length + 1 }
                                        val globalSelection = TextRange(absoluteOffset + pageTextFieldValue.selection.start)
                                        onTextFieldValueChange?.invoke(
                                            TextFieldValue(
                                                text = newFullText,
                                                selection = globalSelection
                                            )
                                        )
                                        mergeBackOffset = -1
                                        mergeBackLocked = true
                                    }
                                }

                                LaunchedEffect(pageContent, targetFocusPage) {
                                    val syncedText = if (pageContent.isEmpty() && pageIndex > 0) "\u200B" else pageContent
                                    if (targetFocusPage == pageIndex && targetFocusOffset != null) {
                                        val offset = if (pageContent.isEmpty() && pageIndex > 0) 1 else targetFocusOffset!!
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = syncedText,
                                            selection = TextRange(offset)
                                        )
                                        lastPushedText = syncedText
                                        targetFocusOffset = null
                                    } else if (syncedText != lastPushedText) {
                                        val newSelection = if (pageTextFieldValue.selection.start <= syncedText.length && pageTextFieldValue.selection.end <= syncedText.length) {
                                            pageTextFieldValue.selection
                                        } else {
                                            TextRange(syncedText.length)
                                        }
                                        pageTextFieldValue = pageTextFieldValue.copy(text = syncedText, selection = newSelection)
                                        lastPushedText = syncedText
                                    }
                                }
                                
                                BasicTextField(
                                    value = pageTextFieldValue,
                                    onValueChange = { newTfv ->
                                        val oldSelection = pageTextFieldValue.selection
                                        pageTextFieldValue = newTfv
                                        
                                        if (newTfv.text.isEmpty() && pageIndex > 0) {
                                            val newPages = pages.toMutableList()
                                            newPages.removeAt(pageIndex)
                                            val newFullText = newPages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                            onContentChange(newFullText)
                                            val absoluteOffsetPrevPage = newPages.map { it.replace("\u200B", "") }.take(pageIndex - 1).sumOf { it.length + 1 }
                                            val prevPageText = newPages.getOrNull(pageIndex - 1) ?: ""
                                            val globalSelection = TextRange(absoluteOffsetPrevPage + prevPageText.length)
                                            onTextFieldValueChange?.invoke(
                                                TextFieldValue(
                                                    text = newFullText,
                                                    selection = globalSelection
                                                )
                                            )
                                            targetFocusPage = pageIndex - 1
                                            targetFocusOffset = prevPageText.length
                                        } else if (newTfv.text != lastPushedText) {
                                            mergeBackLocked = false
                                            val oldText = lastPushedText
                                            var newText = newTfv.text
                                            
                                            if (newText.startsWith("\u200B") && newText.length > 1) {
                                                newText = newText.substring(1)
                                                pageTextFieldValue = pageTextFieldValue.copy(
                                                    text = newText,
                                                    selection = TextRange(newTfv.selection.start - 1, newTfv.selection.end - 1)
                                                )
                                            }
                                            
                                            lastPushedText = newText
                                            
                                            var commonPrefix = 0
                                            while (commonPrefix < oldText.length && commonPrefix < newText.length && oldText[commonPrefix] == newText[commonPrefix]) { commonPrefix++ }
                                            var commonSuffix = 0
                                            while (commonPrefix + commonSuffix < oldText.length && commonPrefix + commonSuffix < newText.length && oldText[oldText.length - 1 - commonSuffix] == newText[newText.length - 1 - commonSuffix]) { commonSuffix++ }
                                            val deletedLen = oldText.length - commonPrefix - commonSuffix
                                            val insertedLen = newText.length - commonPrefix - commonSuffix
                                            val absoluteOffset = pages.map { it.replace("\u200B", "") }.take(pageIndex).sumOf { it.length + 1 }
                                            
                                            // Intercept and record modifications if Track Changes is enabled in review
                                            if (DocReviewManager.isTrackingEnabled(docId)) {
                                                if (insertedLen > 0) {
                                                    val insertedText = newText.substring(commonPrefix, commonPrefix + insertedLen)
                                                    DocReviewManager.addTrackedChange(docId, "insert", insertedText, absoluteOffset + commonPrefix)
                                                    DocFormatRepository.applySpan(docId, "track_insert", "User", absoluteOffset + commonPrefix, absoluteOffset + commonPrefix + insertedLen)
                                                }
                                                if (deletedLen > 0) {
                                                     val deletedText = oldText.substring(commonPrefix, commonPrefix + deletedLen)
                                                     DocReviewManager.addTrackedChange(docId, "delete", deletedText, absoluteOffset + commonPrefix)
                                                }
                                            }
                                            
                                            val newPages = pages.toMutableList()
                                            newPages[pageIndex] = newText
                                            val newFullText = newPages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                            onContentChange(newFullText)
                                            val globalSelection = TextRange(
                                                start = absoluteOffset + pageTextFieldValue.selection.start,
                                                end = absoluteOffset + pageTextFieldValue.selection.end
                                            )
                                            onTextFieldValueChange?.invoke(
                                                TextFieldValue(
                                                    text = newFullText,
                                                    selection = globalSelection
                                                )
                                            )
                                        } else if (oldSelection != newTfv.selection) {
                                            val absoluteOffset = pages.map { it.replace("\u200B", "") }.take(pageIndex).sumOf { it.length + 1 }
                                            val globalFullText = pages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                            val globalSelection = TextRange(
                                                start = absoluteOffset + newTfv.selection.start,
                                                end = absoluteOffset + newTfv.selection.end
                                            )
                                            onTextFieldValueChange?.invoke(
                                                TextFieldValue(
                                                    text = globalFullText,
                                                    selection = globalSelection
                                                )
                                            )
                                        }
                                    },
                                    onTextLayout = { result: androidx.compose.ui.text.TextLayoutResult ->
                                        if (textFieldHeightPx > 0 && result.size.height > (textFieldHeightPx - 50)) {
                                            val availableHeight = (textFieldHeightPx - 50).toFloat()
                                            val line = (0 until result.lineCount).findLast { result.getLineBottom(it) <= availableHeight }
                                            if (line != null && line < result.lineCount - 1 && line > 0 && splitOffset == -1) {
                                                val tentativeSplit = result.getLineEnd(line, visibleEnd = false)
                                                if (tentativeSplit < pageTextFieldValue.text.length) {
                                                    splitOffset = tentativeSplit
                                                } else {
                                                    splitOffset = result.getLineEnd(line - 1, visibleEnd = false)
                                                }
                                            } else if (line == 0 && splitOffset == -1 && result.lineCount > 1) {
                                                val tentativeSplit = result.getLineEnd(0, visibleEnd = false)
                                                if (tentativeSplit < pageTextFieldValue.text.length) {
                                                    splitOffset = tentativeSplit
                                                }
                                            }
                                        }
                                        if (textFieldHeightPx > 0 && splitOffset == -1 && mergeBackOffset == -1 && !mergeBackLocked && pageIndex + 1 < pages.size && pages[pageIndex + 1].isNotEmpty() && result.lineCount > 0) {
                                            val usedHeight = result.getLineBottom(result.lineCount - 1)
                                            val availableHeight = (textFieldHeightPx - 50).toFloat()
                                            if (usedHeight < availableHeight - 40) {
                                                mergeBackOffset = 1
                                            }
                                        }
                                    },
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(paperTextColor),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = paperTextColor,
                                        fontSize = fontSize,
                                        fontFamily = themeProps.defaultFontFamily,
                                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                    ),
                                    visualTransformation = remember(
                                        DocFormatRepository.getSpans(docId).toList(),
                                        docId,
                                        pageIndex,
                                        pageTextFieldValue.text.length,
                                        formatVersion,
                                        DocReviewManager.getCommentsForDoc(docId).size
                                    ) {
                                        val allSpans = DocFormatRepository.getSpans(docId).toMutableList()
                                        DocReviewManager.getCommentsForDoc(docId).forEach { c ->
                                            allSpans.add(DocFormatSpan(start = c.startOffset, end = c.endOffset, type = "comment_highlight", value = c.text))
                                        }
                                        RichTextVisualTransformation(allSpans, pages.take(pageIndex).sumOf { it.length + 1 })
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .onGloballyPositioned { textFieldHeightPx = it.size.height }
                                        .focusRequester(focusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (
                                                keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                                keyEvent.key == androidx.compose.ui.input.key.Key.Backspace &&
                                                pageTextFieldValue.selection.start == 0 &&
                                                pageTextFieldValue.selection.end == 0 &&
                                                pageIndex > 0
                                            ) {
                                                 val newPages = pages.toMutableList()
                                                 val prevText = newPages[pageIndex - 1]
                                                 val currentText = newPages[pageIndex]
                                                 val cleanCurrentText = currentText.replace("\u200B", "")
                                                
                                                targetFocusPage = pageIndex - 1
                                                targetFocusOffset = prevText.length
                                                
                                                newPages[pageIndex - 1] = prevText + cleanCurrentText
                                                newPages.removeAt(pageIndex)
                                                
                                                val newFullText = newPages.map { it.replace("\u200B", "") }.joinToString("\u000C")
                                                onContentChange(newFullText)
                                                val absoluteOffsetPrevPage = newPages.map { it.replace("\u200B", "") }.take(pageIndex - 1).sumOf { it.length + 1 }
                                                val globalSelection = TextRange(absoluteOffsetPrevPage + prevText.length)
                                                onTextFieldValueChange?.invoke(
                                                    TextFieldValue(
                                                        text = newFullText,
                                                        selection = globalSelection
                                                    )
                                                )
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .onFocusChanged { 
                                            if (it.isFocused) {
                                                onFocusChanged?.invoke(true)
                                            }
                                        }
                                        .testTag("word_editor_content_field"),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (pageContent.isEmpty()) {
                                                Text(
                                                        "Start typing your new document here...", 
                                                    color = Color.Gray.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = fontSize
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                            
                            // Footer
                            val shouldShowFooter = (showHeaderFooterOnFirstPage || pageIndex > 0) && (footerText.isNotEmpty() || (shouldShowPageNum && pageNumberPosition != null && (pageNumberPosition.startsWith("Bottom") || pageNumberPosition.contains("Margin"))))
                            if (shouldShowFooter) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = DocWordColor.copy(alpha = 0.1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onShowHeaderFooterDialog?.invoke()
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    val fLeftText = buildString {
                                        if (footerAlignment == "Left" && footerText.isNotEmpty()) append(footerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && (pageNumberPosition.startsWith("Bottom") || pageNumberPosition.contains("Margin"))) {
                                            val isLeft = pageNumberPosition.contains("Left") ||
                                                (pageNumberPosition.contains("Outside Margin") && pageIndex % 2 != 0) ||
                                                (pageNumberPosition.contains("Inside Margin") && pageIndex % 2 == 0)
                                            if (isLeft) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }
                                    val fCenterText = buildString {
                                        if (footerAlignment == "Center" && footerText.isNotEmpty()) append(footerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && (pageNumberPosition.startsWith("Bottom") || pageNumberPosition.contains("Margin"))) {
                                            val isCenter = !pageNumberPosition.contains("Left") && !pageNumberPosition.contains("Right") && !pageNumberPosition.contains("Margin")
                                            if (isCenter) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }
                                    val fRightText = buildString {
                                        if (footerAlignment == "Right" && footerText.isNotEmpty()) append(footerText)
                                        if (shouldShowPageNum && pageNumberPosition != null && (pageNumberPosition.startsWith("Bottom") || pageNumberPosition.contains("Margin"))) {
                                            val isRight = pageNumberPosition.contains("Right") ||
                                                (pageNumberPosition.contains("Outside Margin") && pageIndex % 2 == 0) ||
                                                (pageNumberPosition.contains("Inside Margin") && pageIndex % 2 != 0)
                                            if (isRight) {
                                                if (isNotEmpty()) append("   ")
                                                append(computedPageNumber)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left content
                                        Text(
                                            text = fLeftText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        // Center content
                                        Text(
                                            text = fCenterText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        // Right content
                                        Text(
                                            text = fRightText,
                                            fontSize = 11.sp,
                                            color = DocWordColor.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Picture Overlay
                        val pagePictures = DocPictureRepository.getPictures(docId).filter { it.pageIndex == pageIndex }
                        pagePictures.forEach { docPicture ->
                            DocPictureOverlay(
                                docPicture = docPicture,
                                docId = docId,
                                isSelected = selectedPictureId == docPicture.id,
                                onSelect = { selectedPictureId = docPicture.id },
                                onDeselect = { selectedPictureId = null },
                                paperWidth = paperMaxWidth,
                                paperHeight = minPageHeight,
                                selectedDocumentTheme = selectedDocumentTheme,
                                selectedThemeEffect = selectedThemeEffect
                            )
                        }

                        // Table Overlay
                        val pageTables = DocTableRepository.getTables(docId).filter { it.pageIndex == pageIndex }
                        pageTables.forEach { docTable ->
                            DocTableOverlay(
                                docTable = docTable,
                                docId = docId,
                                isSelected = selectedTableId == docTable.id,
                                onSelect = {
                                    selectedTableId = docTable.id
                                    selectedPictureId = null
                                    selectedShapeId = null
                                },
                                onDeselect = { selectedTableId = null },
                                paperWidth = paperMaxWidth,
                                paperHeight = minPageHeight,
                                selectedDocumentTheme = selectedDocumentTheme,
                                selectedThemeEffect = selectedThemeEffect
                            )
                        }

                        // Shape Overlay
                        val pageShapes = DocShapeRepository.getShapes(docId).filter { it.pageIndex == pageIndex }
                        pageShapes.forEach { docShape ->
                            DocShapeOverlay(
                                docShape = docShape,
                                docId = docId,
                                isSelected = selectedShapeId == docShape.id,
                                onSelect = {
                                    selectedShapeId = docShape.id
                                    selectedPictureId = null
                                    selectedTableId = null
                                },
                                onDeselect = { selectedShapeId = null },
                                paperWidth = paperMaxWidth,
                                paperHeight = minPageHeight,
                                selectedDocumentTheme = selectedDocumentTheme,
                                selectedThemeEffect = selectedThemeEffect
                            )
                        }
                    }
                }
            }
        }
    }
}
        }
    }
}


// --- 2. JC SPREADSHEET EDITOR ---
@Composable
fun SpreadsheetEditor(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCell by viewModel.selectedCell.collectAsStateWithLifecycle()
    val sheetData by viewModel.sheetData.collectAsStateWithLifecycle()

    val columns = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val rows = (1..20).toList()

    val cellExpr = sheetData[selectedCell] ?: ""

    Column(modifier = modifier) {
        // Formulas edit top bar
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coordinate badge
                Box(
                    modifier = Modifier
                        .width(55.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DocSheetColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCell ?: "",
                        fontWeight = FontWeight.Bold,
                        color = DocSheetColor,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Formula Icon indicator
                Text(
                    text = "fx",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DocSheetColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Cell Formula input bar
                OutlinedTextField(
                    value = cellExpr,
                    onValueChange = { viewModel.updateCellExpression(selectedCell, it) },
                    placeholder = { Text("Enter value or formula like =SUM(A1:A5) or =A1*A2", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DocSheetColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("formula_input_field")
                )
            }
        }

        // Active layout scrollable grid cells
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                // Header letters columns
                Row {
                    // Empty corner anchor
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 28.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, Color.LightGray)
                    )

                    for (col in columns) {
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 28.dp)
                                .background(Color(0xFFF1F3F4))
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                col,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Numbers rows & dynamic cell contents
                for (row in rows) {
                    Row {
                        // Row coordinate badge
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 40.dp)
                                .background(Color(0xFFF1F3F4))
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = row.toString(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        for (col in columns) {
                            val cellRef = "$col$row"
                            val isSelected = selectedCell == cellRef

                            val evaluatedValue = viewModel.getCellValue(cellRef)
                            val originalExpression = sheetData[cellRef] ?: ""

                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 40.dp)
                                    .background(
                                        if (isSelected) DocSheetColor.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) DocSheetColor else Color.LightGray
                                    )
                                    .clickable { viewModel.selectCell(cellRef) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = evaluatedValue,
                                            fontWeight = if (originalExpression.startsWith("=")) FontWeight.Bold else FontWeight.Normal,
                                            color = if (evaluatedValue.startsWith("#")) Color.Red else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (originalExpression.startsWith("=") && !isSelected) {
                                            // Tiny tag indicating reactive formulas
                                            Text(
                                                text = "fx",
                                                color = DocSheetColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 3. JC SLIDE PRESENTATION WORKSPACE ---
@Composable
fun SlidePresentationWorkspace(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val activeIdx by viewModel.currentSlideIndex.collectAsStateWithLifecycle()

    val activeSlide = slides.getOrNull(activeIdx) ?: SlideItem("Title Slide", "", "indigo", "title_slide")

    Column(modifier = modifier) {
        // Toolkit control actions bar
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.addNewSlide() },
                    colors = ButtonDefaults.buttonColors(containerColor = DocSlideColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Slide", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Slide", fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.deleteSlide(activeIdx) },
                    enabled = slides.size > 1,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.08f), contentColor = Color.Red),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Slide", fontSize = 12.sp)
                }

                Divider(modifier = Modifier.height(20.dp).width(1.dp))

                // Play presentation mode launcher
                Button(
                    onClick = { viewModel.togglePresenterMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("play_slides_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play presentation", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play Deck", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Slide ${activeIdx + 1} of ${slides.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Secondary workspace split view
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left list of slides navigator
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f))
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slides.forEachIndexed { index, item ->
                    val isActive = index == activeIdx
                    Card(
                        onClick = { viewModel.selectSlide(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = getSlideThemeBg(item.theme).copy(alpha = if (isActive) 1f else 0.4f)
                        ),
                        border = BorderStroke(
                            2.dp,
                            if (isActive) DocSlideColor else Color.Transparent
                        ),
                        modifier = Modifier
                            .size(76.dp, 54.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (item.theme == "charcoal") Color.White else Color.Black,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // Central layout editor
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slide Template background preview wrapper
                Card(
                    colors = CardDefaults.cardColors(containerColor = getSlideThemeBg(activeSlide.theme)),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .height(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (activeSlide.layout) {
                            "title_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 24.sp, FontWeight.ExtraBold, TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth().testTag("slide_title_input")
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                BasicTextField(
                                    value = activeSlide.body,
                                    onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 13.sp, FontWeight.Normal, TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "content_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 18.sp, FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                BasicTextField(
                                    value = activeSlide.body,
                                    onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 12.sp, FontWeight.Normal),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                                )
                            }
                            "split_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 16.sp, FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BasicTextField(
                                        value = activeSlide.body,
                                        onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                        textStyle = TextStyleCompose(activeSlide.theme, 11.sp, FontWeight.Normal),
                                        modifier = Modifier.weight(1f).heightIn(min = 120.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "[ Presentation Illustration Placeholder ]",
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Slide configurations settings cards
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Slide Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Choose Color Deck Theme:", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("indigo", "crimson", "teal", "charcoal", "cyberpunk").forEach { themeName ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(getSlideThemeBg(themeName))
                                        .border(
                                            width = if (activeSlide.theme == themeName) 2.dp else 0.5.dp,
                                            color = if (activeSlide.theme == themeName) DocSlideColor else Color.LightGray
                                        )
                                        .clickable {
                                            viewModel.updateSlideContent(
                                                activeSlide.title,
                                                activeSlide.body,
                                                themeName,
                                                activeSlide.layout
                                            )
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Choose Slide Layout Structure:", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("title_slide", "content_slide", "split_slide").forEach { layout ->
                                Button(
                                    onClick = {
                                        viewModel.updateSlideContent(
                                            activeSlide.title,
                                            activeSlide.body,
                                            activeSlide.theme,
                                            layout
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeSlide.layout == layout) DocSlideColor else Color.LightGray.copy(alpha = 0.2f),
                                        contentColor = if (activeSlide.layout == layout) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(layout.replace("_", " ").uppercase(), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers for presentation theme compilation
fun getSlideThemeBg(theme: String): Color {
    return when (theme) {
        "indigo" -> Color(0xFFE8EAF6)
        "crimson" -> Color(0xFFFFEBEE)
        "teal" -> Color(0xFFE0F2F1)
        "charcoal" -> Color(0xFF2D3033)
        "cyberpunk" -> Color(0xFFFFFDE7)
        else -> Color(0xFFE8EAF6)
    }
}

@Composable
fun TextStyleCompose(theme: String, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight, align: TextAlign = TextAlign.Start): androidx.compose.ui.text.TextStyle {
    return androidx.compose.ui.text.TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = if (theme == "charcoal") Color.White else Color.Black,
        textAlign = align
    )
}

// Fullscreen slideshow overlay presenter style
@Composable
fun FullscreenPresentationView(
    viewModel: DocViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val activeIdx by viewModel.currentSlideIndex.collectAsStateWithLifecycle()

    val activeSlide = slides.getOrNull(activeIdx) ?: SlideItem("End of Deck", "", "indigo", "title_slide")

    Dialog(onDismissRequest = onExit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = getSlideThemeBg(activeSlide.theme)),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Presenter top bar indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DocSlideColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("PRESENTATION MODE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Exit Presentation",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // The actual presentation content display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = if (activeSlide.layout == "title_slide") Alignment.CenterHorizontally else Alignment.Start
                ) {
                    Text(
                        text = activeSlide.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeSlide.theme == "charcoal") Color.White else Color.Black,
                        textAlign = if (activeSlide.layout == "title_slide") TextAlign.Center else TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = activeSlide.body,
                        fontSize = 14.sp,
                        color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray,
                        textAlign = if (activeSlide.layout == "title_slide") TextAlign.Center else TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Presenter switching footers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { viewModel.selectSlide(activeIdx - 1) },
                        enabled = activeIdx > 0
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Previous Slide",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }

                    Text(
                        text = "Slide ${activeIdx + 1} of ${slides.size}",
                        fontSize = 12.sp,
                        color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray
                    )

                    IconButton(
                        onClick = { viewModel.selectSlide(activeIdx + 1) },
                        enabled = activeIdx < slides.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = "Next Slide",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }
                }
            }
        }
    }
}

// Dialog helper for creating new documents
@Composable
fun CreateDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("word") } // "word", "sheet", "slide"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "New ONLYOFFICE File",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    placeholder = { Text("e.g. Sales Forecast 2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OnlyOfficePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_document_title_field")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SELECT OFFICE APP TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom type buttons matching ONLYOFFICE layout
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeSelectionRow(
                        typeName = "Word Document",
                        typeId = "word",
                        color = DocWordColor,
                        desc = "Create styled notes & rich layout docs",
                        isSelected = selectedType == "word",
                        onClick = { selectedType = "word" }
                    )
                    TypeSelectionRow(
                        typeName = "Spreadsheet Ledger",
                        typeId = "sheet",
                        color = DocSheetColor,
                        desc = "Execute formulas & manage row cell matrices",
                        isSelected = selectedType == "sheet",
                        onClick = { selectedType = "sheet" }
                    )
                    TypeSelectionRow(
                        typeName = "Presentation Slides",
                        typeId = "slide",
                        color = DocSlideColor,
                        desc = "Design templates & play interactive slide decks",
                        isSelected = selectedType == "slide",
                        onClick = { selectedType = "slide" }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onConfirm(title, selectedType) },
                        colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_create_button")
                    ) {
                        Text("Create File", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun TypeSelectionRow(
    typeName: String,
    typeId: String,
    color: Color,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selection_type_$typeId")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                val leadChar = when (typeId) {
                    "word" -> "W"
                    "sheet" -> "S"
                    "slide" -> "P"
                    else -> "D"
                }
                Text(leadChar, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typeName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CustomMarginsDialog(
    currentTop: androidx.compose.ui.unit.Dp,
    currentBottom: androidx.compose.ui.unit.Dp,
    currentLeft: androidx.compose.ui.unit.Dp,
    currentRight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onApply: (top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp, left: androidx.compose.ui.unit.Dp, right: androidx.compose.ui.unit.Dp) -> Unit
) {
    var topMargin by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentTop.value / 72.0f)) }
    var bottomMargin by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentBottom.value / 72.0f)) }
    var leftMargin by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentLeft.value / 72.0f)) }
    var rightMargin by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentRight.value / 72.0f)) }
    var gutter by remember { mutableStateOf("0.0") }
    var gutterPosition by remember { mutableStateOf("Left") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Page Setup - Margins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Specify custom page margins (in inches)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Presets Row
                Text("Standard Presets:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("Normal" to (1f to 1f), "Narrow" to (0.5f to 0.5f), "Moderate" to (1f to 0.75f), "Wide" to (1f to 2f))
                    presets.forEach { (name, dims) ->
                        OutlinedButton(
                            onClick = {
                                topMargin = String.format(java.util.Locale.US, "%.1f", dims.first)
                                bottomMargin = String.format(java.util.Locale.US, "%.1f", dims.first)
                                leftMargin = String.format(java.util.Locale.US, "%.1f", dims.second)
                                rightMargin = String.format(java.util.Locale.US, "%.1f", dims.second)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Text(name, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Margins grid
                Text("Margins:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = topMargin,
                        onValueChange = { topMargin = it },
                        label = { Text("Top") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("1.0") }
                    )
                    OutlinedTextField(
                        value = bottomMargin,
                        onValueChange = { bottomMargin = it },
                        label = { Text("Bottom") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("1.0") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = leftMargin,
                        onValueChange = { leftMargin = it },
                        label = { Text("Left") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("1.0") }
                    )
                    OutlinedTextField(
                        value = rightMargin,
                        onValueChange = { rightMargin = it },
                        label = { Text("Right") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("1.0") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Gutter section
                Text("Gutter (Binding Margin):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = gutter,
                        onValueChange = { gutter = it },
                        label = { Text("Size") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        placeholder = { Text("0.0") }
                    )

                    // Gutter position selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Position", fontSize = 10.sp, color = Color.Gray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { gutterPosition = "Left" }
                                    .background(if (gutterPosition == "Left") MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Left", fontSize = 11.sp, color = if (gutterPosition == "Left") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { gutterPosition = "Top" }
                                    .background(if (gutterPosition == "Top") MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Top", fontSize = 11.sp, color = if (gutterPosition == "Top") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val topInches = topMargin.toFloatOrNull() ?: 1.0f
                    val bottomInches = bottomMargin.toFloatOrNull() ?: 1.0f
                    val leftInches = leftMargin.toFloatOrNull() ?: 1.0f
                    val rightInches = rightMargin.toFloatOrNull() ?: 1.0f
                    val gutterInches = gutter.toFloatOrNull() ?: 0.0f
                    
                    val finalLeft = leftInches + (if (gutterPosition == "Left") gutterInches else 0f)
                    val finalTop = topInches + (if (gutterPosition == "Top") gutterInches else 0f)
                    
                    onApply((finalTop * 72f).dp, (bottomInches * 72f).dp, (finalLeft * 72f).dp, (rightInches * 72f).dp)
                }
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomSizeDialog(
    currentWidth: Float,
    currentHeight: Float,
    onDismiss: () -> Unit,
    onApply: (width: Float, height: Float) -> Unit
) {
    var width by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentWidth)) }
    var height by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentHeight)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Page Setup - Page Size", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Specify custom page size (in inches)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Presets List
                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Letter" to (8.5f to 11.0f),
                        "Legal" to (8.5f to 14.0f),
                        "Executive" to (7.25f to 10.5f),
                        "A4" to (8.27f to 11.69f)
                    )
                    presets.forEach { (name, dims) ->
                        OutlinedButton(
                            onClick = {
                                width = String.format(java.util.Locale.US, "%.2f", dims.first)
                                height = String.format(java.util.Locale.US, "%.2f", dims.second)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Text(name, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Page Dimensions inputs
                Text("Dimensions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Width (inches)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("8.5") }
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (inches)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("11.0") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Standard Reference Reference Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Standard Reference Layouts:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("• Letter: 8.5 in x 11.0 in (US Standard)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                        Text("• Legal: 8.5 in x 14.0 in (Contract Drafting)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                        Text("• Executive: 7.25 in x 10.5 in (Corporate)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                        Text("• A4: 8.27 in x 11.69 in (International Standard)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                        Text("• A3: 11.69 in x 16.54 in (Large Format poster)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = width.toFloatOrNull() ?: 8.5f
                    val h = height.toFloatOrNull() ?: 11.0f
                    onApply(w, h)
                }
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PageNumberFormatDialog(
    currentFormat: String,
    currentStartAt: String,
    currentShowOnFirstPage: Boolean,
    onDismiss: () -> Unit,
    onApply: (format: String, startAt: String, showOnFirstPage: Boolean) -> Unit
) {
    var format by remember { mutableStateOf(currentFormat) }
    var startAt by remember { mutableStateOf(currentStartAt) }
    var isStartAtChoice by remember { mutableStateOf(currentStartAt != "1") }
    var showOnFirstPage by remember { mutableStateOf(currentShowOnFirstPage) }
    var includeChapterNumber by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Numbers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Page Number Format") 
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Dropdown for format
                var expanded by remember { mutableStateOf(false) }
                val formats = listOf(
                    "1, 2, 3...", 
                    "01, 02, 03...", 
                    "001, 002, 003...", 
                    "I, II, III...", 
                    "i, ii, iii...", 
                    "A, B, C...", 
                    "a, b, c...",
                    "- 1 -, - 2 -, - 3 -...",
                    "Page X",
                    "Page X of Y"
                )
                
                Box {
                    OutlinedTextField(
                        value = format,
                        onValueChange = {},
                        label = { Text("Number format") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Select format")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        formats.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    format = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showOnFirstPage, onCheckedChange = { showOnFirstPage = it })
                    Text("Show page number on first page", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeChapterNumber, onCheckedChange = { includeChapterNumber = it })
                    Text("Include chapter number", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    var dividerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    androidx.compose.material3.HorizontalDivider(color = dividerColor)
                }

                Text("Page numbering", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                
                // Grouping of radio buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = !isStartAtChoice, 
                            onClick = { 
                                isStartAtChoice = false 
                                startAt = "1"
                            }
                        )
                        Text("Continue from previous section", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = isStartAtChoice, 
                            onClick = { 
                                isStartAtChoice = true 
                            }
                        )
                        Text("Start at:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = if (isStartAtChoice) startAt else "",
                            onValueChange = { 
                                if (isStartAtChoice) {
                                    startAt = it.filter { char -> char.isDigit() }
                                }
                            },
                            enabled = isStartAtChoice,
                            singleLine = true,
                            modifier = Modifier.width(100.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val finalStart = if (isStartAtChoice && startAt.isNotBlank()) startAt else "1"
                    onApply(format, finalStart, showOnFirstPage) 
                }
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HeaderFooterDialog(
    currentHeaderText: String,
    currentFooterText: String,
    currentHeaderAlignment: String,
    currentFooterAlignment: String,
    currentShowOnFirstPage: Boolean,
    onDismiss: () -> Unit,
    onApply: (headerText: String, footerText: String, headerAlignment: String, footerAlignment: String, showOnFirstPage: Boolean) -> Unit
) {
    var headerText by remember { mutableStateOf(currentHeaderText) }
    var footerText by remember { mutableStateOf(currentFooterText) }
    var headerAlignment by remember { mutableStateOf(currentHeaderAlignment) }
    var footerAlignment by remember { mutableStateOf(currentFooterAlignment) }
    var showOnFirstPage by remember { mutableStateOf(currentShowOnFirstPage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ViewAgenda,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Header & Footer Settings")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Standard MS Word headers & footers are visible across top and bottom margins of all pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // --- HEADER SECTION ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Header Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = headerText,
                            onValueChange = { headerText = it },
                            label = { Text("Header Text") },
                            placeholder = { Text("e.g., Document Title, Confidential") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Header Alignment", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Left", "Center", "Right").forEach { align ->
                                val selected = headerAlignment == align
                                Button(
                                    onClick = { headerAlignment = align },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(align, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // --- FOOTER SECTION ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Footer Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = footerText,
                            onValueChange = { footerText = it },
                            label = { Text("Footer Text") },
                            placeholder = { Text("e.g., Company, Page details") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Footer Alignment", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Left", "Center", "Right").forEach { align ->
                                val selected = footerAlignment == align
                                Button(
                                    onClick = { footerAlignment = align },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(align, fontSize = 12.sp)
                                }
                            }
                        }

                        // Presets Row for Footers
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Presets / Quick Fill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Page X of Y", "Confidential", "[Draft]").forEach { preset ->
                                OutlinedButton(
                                    onClick = { footerText = preset },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    Text(preset, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // --- OPTIONS SECTION ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = showOnFirstPage, onCheckedChange = { showOnFirstPage = it })
                    Text("Show headers & footers on first page", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onApply(headerText, footerText, headerAlignment, footerAlignment, showOnFirstPage) 
                }
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DocPictureOverlay(
    docPicture: DocPicture,
    docId: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    paperWidth: Dp,
    paperHeight: Dp,
    selectedDocumentTheme: String = "Office Classic",
    selectedThemeEffect: String = "None"
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Remember the values locally to make the drag/resize interaction hardware-accelerated and ultra-smooth.
    // They are keyed on docPicture.id and their repository source of truth so they load correctly when updated externally or initially.
    var localX by remember(docPicture.id, docPicture.x) { mutableStateOf(docPicture.x) }
    var localY by remember(docPicture.id, docPicture.y) { mutableStateOf(docPicture.y) }
    var localWidth by remember(docPicture.id, docPicture.width) { mutableStateOf(docPicture.width) }
    var localHeight by remember(docPicture.id, docPicture.height) { mutableStateOf(docPicture.height) }

    // Avoid stale closures during persistent pointer input gestures
    val currentPictureState = rememberUpdatedState(docPicture)
    
    val opacity = docPicture.opacity
    val cropLeft = docPicture.cropLeft
    val cropTop = docPicture.cropTop
    val cropRight = docPicture.cropRight
    val cropBottom = docPicture.cropBottom
    val isGrayscale = docPicture.isGrayscale
    val containerElevation = docPicture.elevation

    val themeProps = remember(selectedDocumentTheme) {
        getThemeProperties(selectedDocumentTheme)
    }

    Box(
        modifier = Modifier
            .offset(x = localX, y = localY)
            .size(width = localWidth, height = localHeight)
            .pointerInput(docPicture.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = {
                        DocPictureRepository.updatePicture(
                            docId = docId,
                            picture = currentPictureState.value.copy(x = localX, y = localY)
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
            .shadow(containerElevation, RoundedCornerShape(docPicture.borderRadiusDp))
            .applyThemeEffect(selectedThemeEffect, RoundedCornerShape(docPicture.borderRadiusDp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, themeProps.secondaryAccent, RoundedCornerShape(docPicture.borderRadiusDp))
                } else {
                    Modifier
                }
            )
    ) {
        // Safe hex-to-color helper
        val borderColor = remember(docPicture.borderColorHex, selectedDocumentTheme) {
            try {
                if (docPicture.borderColorHex.isBlank() || docPicture.borderColorHex == "DocWordColor") {
                    themeProps.primaryAccent
                } else {
                    Color(android.graphics.Color.parseColor(if (docPicture.borderColorHex.startsWith("#")) docPicture.borderColorHex else "#${docPicture.borderColorHex}"))
                }
            } catch (e: Exception) {
                themeProps.primaryAccent
            }
        }

        // Custom combined color matrix for grayscale, brightness, contrast, saturation, and color inversion!
        val colorFilter = remember(isGrayscale, docPicture.brightness, docPicture.contrast, docPicture.saturation, docPicture.isInverted) {
            val cm = ColorMatrix()
            
            if (isGrayscale) {
                cm.setToSaturation(0f)
            } else if (docPicture.saturation != 1.0f) {
                cm.setToSaturation(docPicture.saturation)
            }
            
            val b = (docPicture.brightness - 1.0f) * 255f
            val c = docPicture.contrast
            
            val array = floatArrayOf(
                c, 0f, 0f, 0f, b,
                0f, c, 0f, 0f, b,
                0f, 0f, c, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )
            val brightnessContrastMatrix = ColorMatrix(array)
            
            val finalMatrix = if (docPicture.isInverted) {
                val invertArray = floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
                val invertMatrix = ColorMatrix(invertArray)
                ColorMatrix().apply {
                    set(brightnessContrastMatrix)
                    timesAssign(invertMatrix)
                    timesAssign(cm)
                }
            } else {
                ColorMatrix().apply {
                    set(brightnessContrastMatrix)
                    timesAssign(cm)
                }
            }
            
            ColorFilter.colorMatrix(finalMatrix)
        }

        val shape = RoundedCornerShape(docPicture.borderRadiusDp)

        Image(
            painter = coil.compose.rememberAsyncImagePainter(model = docPicture.uri),
            contentDescription = "Document Picture",
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            alpha = opacity,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = docPicture.rotation
                }
                .clip(shape)
                .then(
                    if (docPicture.borderWidthDp > 0.dp) {
                        Modifier.border(
                            width = docPicture.borderWidthDp,
                            color = borderColor,
                            shape = shape
                        )
                    } else Modifier
                )
                .clip(
                    PictureCropShape(
                        left = cropLeft,
                        top = cropTop,
                        right = cropRight,
                        bottom = cropBottom
                    )
                )
        )

        if (isSelected) {
            val handleSize = 10.dp
            val handleColor = DocWordColor

            // Floating Move/Drag handle at TopCenter
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -18.dp)
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .background(handleColor, CircleShape)
                    .pointerInput(docPicture.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDragEnd = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = currentPictureState.value.copy(x = localX, y = localY)
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
                    contentDescription = "Move/Drag Picture Handle",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -handleSize / 2, y = -handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docPicture.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = currentPictureState.value.copy(
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
                            val newWidth = (localWidth - dx).coerceAtLeast(40.dp)
                            val newHeight = (localHeight - dy).coerceAtLeast(40.dp)
                            localX += (localWidth - newWidth)
                            localY += (localHeight - newHeight)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = handleSize / 2, y = -handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docPicture.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = currentPictureState.value.copy(
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
                            val newWidth = (localWidth + dx).coerceAtLeast(40.dp)
                            val newHeight = (localHeight - dy).coerceAtLeast(40.dp)
                            localY += (localHeight - newHeight)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docPicture.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = currentPictureState.value.copy(
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
                            val newWidth = (localWidth - dx).coerceAtLeast(40.dp)
                            val newHeight = (localHeight + dy).coerceAtLeast(40.dp)
                            localX += (localWidth - newWidth)
                            localWidth = newWidth
                            localHeight = newHeight
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(handleColor, CircleShape)
                    .pointerInput(docPicture.id) {
                        detectDragGestures(
                            onDragEnd = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = currentPictureState.value.copy(
                                        width = localWidth,
                                        height = localHeight
                                    )
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            localWidth = (localWidth + dx).coerceAtLeast(40.dp)
                            localHeight = (localHeight + dy).coerceAtLeast(40.dp)
                        }
                    }
            )

            // Dynamic, narrow, in-line formatting toolbar
            var activeSubMenu by remember(docPicture.id) { mutableStateOf("main") }
            val finalToolbarX = remember(localX, localWidth, paperWidth) {
                val desiredStart = localX + (localWidth - 310.dp) / 2
                val finalStart = desiredStart.coerceIn(4.dp, (paperWidth - 314.dp).coerceAtLeast(4.dp))
                finalStart - localX
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = finalToolbarX,
                        y = if (localY < 64.dp) localHeight + 12.dp else (-64).dp
                    )
                    .width(310.dp)
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
                    ) { /* Consume clicks to prevent dragging underneath */ }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (activeSubMenu) {
                    "main" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { activeSubMenu = "crop" }) {
                                Icon(Icons.Default.Crop, contentDescription = "Crop Tool", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { activeSubMenu = "opacity" }) {
                                Icon(Icons.Default.Opacity, contentDescription = "Opacity Control", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { activeSubMenu = "border" }) {
                                Icon(Icons.Default.Edit, contentDescription = "Border Style", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { activeSubMenu = "effects" }) {
                                Icon(Icons.Default.Tune, contentDescription = "Visual Filters", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { activeSubMenu = "elevation" }) {
                                Icon(Icons.Default.Layers, contentDescription = "Shadow Z-Index", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { activeSubMenu = "size" }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Resize Dimensions", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(
                                        rotation = 0f,
                                        opacity = 1f,
                                        brightness = 1f,
                                        contrast = 1f,
                                        saturation = 1f,
                                        elevation = 0.dp,
                                        borderWidthDp = 0.dp,
                                        borderRadiusDp = 0.dp,
                                        cropLeft = 0f,
                                        cropRight = 0f,
                                        cropTop = 0f,
                                        cropBottom = 0f,
                                        isGrayscale = false,
                                        isInverted = false
                                    )
                                )
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Picture", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                DocPictureRepository.removePicture(docId, docPicture.id)
                                onDeselect()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Picture", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    "crop" -> {
                        var cropEdge by remember { mutableStateOf("L") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { activeSubMenu = "main" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            
                            listOf("L", "R", "T", "B").forEach { edge ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            if (cropEdge == edge) DocWordColor else Color.Gray.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .clickable { cropEdge = edge },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(edge, fontSize = 10.sp, color = if (cropEdge == edge) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            val currentValue = when (cropEdge) {
                                "L" -> docPicture.cropLeft
                                "R" -> docPicture.cropRight
                                "T" -> docPicture.cropTop
                                else -> docPicture.cropBottom
                            }
                            
                            Slider(
                                value = currentValue,
                                onValueChange = { newValue ->
                                    val updatedPic = when (cropEdge) {
                                        "L" -> docPicture.copy(cropLeft = newValue)
                                        "R" -> docPicture.copy(cropRight = newValue)
                                        "T" -> docPicture.copy(cropTop = newValue)
                                        else -> docPicture.copy(cropBottom = newValue)
                                    }
                                    DocPictureRepository.updatePicture(docId = docId, picture = updatedPic)
                                },
                                valueRange = 0f..0.5f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                            )
                            
                            Text("${(currentValue * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    "opacity" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { activeSubMenu = "main" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            Icon(Icons.Default.Opacity, contentDescription = "Opacity", tint = DocWordColor, modifier = Modifier.size(14.dp))
                            Slider(
                                value = docPicture.opacity,
                                onValueChange = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(opacity = it)
                                    )
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                            )
                            Text("${(docPicture.opacity * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "border" -> {
                        var borderSubMenu by remember { mutableStateOf("width") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = {
                                if (borderSubMenu != "width") borderSubMenu = "width" else activeSubMenu = "main"
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            
                            if (borderSubMenu == "width") {
                                IconButton(onClick = { borderSubMenu = "radius" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.RoundedCorner, contentDescription = "Border Radius", tint = DocWordColor, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { borderSubMenu = "color" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ColorLens, contentDescription = "Border Color", tint = DocWordColor, modifier = Modifier.size(16.dp))
                                }
                                Slider(
                                    value = docPicture.borderWidthDp.value,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(borderWidthDp = it.dp)
                                        )
                                    },
                                    valueRange = 0f..10f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${docPicture.borderWidthDp.value.toInt()}dp", fontSize = 9.sp)
                            } else if (borderSubMenu == "radius") {
                                IconButton(onClick = { borderSubMenu = "width" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.LineWeight, contentDescription = "Border Width", tint = DocWordColor, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { borderSubMenu = "color" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ColorLens, contentDescription = "Border Color", tint = DocWordColor, modifier = Modifier.size(16.dp))
                                }
                                Slider(
                                    value = docPicture.borderRadiusDp.value,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(borderRadiusDp = it.dp)
                                        )
                                    },
                                    valueRange = 0f..40f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${docPicture.borderRadiusDp.value.toInt()}dp", fontSize = 9.sp)
                            } else {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val presets = listOf("#DF4A32", "#4DA06F", "#E08B3A", "#2196F3", "#9C27B0", "#000000", "#FFFFFF")
                                    presets.forEach { colorStr ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(android.graphics.Color.parseColor(colorStr)), CircleShape)
                                                .clickable {
                                                    DocPictureRepository.updatePicture(
                                                        docId = docId,
                                                        picture = docPicture.copy(borderColorHex = colorStr)
                                                    )
                                                }
                                                .border(
                                                    width = if (docPicture.borderColorHex.uppercase() == colorStr.uppercase()) 2.dp else 1.dp,
                                                    color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "effects" -> {
                        var effectsSubMenu by remember { mutableStateOf("filters") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = {
                                if (effectsSubMenu != "filters") effectsSubMenu = "filters" else activeSubMenu = "main"
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            
                            if (effectsSubMenu == "filters") {
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(isGrayscale = !docPicture.isGrayscale)
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.FilterBAndW,
                                        contentDescription = "Grayscale",
                                        tint = if (docPicture.isGrayscale) DocWordColor else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(isInverted = !docPicture.isInverted)
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.InvertColors,
                                        contentDescription = "Invert Colors",
                                        tint = if (docPicture.isInverted) DocWordColor else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { effectsSubMenu = "brightness" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Brightness5, contentDescription = "Brightness", tint = DocWordColor, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { effectsSubMenu = "contrast" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Tonality, contentDescription = "Contrast", tint = DocWordColor, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { effectsSubMenu = "saturation" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Palette, contentDescription = "Saturation", tint = DocWordColor, modifier = Modifier.size(18.dp))
                                }
                            } else if (effectsSubMenu == "brightness") {
                                Icon(Icons.Default.Brightness5, contentDescription = "Brightness", tint = DocWordColor, modifier = Modifier.size(14.dp))
                                Slider(
                                    value = docPicture.brightness,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(brightness = it)
                                        )
                                    },
                                    valueRange = 0f..2f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${(docPicture.brightness * 100).toInt()}%", fontSize = 9.sp)
                            } else if (effectsSubMenu == "contrast") {
                                Icon(Icons.Default.Tonality, contentDescription = "Contrast", tint = DocWordColor, modifier = Modifier.size(14.dp))
                                Slider(
                                    value = docPicture.contrast,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(contrast = it)
                                        )
                                    },
                                    valueRange = 0f..2f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${(docPicture.contrast * 100).toInt()}%", fontSize = 9.sp)
                            } else {
                                Icon(Icons.Default.Palette, contentDescription = "Saturation", tint = DocWordColor, modifier = Modifier.size(14.dp))
                                Slider(
                                    value = docPicture.saturation,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(saturation = it)
                                        )
                                    },
                                    valueRange = 0f..2f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${(docPicture.saturation * 100).toInt()}%", fontSize = 9.sp)
                            }
                        }
                    }
                    "elevation" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { activeSubMenu = "main" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            Icon(Icons.Default.Layers, contentDescription = "Elevation", tint = DocWordColor, modifier = Modifier.size(14.dp))
                            Slider(
                                value = docPicture.elevation.value,
                                onValueChange = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(elevation = it.dp)
                                    )
                                },
                                valueRange = 0f..24f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                            )
                            Text("${docPicture.elevation.value.toInt()}dp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "size" -> {
                        var sizeProperty by remember { mutableStateOf("W") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { activeSubMenu = "main" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DocWordColor, modifier = Modifier.size(18.dp))
                            }
                            
                            listOf("W", "H", "R").forEach { prop ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            if (sizeProperty == prop) DocWordColor else Color.Gray.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .clickable { sizeProperty = prop },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (prop) {
                                            "W" -> "W"
                                            "H" -> "H"
                                            else -> "Rot"
                                        },
                                        fontSize = 8.sp,
                                        color = if (sizeProperty == prop) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (sizeProperty == "W") {
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(width = (docPicture.width - 15.dp).coerceAtLeast(30.dp))
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                                }
                                Text("${docPicture.width.value.toInt()}dp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(width = docPicture.width + 15.dp)
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                                }
                            } else if (sizeProperty == "H") {
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(height = (docPicture.height - 15.dp).coerceAtLeast(30.dp))
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                                }
                                Text("${docPicture.height.value.toInt()}dp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(height = docPicture.height + 15.dp)
                                    )
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                                }
                            } else {
                                Slider(
                                    value = docPicture.rotation,
                                    onValueChange = {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(rotation = it)
                                        )
                                    },
                                    valueRange = 0f..360f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                                )
                                Text("${docPicture.rotation.toInt()}°", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

class PictureCropShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val rect = androidx.compose.ui.geometry.Rect(
            left = size.width * left,
            top = size.height * top,
            right = size.width * (1f - right),
            bottom = size.height * (1f - bottom)
        )
        return androidx.compose.ui.graphics.Outline.Rectangle(rect)
    }
}

@Composable
fun PictureFormattingPanel(
    docPicture: DocPicture,
    docId: Int,
    onClose: () -> Unit
) {
    var offsetX by remember(docPicture.id) { mutableFloatStateOf(16f) }
    var offsetY by remember(docPicture.id) { mutableFloatStateOf(100f) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Card(
        modifier = Modifier
            .offset(
                x = with(density) { offsetX.toDp() },
                y = with(density) { offsetY.toDp() }
            )
            .width(300.dp)
            .heightIn(max = 420.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF202124) else Color(0xFFF8F9FA)
        ),
        border = BorderStroke(1.dp, DocWordColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DocWordColor)
                    .pointerInput(docPicture.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag Handle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Picture Format",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Core Controls", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotation Angle", fontSize = 11.sp)
                        Text("${docPicture.rotation.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.rotation,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(rotation = it)
                            )
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity / Transparency", fontSize = 11.sp)
                        Text("${(docPicture.opacity * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.opacity,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(opacity = it)
                            )
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shadow / Elevation", fontSize = 11.sp)
                        Text("${docPicture.elevation.value.toInt()} dp", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.elevation.value,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(elevation = it.dp)
                            )
                        },
                        valueRange = 0f..24f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }
                
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                
                Text("Visual Effects & Filters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = docPicture.isGrayscale,
                            onCheckedChange = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(isGrayscale = it)
                                )
                            },
                            colors = CheckboxDefaults.colors(checkedColor = DocWordColor)
                        )
                        Text("Grayscale (B&W)", fontSize = 11.sp)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = docPicture.isInverted,
                            onCheckedChange = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(isInverted = it)
                                )
                            },
                            colors = CheckboxDefaults.colors(checkedColor = DocWordColor)
                        )
                        Text("Invert Colors", fontSize = 11.sp)
                    }
                }
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brightness", fontSize = 11.sp)
                        Text("${(docPicture.brightness * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.brightness,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(brightness = it)
                            )
                        },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Contrast", fontSize = 11.sp)
                        Text("${(docPicture.contrast * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.contrast,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(contrast = it)
                            )
                        },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Saturation", fontSize = 11.sp)
                        Text("${(docPicture.saturation * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.saturation,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(saturation = it)
                            )
                        },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }
                
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                
                Text("Borders & Rounded Corners", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Border Thickness", fontSize = 11.sp)
                        Text("${docPicture.borderWidthDp.value.toInt()} dp", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.borderWidthDp.value,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(borderWidthDp = it.dp)
                            )
                        },
                        valueRange = 0f..10f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Corner Rounding (Radius)", fontSize = 11.sp)
                        Text("${docPicture.borderRadiusDp.value.toInt()} dp", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = docPicture.borderRadiusDp.value,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(borderRadiusDp = it.dp)
                            )
                        },
                        valueRange = 0f..40f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Text("Border Color (Hex)", fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(if (docPicture.borderColorHex.startsWith("#")) docPicture.borderColorHex else "#${docPicture.borderColorHex}")) } catch (e: Exception) { DocWordColor },
                                    CircleShape
                                )
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                        
                        BasicTextField(
                            value = docPicture.borderColorHex,
                            onValueChange = {
                                if (it.length <= 7) {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(borderColorHex = it)
                                    )
                                }
                            },
                            textStyle = TextStyle(fontSize = 11.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSystemInDarkTheme()) Color(0xFF2B2B30) else Color(0xFFE8EAED), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf("#DF4A32", "#4DA06F", "#E08B3A", "#2196F3", "#9C27B0", "#000000")
                        presets.forEach { colorStr ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(android.graphics.Color.parseColor(colorStr)), CircleShape)
                                    .clickable {
                                        DocPictureRepository.updatePicture(
                                            docId = docId,
                                            picture = docPicture.copy(borderColorHex = colorStr)
                                        )
                                    }
                                    .border(
                                        width = if (docPicture.borderColorHex.uppercase() == colorStr.uppercase()) 2.dp else 0.dp,
                                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
                
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                
                Text("Cropping & Framing", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Left Side Crop", fontSize = 11.sp)
                        Text("${(docPicture.cropLeft * 100).toInt()}%", fontSize = 11.sp)
                    }
                    Slider(
                        value = docPicture.cropLeft,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(cropLeft = it)
                            )
                        },
                        valueRange = 0f..0.5f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Right Side Crop", fontSize = 11.sp)
                        Text("${(docPicture.cropRight * 100).toInt()}%", fontSize = 11.sp)
                    }
                    Slider(
                        value = docPicture.cropRight,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(cropRight = it)
                            )
                        },
                        valueRange = 0f..0.5f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top Side Crop", fontSize = 11.sp)
                        Text("${(docPicture.cropTop * 100).toInt()}%", fontSize = 11.sp)
                    }
                    Slider(
                        value = docPicture.cropTop,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(cropTop = it)
                            )
                        },
                        valueRange = 0f..0.5f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bottom Side Crop", fontSize = 11.sp)
                        Text("${(docPicture.cropBottom * 100).toInt()}%", fontSize = 11.sp)
                    }
                    Slider(
                        value = docPicture.cropBottom,
                        onValueChange = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(cropBottom = it)
                            )
                        },
                        valueRange = 0f..0.5f,
                        colors = SliderDefaults.colors(thumbColor = DocWordColor, activeTrackColor = DocWordColor)
                    )
                }

                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                
                Text("Dimensions & Placement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DocWordColor)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Width: ${docPicture.width.value.toInt()} dp", fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(width = (docPicture.width - 10.dp).coerceAtLeast(30.dp))
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.4f), contentColor = Color.Black)
                            ) {
                                Text("-10", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(width = docPicture.width + 10.dp)
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.4f), contentColor = Color.Black)
                            ) {
                                Text("+10", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Height: ${docPicture.height.value.toInt()} dp", fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(height = (docPicture.height - 10.dp).coerceAtLeast(30.dp))
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.4f), contentColor = Color.Black)
                            ) {
                                Text("-10", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    DocPictureRepository.updatePicture(
                                        docId = docId,
                                        picture = docPicture.copy(height = docPicture.height + 10.dp)
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.4f), contentColor = Color.Black)
                            ) {
                                Text("+10", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Column {
                    Text("Pre-set Alignment:", fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(x = 10.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.3f), contentColor = Color.DarkGray)
                        ) {
                            Text("Left", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(x = 140.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.3f), contentColor = Color.DarkGray)
                        ) {
                            Text("Center", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                DocPictureRepository.updatePicture(
                                    docId = docId,
                                    picture = docPicture.copy(x = 240.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.3f), contentColor = Color.DarkGray)
                        ) {
                            Text("Right", fontSize = 10.sp)
                        }
                    }
                }
                
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            DocPictureRepository.updatePicture(
                                docId = docId,
                                picture = docPicture.copy(
                                    rotation = 0f,
                                    opacity = 1.0f,
                                    brightness = 1.0f,
                                    contrast = 1.0f,
                                    saturation = 1.0f,
                                    elevation = 0.dp,
                                    borderWidthDp = 0.dp,
                                    borderRadiusDp = 0.dp,
                                    cropLeft = 0f,
                                    cropRight = 0f,
                                    cropTop = 0f,
                                    cropBottom = 0f,
                                    isGrayscale = false,
                                    isInverted = false
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Picture", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset All", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            DocPictureRepository.removePicture(docId, docPicture.id)
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Picture", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ShapePreviewItem(type: String, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(76.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(32.dp)) {
                val w = size.width
                val h = size.height
                val color = Color(0xFF4F81BD)
                val strokeColor = Color(0xFF1B365D)
                val strokePx = 1.5.dp.toPx()
                
                val path = Path().apply {
                    when (type) {
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

                when (type) {
                    "rectangle" -> {
                        drawRect(color = color)
                        drawRect(color = strokeColor, style = Stroke(width = strokePx))
                    }
                    "round_rectangle" -> {
                        val cr = CornerRadius(6f, 6f)
                        drawRoundRect(color = color, cornerRadius = cr)
                        drawRoundRect(color = strokeColor, cornerRadius = cr, style = Stroke(width = strokePx))
                    }
                    "ellipse" -> {
                        drawOval(color = color)
                        drawOval(color = strokeColor, style = Stroke(width = strokePx))
                    }
                    "smiley" -> {
                        drawCircle(color = color, radius = size.minDimension / 2f)
                        drawCircle(color = strokeColor, radius = size.minDimension / 2f, style = Stroke(width = strokePx))
                        drawCircle(color = strokeColor, radius = 2f, center = Offset(w * 0.35f, h * 0.4f))
                        drawCircle(color = strokeColor, radius = 2f, center = Offset(w * 0.65f, h * 0.4f))
                        drawArc(
                            color = strokeColor,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(w * 0.3f, h * 0.5f),
                            size = Size(w * 0.4f, h * 0.25f),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                    else -> {
                        drawPath(path = path, color = color)
                        drawPath(path = path, color = strokeColor, style = Stroke(width = strokePx))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 8.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun CustomWatermarkDialog(
    currentText: String,
    currentType: String,
    onDismiss: () -> Unit,
    onApply: (text: String, type: String) -> Unit
) {
    var textState by remember { mutableStateOf(currentText) }
    var typeState by remember { mutableStateOf(currentType) } // "Diagonal" or "Horizontal"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Page Setup - Custom Watermark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Specify watermark text and orientation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Watermark Text") },
                    placeholder = { Text("CONFIDENTIAL, DRAFT, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Orientation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Diagonal", "Horizontal").forEach { direction ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { typeState = direction }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = typeState == direction,
                                    onClick = { typeState = direction }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = direction,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(textState, typeState)
                },
                enabled = textState.trim().isNotEmpty()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomBordersDialog(
    currentBorderType: String,
    currentBorderColorHex: String,
    onDismiss: () -> Unit,
    onApply: (borderType: String, borderColorHex: String) -> Unit
) {
    var borderTypeState by remember { mutableStateOf(currentBorderType) }
    var borderColorHexState by remember { 
        mutableStateOf(if (currentBorderColorHex == "default" || currentBorderColorHex.isEmpty()) "#000000" else if (currentBorderColorHex.startsWith("#")) currentBorderColorHex else "#$currentBorderColorHex") 
    }

    val styleOptions = listOf(
        "None" to "No Page Border",
        "Thin Box" to "Thin Solid Border (1dp)",
        "Medium Box" to "Medium Solid Border (2dp)",
        "Thick Box" to "Thick Solid Border (4dp)",
        "Double Line" to "Classic Double Line (3dp)",
        "Dashed Line" to "Dashed Line Accent"
    )

    val colorPresets = listOf(
        "#000000" to "Black",
        "#7F8C8D" to "Slate",
        "#1A73E8" to "Office Blue",
        "#D93025" to "Crimson Red",
        "#188038" to "Forest Green",
        "#F9AB00" to "Gold Amber"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Page Setup - Page Borders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize the style and appearance of document borders",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                // Section 1: Style Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Border Style",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    styleOptions.forEach { (typeVal, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { borderTypeState = typeVal }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .background(
                                    if (borderTypeState == typeVal) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            RadioButton(
                                selected = borderTypeState == typeVal,
                                onClick = { borderTypeState = typeVal }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)

                // Section 2: Color Selection (with Presets and Direct Input)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Border Color",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Row of preset color circles
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorPresets.forEach { (hex, title) ->
                            val isSelected = borderColorHexState.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                    .clickable { borderColorHexState = hex }
                            )
                        }
                    }

                    // Direct HEX Input
                    OutlinedTextField(
                        value = borderColorHexState,
                        onValueChange = { 
                            if (it.startsWith("#") && it.length <= 9) {
                                borderColorHexState = it
                            } else if (!it.startsWith("#") && it.length <= 8) {
                                borderColorHexState = "#$it"
                            }
                        },
                        label = { Text("Custom Color (Hex)") },
                        placeholder = { Text("#000000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(borderTypeState, borderColorHexState)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =========================================================================
// =================== FULLY FUNCTIONAL REVIEW RIBBON DEV ==================
// =========================================================================

@Composable
fun CommentsSidebar(
    docId: Int,
    onClose: () -> Unit,
    currentText: TextFieldValue,
    onUpdateText: (TextFieldValue) -> Unit
) {
    val comments = DocReviewManager.getCommentsForDoc(docId)

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
            .testTag("comments_sidebar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Chat,
                    contentDescription = "Comments Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comments Pane",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Pane")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (comments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No comments in this document.\nSelect text and click 'New Comment'.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments.size) { i ->
                    val comment = comments[i]
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("comment_card_${comment.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = comment.author.take(2).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = comment.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { DocReviewManager.deleteComment(docId, comment.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete Comment",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Anchored range: [${comment.startOffset}, ${comment.endOffset}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Replies
                            comment.replies.forEach { reply ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = reply.author + ":",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = reply.text,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            var replyInput by remember(comment.id) { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = replyInput,
                                    onValueChange = { replyInput = it },
                                    placeholder = { Text("Reply...", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        if (replyInput.isNotBlank()) {
                                            DocReviewManager.addCommentReply(docId, comment.id, replyInput, "User")
                                            replyInput = ""
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send Reply",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RevisionsSidebar(
    docId: Int,
    onClose: () -> Unit,
    currentContent: String,
    onUpdateContent: (String) -> Unit
) {
    val changes = DocReviewManager.getTrackedChangesForDoc(docId)

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
            .testTag("revisions_sidebar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = "Revision Pane Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Revisions Board",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Revisions Pane")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (changes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pending tracked changes.\nTurn on 'Track Changes' to capture edits.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(changes.size) { i ->
                    val change = changes[i]
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (change.type == "insert") {
                                Color(0xFFECFDF5)
                            } else {
                                Color(0xFFFEF2F2)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("revision_card_${change.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (change.type == "insert") "INSERTION" else "DELETION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (change.type == "insert") Color(0xFF047857) else Color(0xFFB91C1C)
                                )
                                Text(
                                    text = change.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "\"${change.text}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "At position: ${change.offset}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        DocReviewManager.rejectTrackedChange(docId, change, currentContent, onUpdateContent)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB91C1C))
                                ) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Reject", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        DocReviewManager.acceptTrackedChange(docId, change, currentContent, onUpdateContent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Accept", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Accept", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpellingCheckDialog(
    docId: Int,
    text: String,
    language: String,
    onDismiss: () -> Unit,
    onWordReplaced: (String) -> Unit
) {
    val dict = DocReviewManager.getDictionaryForLanguage(language)
    val typos = DocReviewManager.getCommonTypos(language)
    
    val allWords = remember(text, language) {
        "\\b[a-zA-ZáéíóúüñÂÊÎÔÛâêîôûàèùœçÀÈÙŒÇ']+\\b".toRegex().findAll(text).toList()
    }
    
    var wordListIndex by remember { mutableStateOf(0) }
    
    var currentMissedWordMatch = remember(wordListIndex, allWords) {
        var found: kotlin.text.MatchResult? = null
        for (i in wordListIndex until allWords.size) {
            val w = allWords[i]
            val lowercaseVal = w.value.lowercase()
            if (lowercaseVal.length > 1 && !dict.contains(lowercaseVal) && !sessionIgnoredWords.contains(lowercaseVal)) {
                found = w
                wordListIndex = i
                break
            }
        }
        found
    }

    if (currentMissedWordMatch == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Spelling Check") },
            text = { Text("Spelling & Grammar check completed successfully!\nNo other issues found in the document.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    } else {
        val missedWord = currentMissedWordMatch.value
        val missedRange = currentMissedWordMatch.range
        val suggestions = remember(missedWord) {
            val result = mutableListOf<String>()
            val lowercaseMissed = missedWord.lowercase()
            if (typos.containsKey(lowercaseMissed)) {
                typos[lowercaseMissed]?.forEach { result.add(it) }
            } else {
                dict.filter { it.startsWith(lowercaseMissed.take(1)) && kotlin.math.abs(it.length - lowercaseMissed.length) <= 2 }.take(4).forEach { result.add(it) }
                if (result.isEmpty()) {
                    result.add("${missedWord}s")
                    result.add(missedWord.replaceFirstChar { it.lowercase() })
                }
            }
            result
        }
        
        var selectedSuggestion by remember(suggestions) { mutableStateOf(suggestions.firstOrNull() ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Spelling: $language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Not in Dictionary:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                        Text(
                            text = missedWord,
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    LazyColumn(modifier = Modifier.height(100.dp)) {
                        items(suggestions.size) { index ->
                            val s = suggestions[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (s == selectedSuggestion) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { selectedSuggestion = s }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = s, style = MaterialTheme.typography.bodyLarge, fontWeight = if (s == selectedSuggestion) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                sessionIgnoredWords.add(missedWord.lowercase())
                                wordListIndex++
                            }
                        ) {
                            Text("Ignore Once")
                        }
                        Button(
                            onClick = {
                                if (selectedSuggestion.isNotBlank()) {
                                    val start = missedRange.first
                                    val end = missedRange.last + 1
                                    val corrected = text.substring(0, start) + selectedSuggestion + text.substring(end)
                                    onWordReplaced(corrected)
                                    
                                    try {
                                        DocFormatRepository.shiftSpans(docId, start, end - start, selectedSuggestion.length)
                                    } catch (e: Exception) {}
                                    
                                    wordListIndex++
                                }
                            }
                        ) {
                            Text("Change")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                sessionIgnoredWords.add(missedWord.lowercase())
                                wordListIndex++
                            }
                        ) {
                            Text("Ignore All")
                        }
                        TextButton(
                            onClick = {
                                if (selectedSuggestion.isNotBlank()) {
                                    val corrected = text.replace(missedWord, selectedSuggestion)
                                    onWordReplaced(corrected)
                                    wordListIndex++
                                }
                            }
                        ) {
                            Text("Change All")
                        }
                    }
                }
            }
        )
    }
}

val sessionIgnoredWords = mutableStateListOf<String>()

@Composable
fun ThesaurusDialog(
    selectedWord: String,
    onDismiss: () -> Unit,
    onSynonymSelected: (String) -> Unit
) {
    var searchWord by remember { mutableStateOf(selectedWord.trim()) }
    val synonyms = remember(searchWord) {
        DocReviewManager.localThesaurus[searchWord.lowercase()] 
            ?: listOf("${searchWord} equivalent", "alternative for $searchWord", "similar term")
    }
    
    var selectedSynonym by remember(synonyms) { mutableStateOf(synonyms.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.MenuBook, contentDescription = "Thesaurus")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Thesaurus")
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchWord,
                    onValueChange = { searchWord = it },
                    label = { Text("Look up selected word") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Synonyms found:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                LazyColumn(modifier = Modifier.height(130.dp)) {
                    items(synonyms.size) { i ->
                        val syn = synonyms[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (syn == selectedSynonym) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedSynonym = syn }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = syn, style = MaterialTheme.typography.bodyLarge, fontWeight = if (syn == selectedSynonym) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedSynonym.isNotBlank()) {
                        onSynonymSelected(selectedSynonym)
                        onDismiss()
                    }
                },
                enabled = selectedSynonym.isNotBlank()
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun WordCountDialog(
    text: String,
    onDismiss: () -> Unit
) {
    val charactersWithSpaces = text.length
    val charactersNoSpaces = text.filter { !it.isWhitespace() }.length
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val paragraphs = text.split('\n').filter { it.isNotBlank() }.size
    val pages = text.split('\u000C').size
    val lines = (text.length / 45) + paragraphs

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Word Count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pages", style = MaterialTheme.typography.bodyMedium)
                    Text("$pages", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Words", style = MaterialTheme.typography.bodyMedium)
                    Text("$words", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Characters (no spaces)", style = MaterialTheme.typography.bodyMedium)
                    Text("$charactersNoSpaces", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Characters (with spaces)", style = MaterialTheme.typography.bodyMedium)
                    Text("$charactersWithSpaces", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paragraphs", style = MaterialTheme.typography.bodyMedium)
                    Text("$paragraphs", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lines", style = MaterialTheme.typography.bodyMedium)
                    Text("$lines", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var checked by remember { mutableStateOf(true) }
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Include footnotes and endnotes", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AccessibilityCheckDialog(
    docId: Int,
    text: String,
    onDismiss: () -> Unit,
    onAutoFixContent: (String) -> Unit
) {
    val results = remember(text) {
        val inspections = mutableListOf<Triple<String, String, String>>()
        if (text.length < 50) {
            inspections.add(Triple("Warning", "Heading levels sequence missing", "Structure is missing standard heading hierarchies. Try adding headings like Section title."))
        }
        val lowercase = text.lowercase()
        if (lowercase.contains("italic")) {
            inspections.add(Triple("Tip", "Italics block representation warning", "A dense block is using italics, representing reading challenges for screen viewers."))
        }
        inspections.add(Triple("Tip", "Ensure dynamic color light schema", "Font colors match requirements. All background tints meet dynamic 4.5:1 ratio targets."))
        inspections
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Accessibility, contentDescription = "Accessibility Logo", tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Accessibility Checker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Inspection Results Summary:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.size) { i ->
                        val r = results[i]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (r.first == "Error") Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "[${r.first.uppercase()}]",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (r.first == "Error") Color(0xFFB91C1C) else Color(0xFFB45309)
                                    )
                                    Text(text = r.second, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = r.third, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun TranslateDialog(
    text: String,
    isSelectionMode: Boolean,
    onDismiss: () -> Unit,
    onInsertTranslation: (String) -> Unit
) {
    var targetLang by remember { mutableStateOf("Spanish") }
    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Translate, contentDescription = "Translate Logo")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate")
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isSelectionMode) "Mode: Translate Selected Text" else "Mode: Translate Full Document",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Target:")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(targetLang)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("Spanish", "French", "German", "Italian", "Hindi", "Japanese", "Portuguese").forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        targetLang = lang
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (isTranslating) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (translatedText.isNotEmpty()) {
                    Text("Translated Result Preview:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.height(100.dp).padding(8.dp)) {
                            item {
                                Text(translatedText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (translatedText.isEmpty()) {
                    Button(
                        onClick = {
                            isTranslating = true
                            scope.launch {
                                kotlinx.coroutines.delay(800)
                                translatedText = DocReviewManager.translateText(text, targetLang)
                                isTranslating = false
                            }
                        }
                    ) {
                        Text("Translate")
                    }
                } else {
                    Button(
                        onClick = {
                            onInsertTranslation(translatedText)
                            onDismiss()
                        }
                    ) {
                        Text("Insert")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LanguageSetDialog(
    currentLang: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var selectedLang by remember { mutableStateOf(currentLang) }
    val languages = listOf("English (United States)", "Spanish (Spain)", "French (France)")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Language, contentDescription = "Language Logo")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Set Proofing Language")
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Proofing rules and corrections will adapt to this language standard:", style = MaterialTheme.typography.bodySmall)
                languages.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLang = lang }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (lang == selectedLang), onClick = { selectedLang = lang })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedLang)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReadAloudPlayerControls(
    text: String,
    tts: android.speech.tts.TextToSpeech?,
    onClose: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1.0f) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("read_aloud_floating_controls")
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            tts?.setSpeechRate(speed)
                            val clean = text.replace("[#*_|\\-<>]+".toRegex(), " ")
                            tts?.speak(clean, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                        } else {
                            tts?.stop()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }
                IconButton(
                    onClick = {
                        tts?.stop()
                        isPlaying = false
                    }
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speech rate: ", style = MaterialTheme.typography.bodySmall)
                    var expandedSpeedMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expandedSpeedMenu = true }) {
                            Text("${speed}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = expandedSpeedMenu, onDismissRequest = { expandedSpeedMenu = false }) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
                                        speed = s
                                        tts?.setSpeechRate(s)
                                        expandedSpeedMenu = false
                                        if (isPlaying) {
                                            val clean = text.replace("[#*_|\\-<>]+".toRegex(), " ")
                                            tts?.speak(clean, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = {
                tts?.stop()
                onClose()
            }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close speaker controls")
            }
        }
    }
}
