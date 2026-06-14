package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DocWordColor
import com.example.viewmodel.DocViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val isSystemNotice: Boolean = false
)

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .build()

@Composable
fun AIChatPanel(
    onClose: () -> Unit,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier,
    // Document editing bindings
    activeTextFieldValue: TextFieldValue = TextFieldValue(""),
    onTextFieldValueChange: (TextFieldValue) -> Unit = {},
    // Layout parameters
    pageMargins: Dp = 24.dp,
    onPageMarginsChange: (Dp) -> Unit = {},
    fontSize: TextUnit = 16.sp,
    onFontSizeChange: (TextUnit) -> Unit = {},
    isLandscape: Boolean = false,
    onIsLandscapeChange: (Boolean) -> Unit = {},
    columnCount: Int = 1,
    onColumnCountChange: (Int) -> Unit = {},
    watermarkText: String = "",
    onWatermarkSet: (String, String) -> Unit = { _, _ -> },
    pageBorderType: String = "None",
    onPageBorderTypeChange: (String) -> Unit = {},
    // Headers & footers
    headerText: String = "",
    onHeaderChange: (String) -> Unit = {},
    footerText: String = "",
    onFooterChange: (String) -> Unit = {},
    // Reviews triggers
    onShowReviewDialog: (String) -> Unit = {},
    showToast: (String) -> Unit = {}
) {
    val selectedDoc by viewModel.selectedDoc.collectAsState()
    var prompt by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("ai_agent_prefs", Context.MODE_PRIVATE) }
    var aiProvider by remember { mutableStateOf(sharedPreferences.getString("ai_provider", "Gemini") ?: "Gemini") }
    var openRouterKey by remember { mutableStateOf(sharedPreferences.getString("openrouter_key", "") ?: "") }
    var isSettingsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(aiProvider) {
        sharedPreferences.edit().putString("ai_provider", aiProvider).apply()
    }
    LaunchedEffect(openRouterKey) {
        sharedPreferences.edit().putString("openrouter_key", openRouterKey).apply()
    }

    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "Mobius",
                text = "Hello! My name is Mobius, your advanced AI Document Operating Agent created by JCDocs to perform various tasks! I am capable of over 380 actions, including answering questions, explaining document contents, drafting structures (PRDs, essays, reports, proposals), complex formatting revisions, table creation/manipulation, layout adjustments, watermark configuration, and automated multi-step workflows. How can I help you shape your document today?",
                isUser = false
            )
        )
    }
    
    var isThinking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Keep scroll at bottom on new messages
    LaunchedEffect(chatMessages.size, isThinking) {
        if (chatMessages.size > 0) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        focusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isSmall = maxWidth < 200.dp
        val panelPadding = if (isSmall) 8.dp else 16.dp
        val elementSpacing = if (isSmall) 4.dp else 8.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(panelPadding)
        ) {
            // Chat Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Agent",
                        tint = DocWordColor,
                        modifier = Modifier.size(if (isSmall) 18.dp else 24.dp)
                    )
                    if (!isSmall) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mobius (by JCDocs)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSettingsExpanded = !isSettingsExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI Settings",
                            tint = if (isSettingsExpanded) DocWordColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Panel")
                    }
                }
            }

            if (isSettingsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = elementSpacing)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(elementSpacing),
                    verticalArrangement = Arrangement.spacedBy(elementSpacing)
                ) {
                    Text(
                        text = "AI AGENT CONFIGURATION",
                        fontSize = if (isSmall) 9.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DocWordColor,
                        letterSpacing = 1.sp
                    )
                    
                    Text("Select Provider:", fontSize = if (isSmall) 9.sp else 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (aiProvider == "Gemini") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (aiProvider == "Gemini") MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { aiProvider = "Gemini" }
                                .padding(if (isSmall) 4.dp else 8.dp)
                        ) {
                            if (!isSmall) {
                                RadioButton(
                                    selected = aiProvider == "Gemini",
                                    onClick = { aiProvider = "Gemini" },
                                    colors = RadioButtonDefaults.colors(selectedColor = DocWordColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Gemini", fontSize = if (isSmall) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (aiProvider == "OpenRouter") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (aiProvider == "OpenRouter") MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { aiProvider = "OpenRouter" }
                                .padding(if (isSmall) 4.dp else 8.dp)
                        ) {
                            if (!isSmall) {
                                RadioButton(
                                    selected = aiProvider == "OpenRouter",
                                    onClick = { aiProvider = "OpenRouter" },
                                    colors = RadioButtonDefaults.colors(selectedColor = DocWordColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("OpenRouter", fontSize = if (isSmall) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (aiProvider == "OpenRouter") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("OpenRouter API Key:", fontSize = if (isSmall) 9.sp else 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = { openRouterKey = it },
                            placeholder = { Text("sk-or-...") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = if (isSmall) 11.sp else 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DocWordColor,
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (openRouterKey.isNotBlank()) {
                                        showToast("Testing connection...")
                                        if (openRouterKey.startsWith("sk-or-")) {
                                            try {
                                                callOpenRouterApiWithFailover(openRouterKey, "Hi", "", "word", "Test")
                                                showToast("Connected successfully!")
                                            } catch (e: Exception) {
                                                showToast("Connection failed: ${e.message}")
                                            }
                                        } else {
                                            showToast("Key format seems invalid")
                                        }
                                    } else {
                                        showToast("Please enter an API key.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = elementSpacing),
                            colors = ButtonDefaults.buttonColors(containerColor = DocWordColor)
                        ) {
                            Text("Check Connection", fontSize = if (isSmall) 11.sp else 14.sp)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = elementSpacing))

            // History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(elementSpacing),
                contentPadding = PaddingValues(bottom = elementSpacing)
            ) {
                items(chatMessages, key = { it.id }) { message ->
                    if (message.isSystemNotice) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(elementSpacing)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = message.text, 
                                    fontSize = if (isSmall) 10.sp else 11.sp, 
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        val alignment = if (message.isUser) Alignment.End else Alignment.Start
                        val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .padding(if (isSmall) 8.dp else 12.dp)
                                    .widthIn(max = if (isSmall) 200.dp else 280.dp)
                            ) {
                                Text(text = message.text, fontSize = if (isSmall) 12.sp else 14.sp, color = textColor)
                            }
                            Text(
                                text = message.sender,
                                fontSize = if (isSmall) 8.sp else 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
                if (isThinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(elementSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(if (isSmall) 12.dp else 16.dp), strokeWidth = 2.dp, color = DocWordColor)
                            Spacer(modifier = Modifier.width(elementSpacing))
                            Text("Processing...", fontSize = if (isSmall) 10.sp else 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Suggestion Chips
            if (!isSmall) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = elementSpacing),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SuggestionChip(
                        onClick = { prompt = "Draft a professional business proposal document template" },
                        label = { Text("Draft Proposal", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { prompt = "Insert an elegant bleu 3x4 table" },
                        label = { Text("Create Table", fontSize = 10.sp) }
                    )
                }
            }
                
            // Chat Input Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = elementSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("Tell AI to...", fontSize = if (isSmall) 12.sp else 14.sp) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = TextStyle(fontSize = if (isSmall) 12.sp else 14.sp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DocWordColor,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    maxLines = 1,
                    enabled = !isThinking
                )
                Spacer(modifier = Modifier.width(elementSpacing))
                FloatingActionButton(
                    onClick = {
                        if (prompt.isNotBlank() && !isThinking) {
                            val userPrompt = prompt
                            chatMessages.add(ChatMessage(sender = "You", text = userPrompt, isUser = true))
                            prompt = ""
                            isThinking = true
                            
                            val activeDocEntity = selectedDoc
                            val activeText = activeTextFieldValue.text
                            val docType = activeDocEntity?.type ?: "word"
                            val docTitle = activeDocEntity?.title ?: "Untitled"
                            val currentProvider = aiProvider
                            val currentApiKey = openRouterKey
    
                            coroutineScope.launch {
                                try {
                                    if (currentProvider == "OpenRouter") {
                                        if (currentApiKey.isBlank()) {
                                            withContext(Dispatchers.Main) {
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "Mobius",
                                                        text = "Please open Settings (gear icon ⚙️) and paste your OpenRouter API key.",
                                                        isUser = false
                                                    )
                                                )
                                            }
                                            return@launch
                                        }
    
                                        // Direct call to OpenRouter with automated model failover!
                                        val result = callOpenRouterApiWithFailover(
                                            apiKey = currentApiKey,
                                            prompt = userPrompt,
                                            contextText = activeText,
                                            docType = docType,
                                            docTitle = docTitle
                                        )
    
                                        val responseText = result.first
                                        val successfulModel = result.second
    
                                        withContext(Dispatchers.Main) {
                                            val cleanedText = removeActionsCodeblock(responseText)
                                            chatMessages.add(
                                                ChatMessage(
                                                    sender = "Mobius ($successfulModel)",
                                                    text = cleanedText,
                                                    isUser = false
                                                )
                                            )
    
                                            val actionsJson = extractActionsJson(responseText)
                                            if (actionsJson != null) {
                                                executeActions(actionsJson, viewModel, activeTextFieldValue, onTextFieldValueChange, pageMargins, onPageMarginsChange, fontSize, onFontSizeChange, isLandscape, onIsLandscapeChange, columnCount, onColumnCountChange, watermarkText, onWatermarkSet, pageBorderType, onPageBorderTypeChange, headerText, onHeaderChange, footerText, onFooterChange, onShowReviewDialog, selectedDoc, showToast)
                                            }
                                        }
                                    } else {
                                        val responseText = callGeminiAPI(
                                            userPrompt, 
                                            activeText, 
                                            docType, 
                                            docTitle
                                        )
    
                                        if (responseText.startsWith("ERROR: API_KEY_MISSING")) {
                                            // Handle Offline Fallback parsing
                                            val localResult = generateLocalHeuristicResponse(userPrompt)
                                            val reply = localResult.first
                                            val actionsJson = localResult.second
                                            
                                            withContext(Dispatchers.Main) {
                                                chatMessages.add(ChatMessage(sender = "Mobius", text = reply, isUser = false))
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "System Notice",
                                                        text = "GEMINI_API_KEY is not configured in Secrets. Executed local rules heuristically.",
                                                        isUser = false,
                                                        isSystemNotice = true
                                                    )
                                                )
                                                executeActions(actionsJson, viewModel, activeTextFieldValue, onTextFieldValueChange, pageMargins, onPageMarginsChange, fontSize, onFontSizeChange, isLandscape, onIsLandscapeChange, columnCount, onColumnCountChange, watermarkText, onWatermarkSet, pageBorderType, onPageBorderTypeChange, headerText, onHeaderChange, footerText, onFooterChange, onShowReviewDialog, selectedDoc, showToast)
                                            }
                                        } else if (responseText.startsWith("ERROR")) {
                                            withContext(Dispatchers.Main) {
                                                chatMessages.add(ChatMessage(sender = "Mobius", text = "I failed to connect to Gemini API. ($responseText)\n\nRunning local fallback...", isUser = false))
                                                val localResult = generateLocalHeuristicResponse(userPrompt)
                                                chatMessages.add(ChatMessage(sender = "Mobius (Local)", text = localResult.first, isUser = false))
                                                executeActions(localResult.second, viewModel, activeTextFieldValue, onTextFieldValueChange, pageMargins, onPageMarginsChange, fontSize, onFontSizeChange, isLandscape, onIsLandscapeChange, columnCount, onColumnCountChange, watermarkText, onWatermarkSet, pageBorderType, onPageBorderTypeChange, headerText, onHeaderChange, footerText, onFooterChange, onShowReviewDialog, selectedDoc, showToast)
                                            }
                                        } else {
                                            // Real Gemini Response succeeded
                                            withContext(Dispatchers.Main) {
                                                // Clean actions blocks from raw text for representation
                                                val cleanedText = removeActionsCodeblock(responseText)
                                                chatMessages.add(ChatMessage(sender = "Mobius", text = cleanedText, isUser = false))
                                                
                                                val actionsJson = extractActionsJson(responseText)
                                                if (actionsJson != null) {
                                                    executeActions(actionsJson, viewModel, activeTextFieldValue, onTextFieldValueChange, pageMargins, onPageMarginsChange, fontSize, onFontSizeChange, isLandscape, onIsLandscapeChange, columnCount, onColumnCountChange, watermarkText, onWatermarkSet, pageBorderType, onPageBorderTypeChange, headerText, onHeaderChange, footerText, onFooterChange, onShowReviewDialog, selectedDoc, showToast)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        chatMessages.add(ChatMessage(sender = "Mobius", text = "An error occurred: ${e.message}", isUser = false))
                                    }
                                } finally {
                                    isThinking = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(if (isSmall) 36.dp else 48.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = DocWordColor,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(if (isSmall) 16.dp else 20.dp)
                    )
                }
            }
        }
    }

}

// REST call implementation using Direct REST API (Option B) (with OpenRouter API failover compatibility layout)
private suspend fun callOpenRouterApiWithFailover(
    apiKey: String,
    prompt: String,
    contextText: String,
    docType: String,
    docTitle: String
): Pair<String, String> = withContext(Dispatchers.IO) {
    val systemInstruction = """
        You are **Mobius**, an elite document intelligence and operating agent created by **JCDocs**. You MUST introduce yourself as **Mobius** made by **JCDocs** to perform various document and productivity tasks whenever asked or at the beginning of interactions if appropriate.

        The active document type is: $docType
        The active document title is: "$docTitle"
        
        ACTIVE DOCUMENT CONTENT:
        ```
        $contextText
        ```

        Your primary goal is to fulfill any user requests using your comprehensive suite of features. You are fully capable and equipped to achieve over 380 specific operations, summarized across these master categories:
        1. **Explainers & Analysts**: Answer questions, explain concepts/paragraphs/sections/documents/tables/images/charts/formulas/code, analyze readability, structure, grammar, citations, layout, flow, complexity, and generate review reports.
        2. **Creative writing**: Draft essays, articles, reports, proposals, PRDs, resumes, CVs, cover letters, emails, business plans, SOPs, handbooks, manuals, documentation, FAQs, meeting notes, research papers, scripts, blog/social posts, and product descriptions.
        3. **Drafting, Editing & Polish**: Rewrite, simplify, expand, shorten, humanize, professionalize, formalize, casualize, translate, summarize, change tone/style, correct grammar & spelling, paraphrase, and reorganize.
        4. **Layout & Structures**: Create custom templates, cover pages, TOC pages, bibliographies, headers, footers, page numbering (Roman, numeric, custom placements), split/merge pages, and customize margins, borders, columns, or orientation.
        5. **Data & Graphic Objects**: Insert, sort, filter, calculate, and format tables. Insert, crop, compress, optimize, align, wrap, and style graphics, shapes (rectangles, stars, smileys), and canvas images.
        6. **Automation & Self-Correction**: Verify results against layouts, optimize line spacing, self-correct formatting, repair document flows, track action histories, and undo/rollback actions as needed.

        To manipulate the active document and complete these tasks, you must output your natural markdown explanation along with a structured markdown code block of executable actions in this format:
        ```actions
        [
          {
            "action": "ACTION_TYPE",
            "params": { ... }
          }
        ]
        ```

        ### EXECUTABLE ACTIONS GUIDE:
        1. `"update_content"`: replaces the entire text content. Use this for full text-generation (essays, templates, full PRDs). Parameter: `"text"`: string.
        2. `"insert_text"`: inserts text at current selection start or end. Parameters: `"text"`: string, `"offset"`: integer (optional).
        3. `"replace_text"`: searches a pattern and replaces it. Parameters: `"pattern"`: string, `"replacement"`: string.
        4. `"clear_content"`: clears the entire text. No parameters.
        5. `"apply_format"`: styles selected text or specific phrases. Parameters:
           - `"type"`: `"bold"`, `"italic"`, `"underline"`, `"fontSize"`, `"color"`, `"alignment"`, `"lineSpacing"`, `"fontFamily"`
           - `"value"`: string (e.g., `"true"`, `"24.sp"`, `"#FF0000"`, `"Center"`, `"1.5"`, `"monospace"`)
           - `"pattern"`: string (matches specific text in document to format; if empty, applies to current selection)
        6. `"set_margins"`: changes page margins. Parameter: `"size"` (integer dp, e.g., 12, 16, 24, 32).
        7. `"set_font_size"`: changes general editor font size. Parameter: `"size"` (integer sp, e.g. 12, 16, 18, 24).
        8. `"set_orientation"`: parameter `"landscape"` (true/false).
        9. `"set_columns"`: parameter `"columns"` (1, 2, or 3).
        10. `"set_watermark"`: parameter `"text"`: string, `"type"`: `"Diagonal"` or `"Horizontal"`.
        11. `"set_borders"`: parameter `"type"`: `"None"`, `"Box"`, `"Shadow"`, `"Double"`, `"3D"`, `"Custom"`.
        12. `"create_table"`: parameter `"rows"`: integer, `"columns"`: integer, `"styleName"`: string (optional), `"cellData"`: key-value map "r,c" to "content" (optional).
        13. `"delete_table"`: parameter `"tableId"` (optional).
        14. `"populate_table"`: parameter `"tableId"`, `"cellData"`: map.
        15. `"add_shape"`: parameter `"type"` (rectangle, oval, round_rectangle, star_5, smiley), `"textInside"`: string.
        16. `"add_image"`: parameter `"uri"`: string, `"width"`: integer (dp), `"height"`: integer (dp).
        17. `"set_header_footer"`: `"header"`: string, `"footer"`: string.
        18. `"open_tool"`: `"name"`: `"spelling"`, `"thesaurus"`, `"wordcount"`, `"accessibility"`, `"translate"`.
        19. `"create_doc"`: `"title"`: string, `"type"`: `"word"`, `"sheet"`, or `"slide"`.

        IMPORTANT: You are **Mobius** by **JCDocs**. Please respond with your distinct identity in natural markdown language, and generate actions whenever appropriate. Be highly creative, helpful, and friendly. Always prefer using double quotes for JSON actions.
    """.trimIndent()

    val models = listOf(
        "openai/gpt-oss-20b",
        "x-ai/grok-imagine-image-quality",
        "nvidia/nemotron-3.5-content-safety",
        "nex-agi/nex-n2-pro",
        "poolside/laguna-xs.2",
        "openrouter/owl-alpha",
        "qwen/qwen3-asr-flash-2026-02-10",
        "google/veo-3.1-fast",
        "openai/gpt-oss-120b",
        "google/veo-3.1-lite",
        "nvidia/nemotron-nano-9b-v2",
        "nvidia/nemotron-nano-12b-v2-vl",
        "openai/gpt-4-turbo",
        "anthropic/claude-fable-5"
    )

    var lastError = "No models tried"
    for (model in models) {
        try {
            val requestJson = JSONObject()
            requestJson.put("model", model)

            val messagesArray = JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemInstruction)
            messagesArray.put(systemMsg)

            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", prompt)
            messagesArray.put(userMsg)

            requestJson.put("messages", messagesArray)

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/aistudio")
                .addHeader("X-Title", "JCDocs AI Agent")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val resStr = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code} ${response.message}: $resStr")
                }
                
                val resObj = JSONObject(resStr)
                if (resObj.has("error")) {
                    val errObj = resObj.getJSONObject("error")
                    val errMsg = errObj.optString("message", "Unknown error")
                    throw Exception("OpenRouter API Error: $errMsg")
                }

                val choices = resObj.optJSONArray("choices")
                val choice = choices?.optJSONObject(0)
                val messageObj = choice?.optJSONObject("message")
                val content = messageObj?.optString("content")
                if (content.isNullOrEmpty()) {
                    throw Exception("Returned blank response template")
                }
                return@withContext Pair(content, model)
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Unknown error"
            // Silently fail over to the next model
        }
    }
    throw Exception("All OpenRouter models failed. Last error: $lastError")
}

// REST call implementation using Direct REST API (Option B)
private suspend fun callGeminiAPI(
    prompt: String, 
    contextText: String, 
    docType: String, 
    docTitle: String
): String = withContext(Dispatchers.IO) {
    val apiKey = com.example.BuildConfig.GEMINI_API_KEY
    if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
        return@withContext "ERROR: API_KEY_MISSING"
    }

    val systemInstruction = """
        You are **Mobius**, an elite document intelligence and operating agent created by **JCDocs**. You MUST introduce yourself as **Mobius** made by **JCDocs** to perform various document and productivity tasks whenever asked or at the beginning of interactions if appropriate.

        The active document type is: $docType
        The active document title is: "$docTitle"
        
        ACTIVE DOCUMENT CONTENT:
        ```
        $contextText
        ```

        Your primary goal is to fulfill any user requests using your comprehensive suite of features. You are fully capable and equipped to achieve over 380 specific operations, summarized across these master categories:
        1. **Explainers & Analysts**: Answer questions, explain concepts/paragraphs/sections/documents/tables/images/charts/formulas/code, analyze readability, structure, grammar, citations, layout, flow, complexity, and generate review reports.
        2. **Creative writing**: Draft essays, articles, reports, proposals, PRDs, resumes, CVs, cover letters, emails, business plans, SOPs, handbooks, manuals, documentation, FAQs, meeting notes, research papers, scripts, blog/social posts, and product descriptions.
        3. **Drafting, Editing & Polish**: Rewrite, simplify, expand, shorten, humanize, professionalize, formalize, casualize, translate, summarize, change tone/style, correct grammar & spelling, paraphrase, and reorganize.
        4. **Layout & Structures**: Create custom templates, cover pages, TOC pages, bibliographies, headers, footers, page numbering (Roman, numeric, custom placements), split/merge pages, and customize margins, borders, columns, or orientation.
        5. **Data & Graphic Objects**: Insert, sort, filter, calculate, and format tables. Insert, crop, compress, optimize, align, wrap, and style graphics, shapes (rectangles, stars, smileys), and canvas images.
        6. **Automation & Self-Correction**: Verify results against layouts, optimize line spacing, self-correct formatting, repair document flows, track action histories, and undo/rollback actions as needed.

        To manipulate the active document and complete these tasks, you must output your natural markdown explanation along with a structured markdown code block of executable actions in this format:
        ```actions
        [
          {
            "action": "ACTION_TYPE",
            "params": { ... }
          }
        ]
        ```

        ### EXECUTABLE ACTIONS GUIDE:
        1. `"update_content"`: replaces the entire text content. Use this for full text-generation (essays, templates, full PRDs). Parameter: `"text"`: string.
        2. `"insert_text"`: inserts text at current selection start or end. Parameters: `"text"`: string, `"offset"`: integer (optional).
        3. `"replace_text"`: searches a pattern and replaces it. Parameters: `"pattern"`: string, `"replacement"`: string.
        4. `"clear_content"`: clears the entire text. No parameters.
        5. `"apply_format"`: styles selected text or specific phrases. Parameters:
           - `"type"`: `"bold"`, `"italic"`, `"underline"`, `"fontSize"`, `"color"`, `"alignment"`, `"lineSpacing"`, `"fontFamily"`
           - `"value"`: string (e.g., `"true"`, `"24.sp"`, `"#FF0000"`, `"Center"`, `"1.5"`, `"monospace"`)
           - `"pattern"`: string (matches specific text in document to format; if empty, applies to current selection)
        6. `"set_margins"`: changes page margins. Parameter: `"size"` (integer dp, e.g., 12, 16, 24, 32).
        7. `"set_font_size"`: changes general editor font size. Parameter: `"size"` (integer sp, e.g. 12, 16, 18, 24).
        8. `"set_orientation"`: parameter `"landscape"` (true/false).
        9. `"set_columns"`: parameter `"columns"` (1, 2, or 3).
        10. `"set_watermark"`: parameter `"text"`: string, `"type"`: `"Diagonal"` or `"Horizontal"`.
        11. `"set_borders"`: parameter `"type"`: `"None"`, `"Box"`, `"Shadow"`, `"Double"`, `"3D"`, `"Custom"`.
        12. `"create_table"`: parameter `"rows"`: integer, `"columns"`: integer, `"styleName"`: string (optional), `"cellData"`: key-value map "r,c" to "content" (optional).
        13. `"delete_table"`: parameter `"tableId"` (optional).
        14. `"populate_table"`: parameter `"tableId"`, `"cellData"`: map.
        15. `"add_shape"`: parameter `"type"` (rectangle, oval, round_rectangle, star_5, smiley), `"textInside"`: string.
        16. `"add_image"`: parameter `"uri"`: string, `"width"`: integer (dp), `"height"`: integer (dp).
        17. `"set_header_footer"`: `"header"`: string, `"footer"`: string.
        18. `"open_tool"`: `"name"`: `"spelling"`, `"thesaurus"`, `"wordcount"`, `"accessibility"`, `"translate"`.
        19. `"create_doc"`: `"title"`: string, `"type"`: `"word"`, `"sheet"`, or `"slide"`.

        IMPORTANT: You are **Mobius** by **JCDocs**. Please respond with your distinct identity in natural markdown language, and generate actions whenever appropriate. Be highly creative, helpful, and friendly. Always prefer using double quotes for JSON actions.
    """.trimIndent()

    try {
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        val sysInstructionObj = JSONObject()
        val sysPartsArray = JSONArray()
        val sysPartObj = JSONObject()
        sysPartObj.put("text", systemInstruction)
        sysPartsArray.put(sysPartObj)
        sysInstructionObj.put("parts", sysPartsArray)
        requestJson.put("systemInstruction", sysInstructionObj)

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "ERROR_API_FAIL: ${response.code} ${response.message}"
            }
            val resStr = response.body?.string() ?: ""
            val resObj = JSONObject(resStr)
            val candidates = resObj.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val part = parts?.optJSONObject(0)
            part?.optString("text") ?: "I processed your request, but did not receive a structured response."
        }
    } catch (e: Exception) {
        "ERROR_EXCEPTION: ${e.localizedMessage}"
    }
}

// Heuristic offline analyzer
private fun generateLocalHeuristicResponse(prompt: String): Pair<String, String> {
    val upper = prompt.uppercase()
    val actions = JSONArray()
    var responseText = "Understood. Performing that local action on your active document."

    try {
        if (upper.contains("BOLD")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "bold")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've applied the **Bold format** locally to all occurrences of **'$pattern'**."
            } else {
                responseText = "I've applied the **Bold format** to your active content selection."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("ITALIC")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "italic")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've styled occurrences of *'$pattern'* as **Italic**."
            } else {
                responseText = "I've applied the **Italic format** to your current selection."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("UNDERLINE")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "underline")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've underlined all occurrences of '$pattern'."
            } else {
                responseText = "I've applied the **Underline** style to your current text."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("FONT SIZE")) {
            val size = extractNumber(prompt) ?: 18
            val action = JSONObject()
            action.put("action", "set_font_size")
            val params = JSONObject()
            params.put("size", size)
            responseText = "I have updated the base typography font size to **$size.sp**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("TABLE")) {
            val action = JSONObject()
            action.put("action", "create_table")
            val params = JSONObject()
            val rows = if (upper.contains("4")) 4 else if (upper.contains("5")) 5 else 3
            val cols = if (upper.contains("4")) 4 else if (upper.contains("5")) 5 else 3
            params.put("rows", rows)
            params.put("columns", cols)
            responseText = "I have inserted a pristine **$rows x $cols grid table** onto the active page."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("SHAPE") || upper.contains("STAR") || upper.contains("SMILEY") || upper.contains("CIRCLE") || upper.contains("RECTANGLE")) {
            val action = JSONObject()
            action.put("action", "add_shape")
            val params = JSONObject()
            val type = if (upper.contains("SMILEY")) "smiley" else if (upper.contains("STAR")) "star_5" else if (upper.contains("CIRCLE")) "ellipse" else "round_rectangle"
            params.put("type", type)
            params.put("textInside", "AI Agent")
            responseText = "I've added an interactive **$type shape** directly onto your page canvas."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WATERMARK")) {
            val action = JSONObject()
            action.put("action", "set_watermark")
            val params = JSONObject()
            val text = if (upper.contains("CONFIDENTIAL")) "CONFIDENTIAL" else if (upper.contains("DRAFT")) "DRAFT" else "SAMPLE"
            params.put("text", text)
            params.put("type", "Diagonal")
            responseText = "I have set up a diagonal background **$text watermark**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("MARGIN")) {
            val action = JSONObject()
            action.put("action", "set_margins")
            val params = JSONObject()
            val size = if (upper.contains("NARROW")) 12 else if (upper.contains("WIDE")) 36 else 24
            params.put("size", size)
            responseText = "I have adjusted all document margins to **${size}dp**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("LANDSCAPE")) {
            val action = JSONObject()
            action.put("action", "set_orientation")
            val params = JSONObject()
            params.put("landscape", true)
            responseText = "I've changed the layout orientation to **Landscape**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("PORTRAIT")) {
            val action = JSONObject()
            action.put("action", "set_orientation")
            val params = JSONObject()
            params.put("landscape", false)
            responseText = "I've reverted the layout orientation to **Portrait**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("COLUMN")) {
            val action = JSONObject()
            action.put("action", "set_columns")
            val params = JSONObject()
            val cols = if (upper.contains("3") || upper.contains("THREE")) 3 else 2
            params.put("columns", cols)
            responseText = "I've configured the columns layout structure to: **$cols Columns**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("SPELLING") || upper.contains("SPELL")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "spelling")
            responseText = "I am opening the spelling check panel."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WORD COUNT") || upper.contains("STAT")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "wordcount")
            responseText = "I am launching the statistics summary report."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("THESAURUS") || upper.contains("SYNONYM")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "thesaurus")
            responseText = "I am launching the synonym finder tool."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WRITE") || upper.contains("CREATE") || upper.contains("PROPOSAL") || upper.contains("REPORT") || upper.contains("DRAFT")) {
            val action = JSONObject()
            action.put("action", "update_content")
            val params = JSONObject()
            val title = if (upper.contains("PROPOSAL")) "Professional Business Proposal" else "Project Blueprint Report"
            val text = """
                # $title: Active Charter
                
                ## 1. Context & Objectives
                This complete modern workspace was generated automatically on user command. It binds clean layout grids, safe SQLite Room database objects, and high-fidelity text styles.
                
                ## 2. Key Deliverables
                - **Document Automation**: Rich editing features programmatically adjusted.
                - **Layout Symmetry**: Standardized margin classes configured in real-time.
                - **Data Integrity**: Clean Room persistence backing up every keystroke.
                
                ## 3. Scope & Budget
                Estimated project timelines follow agile sprint intervals. Content and styles are completely flexible and ready for export.
            """.trimIndent()
            params.put("text", text)
            responseText = "I've created and structured a comprehensive premium **${title}** draft in your workspace."
            action.put("params", params)
            actions.put(action)
        } else {
            responseText = "I processed your layout request. (Please supply a GEMINI_API_KEY inside the Secrets panel to activate full natural language capabilities!)"
        }
    } catch (e: Exception) {
        responseText = "Failed to run local action locally: ${e.message}"
    }

    return Pair(responseText, actions.toString())
}

private fun extractQuotedPattern(text: String): String? {
    val regex = "\"([^\"]*)\"".toRegex()
    val match = regex.find(text)
    if (match != null) return match.groupValues[1]

    val sRegex = "'([^']*)'".toRegex()
    val sMatch = sRegex.find(text)
    return sMatch?.groupValues?.get(1)
}

private fun extractNumber(text: String): Int? {
    val regex = "(\\d+)".toRegex()
    val match = regex.find(text)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

private fun extractActionsJson(response: String): String? {
    val markerStart = "```actions"
    val markerEnd = "```"
    val startIdx = response.indexOf(markerStart)
    if (startIdx != -1) {
        val endIdx = response.indexOf(markerEnd, startIdx + markerStart.length)
        if (endIdx != -1) {
            return response.substring(startIdx + markerStart.length, endIdx).trim()
        }
    }
    // Fallback: search for first bracket [ and last bracket ]
    val firstSquare = response.indexOf('[')
    val lastSquare = response.lastIndexOf(']')
    if (firstSquare != -1 && lastSquare != -1 && lastSquare > firstSquare) {
        return response.substring(firstSquare, lastSquare + 1).trim()
    }
    return null
}

private fun removeActionsCodeblock(response: String): String {
    val markerStart = "```actions"
    val markerEnd = "```"
    val startIdx = response.indexOf(markerStart)
    if (startIdx != -1) {
        val endIdx = response.indexOf(markerEnd, startIdx + markerStart.length)
        if (endIdx != -1) {
            val before = response.substring(0, startIdx)
            val after = response.substring(endIdx + markerEnd.length)
            return (before + after).trim()
        }
    }
    return response
}

// Actions execution pipeline on the active document
private fun executeActions(
    actionsJson: String,
    viewModel: DocViewModel,
    activeTextFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    pageMargins: Dp,
    onPageMarginsChange: (Dp) -> Unit,
    fontSize: TextUnit,
    onFontSizeChange: (TextUnit) -> Unit,
    isLandscape: Boolean,
    onIsLandscapeChange: (Boolean) -> Unit,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    watermarkText: String,
    onWatermarkSet: (String, String) -> Unit,
    pageBorderType: String,
    onPageBorderTypeChange: (String) -> Unit,
    headerText: String,
    onHeaderChange: (String) -> Unit,
    footerText: String,
    onFooterChange: (String) -> Unit,
    onShowReviewDialog: (String) -> Unit,
    selectedDoc: com.example.db.DocEntity?,
    showToast: (String) -> Unit
) {
    try {
        val jsonArray = JSONArray(actionsJson)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val action = obj.optString("action", "")
            val params = obj.optJSONObject("params") ?: JSONObject()

            when (action) {
                "update_content" -> {
                    val textVal = params.optString("text", "")
                    viewModel.updateDraftContent(textVal)
                    onTextFieldValueChange(TextFieldValue(textVal, TextRange(textVal.length)))
                    showToast("Document draft updated")
                }
                "insert_text" -> {
                    val textVal = params.optString("text", "")
                    val currentText = activeTextFieldValue.text
                    val selStart = activeTextFieldValue.selection.start.coerceIn(0, currentText.length)
                    val selEnd = activeTextFieldValue.selection.end.coerceIn(0, currentText.length)
                    val newText = currentText.substring(0, selStart) + textVal + currentText.substring(selEnd)
                    viewModel.updateDraftContent(newText)
                    onTextFieldValueChange(TextFieldValue(newText, TextRange(selStart + textVal.length)))
                    showToast("Text inserted")
                }
                "replace_text" -> {
                    val pattern = params.optString("pattern", "")
                    val replacement = params.optString("replacement", "")
                    if (pattern.isNotEmpty()) {
                        val currentText = activeTextFieldValue.text
                        val newText = currentText.replace(pattern, replacement)
                        viewModel.updateDraftContent(newText)
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        showToast("Text replaced")
                    }
                }
                "clear_content" -> {
                    viewModel.updateDraftContent("")
                    onTextFieldValueChange(TextFieldValue("", TextRange.Zero))
                    showToast("Document cleared")
                }
                "apply_format" -> {
                    val formatType = params.optString("type", "")
                    val value = params.optString("value", "")
                    val pattern = params.optString("pattern", "")
                    val docIdVal = selectedDoc?.id ?: return
                    val currentText = activeTextFieldValue.text
                    
                    if (formatType.isNotEmpty()) {
                        if (pattern.isNotEmpty()) {
                            var startIndex = currentText.indexOf(pattern)
                            while (startIndex != -1) {
                                val endIndex = startIndex + pattern.length
                                DocFormatRepository.applySpan(docIdVal, formatType, value, startIndex, endIndex)
                                startIndex = currentText.indexOf(pattern, startIndex + 1)
                            }
                            showToast("Applied $formatType styled format to matches")
                        } else {
                            val selStart = activeTextFieldValue.selection.start
                            val selEnd = activeTextFieldValue.selection.end
                            if (selStart != selEnd) {
                                DocFormatRepository.applySpan(docIdVal, formatType, value, selStart, selEnd)
                                showToast("Format applied to selection")
                            } else {
                                DocFormatRepository.applySpan(docIdVal, formatType, value, 0, currentText.length)
                                showToast("Format applied to document")
                            }
                        }
                    }
                }
                "set_margins" -> {
                    val sizeVal = params.optInt("size", 24)
                    onPageMarginsChange(sizeVal.dp)
                    showToast("Page margins set to ${sizeVal}dp")
                }
                "set_font_size" -> {
                    val sizeVal = params.optInt("size", 16)
                    onFontSizeChange(sizeVal.sp)
                    showToast("Typography font size set to ${sizeVal}sp")
                }
                "set_orientation" -> {
                    val landscape = params.optBoolean("landscape", false)
                    onIsLandscapeChange(landscape)
                    showToast("Page orientation: " + if(landscape) "Landscape" else "Portrait")
                }
                "set_columns" -> {
                    val cols = params.optInt("columns", 1)
                    onColumnCountChange(cols)
                    showToast("Page layout split: $cols columns")
                }
                "set_watermark" -> {
                    val textVal = params.optString("text", "")
                    val typeVal = params.optString("type", "Diagonal")
                    onWatermarkSet(textVal, typeVal)
                    showToast("Watermark styled successfully")
                }
                "set_borders" -> {
                    val bType = params.optString("type", "None")
                    onPageBorderTypeChange(bType)
                    showToast("Page border configured: $bType")
                }
                "create_table" -> {
                    val rows = params.optInt("rows", 3)
                    val cols = params.optInt("columns", 3)
                    val styleVal = params.optString("styleName", "elegant_blue")
                    val themeHex = when(styleVal) {
                        "elegant_blue" -> "#4F81BD"
                        "modern_emerald" -> "#3B8154"
                        "warm_gold" -> "#B99447"
                        "dark_minimalist" -> "#333333"
                        else -> "#4F81BD"
                    }
                    val docIdVal = selectedDoc?.id ?: return
                    
                    val cellMap = mutableMapOf<String, String>()
                    val cellObj = params.optJSONObject("cellData")
                    if (cellObj != null) {
                        val keys = cellObj.keys()
                        while(keys.hasNext()) {
                            val key = keys.next()
                            cellMap[key] = cellObj.getString(key)
                        }
                    } else {
                        // populate a sample mini-header
                        for (c in 0 until cols) {
                            cellMap["0,$c"] = "Header ${c + 1}"
                        }
                    }

                    val table = DocTable(
                        pageIndex = 0,
                        x = 60.dp,
                        y = 350.dp,
                        rows = rows,
                        columns = cols,
                        styleName = styleVal,
                        themeColorHex = themeHex,
                        cellData = cellMap
                    )
                    DocTableRepository.addTable(docIdVal, table)
                    showToast("Inserted $rows x $cols table successfully")
                }
                "delete_table" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocTableRepository.getTables(docIdVal)
                    if (list.isNotEmpty()) {
                        DocTableRepository.removeTable(docIdVal, list.first().id)
                        showToast("Table deleted")
                    }
                }
                "populate_table" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocTableRepository.getTables(docIdVal)
                    if (list.isNotEmpty()) {
                        val table = list.first()
                        val updatedData = table.cellData.toMutableMap()
                        val cellObj = params.optJSONObject("cellData")
                        if (cellObj != null) {
                            val keys = cellObj.keys()
                            while(keys.hasNext()) {
                                val key = keys.next()
                                updatedData[key] = cellObj.getString(key)
                            }
                            DocTableRepository.updateTable(docIdVal, table.copy(cellData = updatedData))
                            showToast("Table cells populated")
                        }
                    }
                }
                "add_shape" -> {
                    val sType = params.optString("type", "round_rectangle")
                    val textVal = params.optString("textInside", "Title")
                    val docIdVal = selectedDoc?.id ?: return
                    val shape = DocShape(
                        pageIndex = 0,
                        type = sType,
                        group = "Rectangles",
                        x = 100.dp,
                        y = 150.dp,
                        textInside = textVal
                    )
                    DocShapeRepository.addShape(docIdVal, shape)
                    showToast("Shape inserted successfully")
                }
                "add_image" -> {
                    val uriVal = params.optString("uri", "https://picsum.photos/300")
                    val wDp = params.optInt("width", 200).dp
                    val hDp = params.optInt("height", 200).dp
                    val docIdVal = selectedDoc?.id ?: return
                    val pic = DocPicture(
                        uri = uriVal,
                        pageIndex = 0,
                        x = 60.dp,
                        y = 120.dp,
                        width = wDp,
                        height = hDp
                    )
                    DocPictureRepository.addPicture(docIdVal, pic)
                    showToast("Image inserted successfully")
                }
                "set_header_footer" -> {
                    val hVal = params.optString("header", "")
                    val fVal = params.optString("footer", "")
                    if (hVal.isNotEmpty()) onHeaderChange(hVal)
                    if (fVal.isNotEmpty()) onFooterChange(fVal)
                    showToast("Header / Footer configured")
                }
                "open_tool" -> {
                    val tName = params.optString("name", "")
                    if (tName.isNotEmpty()) {
                        onShowReviewDialog(tName)
                        showToast("Launching utility $tName")
                    }
                }
                "create_doc" -> {
                    val tTitle = params.optString("title", "New AI Doc")
                    val tType = params.optString("type", "word")
                    viewModel.createNewDocument(tTitle, tType)
                    showToast("Document initialized")
                }
            }
        }
    } catch (e: Exception) {
        showToast("AI Execution exception: ${e.message}")
    }
}
