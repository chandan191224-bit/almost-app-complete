package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.DocEntity
import com.example.db.DocRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SlideItem(
    val title: String = "Slide Title",
    val body: String = "Slide Body Content",
    val theme: String = "classic", // colors: "classic", "indigo", "orange", "dark"
    val layout: String = "title_body" // layouts: "title_body", "title_slide", "title_only"
)

class DocViewModel(private val repository: DocRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedTypeFilter = MutableStateFlow("all")

    val documents: StateFlow<List<DocEntity>> = repository.allDocs
        .combine(searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.title.contains(query, ignoreCase = true) }
        }
        .combine(selectedTypeFilter) { docs, filter ->
            if (filter == "all") docs else docs.filter { it.type.equals(filter, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDoc = MutableStateFlow<DocEntity?>(null)
    val draftTitle = MutableStateFlow("")
    val draftContent = MutableStateFlow("")
    val pageFormat = MutableStateFlow("Letter")
    val customPageDimensions = MutableStateFlow(Pair(8.5f, 11f))
    val isPlayingPresentation = MutableStateFlow(false)

    // Spreadsheet state
    val selectedCell = MutableStateFlow<String?>(null)
    val sheetData = MutableStateFlow<Map<String, String>>(emptyMap())

    // Slides state
    val slides = MutableStateFlow<List<SlideItem>>(emptyList())
    val currentSlideIndex = MutableStateFlow(0)

    fun togglePresenterMode(isPlaying: Boolean) {
        isPlayingPresentation.value = isPlaying
    }

    fun selectDocument(doc: DocEntity?) {
        selectedDoc.value = doc
        draftTitle.value = doc?.title ?: ""
        draftContent.value = doc?.content ?: ""

        if (doc != null) {
            when (doc.type.lowercase()) {
                "sheet" -> {
                    sheetData.value = deserializeSheetData(doc.content)
                    selectedCell.value = null
                }
                "slide" -> {
                    slides.value = deserializeSlides(doc.content)
                    currentSlideIndex.value = 0
                }
            }
        } else {
            sheetData.value = emptyMap()
            selectedCell.value = null
            slides.value = emptyList()
            currentSlideIndex.value = 0
        }
    }

    fun updateDraftTitle(title: String) {
        draftTitle.value = title
        val cur = selectedDoc.value
        if (cur != null) {
            val updated = cur.copy(title = title, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updated
            viewModelScope.launch {
                repository.update(updated)
            }
        }
    }

    fun updateDraftContent(content: String) {
        draftContent.value = content
        val cur = selectedDoc.value
        if (cur != null) {
            val updated = cur.copy(content = content, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updated
            viewModelScope.launch {
                repository.update(updated)
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTypeFilter(filter: String) {
        selectedTypeFilter.value = filter
    }

    fun deleteDocument(doc: DocEntity) {
        viewModelScope.launch {
            repository.delete(doc)
            if (selectedDoc.value?.id == doc.id) {
                selectDocument(null)
            }
        }
    }

    fun toggleFavorite(doc: DocEntity) {
        viewModelScope.launch {
            val updated = doc.copy(isFavorite = !doc.isFavorite, updatedAt = System.currentTimeMillis())
            repository.update(updated)
            if (selectedDoc.value?.id == doc.id) {
                selectedDoc.value = updated
            }
        }
    }

    fun createNewDocument(title: String, type: String) {
        viewModelScope.launch {
            val contentStr = when (type.lowercase()) {
                "sheet" -> ""
                "slide" -> serializeSlides(listOf(
                    SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide")
                ))
                else -> "Start writing your document here..."
            }
            val newDoc = DocEntity(
                title = title.ifBlank { "Untitled ${type.uppercase()}" },
                type = type.lowercase(),
                content = contentStr
            )
            val id = repository.insert(newDoc)
            val createdDoc = newDoc.copy(id = id.toInt())
            selectDocument(createdDoc)
        }
    }

    fun setPageFormat(format: String) {
        pageFormat.value = format
    }

    fun setCustomPageDimensions(width: Float, height: Float) {
        customPageDimensions.value = Pair(width, height)
    }

    fun selectCell(cell: String?) {
        selectedCell.value = cell
    }

    fun updateCellExpression(cell: String?, expression: String) {
        if (cell != null) {
            val updatedMap = sheetData.value.toMutableMap()
            updatedMap[cell] = expression
            sheetData.value = updatedMap
            val curDoc = selectedDoc.value
            if (curDoc != null && curDoc.type == "sheet") {
                val serialized = serializeSheetData(updatedMap)
                val updatedDoc = curDoc.copy(content = serialized, updatedAt = System.currentTimeMillis())
                selectedDoc.value = updatedDoc
                viewModelScope.launch {
                    repository.update(updatedDoc)
                }
            }
        }
    }

    fun getCellValue(cellRef: String): String {
        val expr = sheetData.value[cellRef] ?: return ""
        if (expr.startsWith("=")) {
            val rawFormula = expr.substring(1).trim().uppercase()
            if (rawFormula.startsWith("SUM(")) {
                val range = rawFormula.removePrefix("SUM(").removeSuffix(")")
                return evaluateSum(range).toString()
            }
            if (rawFormula.startsWith("AVERAGE(")) {
                val range = rawFormula.removePrefix("AVERAGE(").removeSuffix(")")
                val (sum, count) = evaluateSumAndCount(range)
                return if (count > 0) String.format("%.2f", sum / count) else "0"
            }
            return expr
        }
        return expr
    }

    private fun getCellsInRange(range: String): List<String> {
        val pts = range.split(":")
        if (pts.size != 2) return emptyList()
        val start = pts[0].trim()
        val end = pts[1].trim()
        val startCol = start.firstOrNull { it.isLetter() } ?: return emptyList()
        val startRow = start.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()
        val endCol = end.firstOrNull { it.isLetter() } ?: return emptyList()
        val endRow = end.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()

        val list = mutableListOf<String>()
        val colStart = minOf(startCol.code, endCol.code)
        val colEnd = maxOf(startCol.code, endCol.code)
        val rowStart = minOf(startRow, endRow)
        val rowEnd = maxOf(startRow, endRow)

        for (c in colStart..colEnd) {
            for (r in rowStart..rowEnd) {
                list.add("${c.toChar()}$r")
            }
        }
        return list
    }

    private fun evaluateSum(range: String): Double {
        var sum = 0.0
        getCellsInRange(range).forEach { cell ->
            val v = sheetData.value[cell] ?: "0"
            sum += v.toDoubleOrNull() ?: 0.0
        }
        return sum
    }

    private fun evaluateSumAndCount(range: String): Pair<Double, Int> {
        var sum = 0.0
        var count = 0
        getCellsInRange(range).forEach { cell ->
            val v = sheetData.value[cell] ?: "0"
            sum += v.toDoubleOrNull() ?: 0.0
            count++
        }
        return Pair(sum, count)
    }

    // slides logic
    fun addNewSlide() {
        val updatedSlides = slides.value.toMutableList()
        updatedSlides.add(SlideItem("New Slide", "Double click to edit text", "indigo", "title_body"))
        slides.value = updatedSlides
        saveSlidesToDoc(updatedSlides)
        currentSlideIndex.value = updatedSlides.size - 1
    }

    fun deleteSlide(index: Int) {
        val updatedSlides = slides.value.toMutableList()
        if (updatedSlides.size > 1 && index in updatedSlides.indices) {
            updatedSlides.removeAt(index)
            slides.value = updatedSlides
            saveSlidesToDoc(updatedSlides)
            currentSlideIndex.value = maxOf(0, index - 1)
        }
    }

    fun selectSlide(index: Int) {
        if (index in slides.value.indices) {
            currentSlideIndex.value = index
        }
    }

    fun updateSlideContent(title: String, body: String, theme: String, layout: String) {
        val idx = currentSlideIndex.value
        val updatedSlides = slides.value.toMutableList()
        if (idx in updatedSlides.indices) {
            updatedSlides[idx] = SlideItem(title, body, theme, layout)
            slides.value = updatedSlides
            saveSlidesToDoc(updatedSlides)
        }
    }

    private fun saveSlidesToDoc(list: List<SlideItem>) {
        val curDoc = selectedDoc.value
        if (curDoc != null && curDoc.type == "slide") {
            val serialized = serializeSlides(list)
            val updatedDoc = curDoc.copy(content = serialized, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updatedDoc
            viewModelScope.launch {
                repository.update(updatedDoc)
            }
        }
    }

    // Helper functions
    private fun serializeSheetData(data: Map<String, String>): String {
        return data.entries.joinToString(";;") { "${it.key}=${it.value}" }
    }

    private fun deserializeSheetData(content: String): Map<String, String> {
        if (content.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            content.split(";;").forEach { entry ->
                val pts = entry.split("=")
                if (pts.size >= 2) {
                    map[pts[0]] = pts.subList(1, pts.size).joinToString("=")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun serializeSlides(slides: List<SlideItem>): String {
        return slides.joinToString(";;;") { "${it.title}||${it.body}||${it.theme}||${it.layout}" }
    }

    private fun deserializeSlides(content: String): List<SlideItem> {
        if (content.isBlank()) {
            return listOf(
                SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide"),
                SlideItem("Overview", "1. Beautiful styles\n2. Modern presentation\n3. High-fidelity layouts", "orange", "title_body")
            )
        }
        val list = mutableListOf<SlideItem>()
        try {
            content.split(";;;").forEach { entry ->
                val pts = entry.split("||")
                if (pts.size == 4) {
                    list.add(SlideItem(pts[0], pts[1], pts[2], pts[3]))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            list.add(SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide"))
        }
        return list
    }
}
