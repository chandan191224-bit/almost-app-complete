package com.example.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DocTable(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val x: Dp,
    val y: Dp,
    val width: Dp = 400.dp,
    val height: Dp = 150.dp,
    val rows: Int = 3,
    val columns: Int = 3,
    val styleName: String = "elegant_blue", // "classic", "elegant_blue", "modern_emerald", "warm_gold", "dark_minimalist"
    val themeColorHex: String = "#5B9BD5",  // Hex color code matching style
    val alternateRows: Boolean = true,
    val hasHeaderRow: Boolean = true,
    val borderWidth: Dp = 1.dp,
    // Key: "row,col", Value: Text content
    val cellData: Map<String, String> = emptyMap(),
    // Key: "row,col", Value: Hex color string (e.g. "#FFFFFF")
    val cellBgColors: Map<String, String> = emptyMap(),
    // Key: "row,col", Value: Bold
    val cellBold: Map<String, Boolean> = emptyMap(),
    // Key: "row,col", Value: Italic
    val cellItalic: Map<String, Boolean> = emptyMap(),
    // Key: "row,col", Value: Underline
    val cellUnderline: Map<String, Boolean> = emptyMap()
) {
    fun getCellText(row: Int, col: Int): String {
        return cellData["$row,$col"] ?: ""
    }

    fun getCellBgColor(row: Int, col: Int): String? {
        val specific = cellBgColors["$row,$col"]
        if (specific != null) return specific
        
        // Return alternating or header default background if not specified
        if (hasHeaderRow && row == 0) {
            return themeColorHex
        }
        if (alternateRows && row % 2 == 1) {
            // Very light shade of the theme color or a light grey tint
            return "#F2F4F8"
        }
        return "#FFFFFF"
    }
}

object DocTableRepository {
    private val tablesMap = mutableMapOf<Int, SnapshotStateList<DocTable>>()

    fun getTables(docId: Int): SnapshotStateList<DocTable> {
        return tablesMap.getOrPut(docId) {
            val list = mutableStateListOf<DocTable>()
            // Populate with a default sample table for demo docs
            if (docId == 1) {
                list.add(
                    DocTable(
                        pageIndex = 0,
                        x = 60.dp,
                        y = 480.dp,
                        width = 450.dp,
                        height = 160.dp,
                        rows = 3,
                        columns = 4,
                        styleName = "elegant_blue",
                        themeColorHex = "#4F81BD",
                        cellData = mapOf(
                            "0,0" to "Quarter", "0,1" to "Revenue", "0,2" to "Expenses", "0,3" to "Net Margin",
                            "1,0" to "Q1 2026", "1,1" to "$124,500", "1,2" to "$89,200", "1,3" to "28.3%",
                            "2,0" to "Q2 2026", "2,1" to "$141,800", "2,2" to "$91,400", "2,3" to "35.5%"
                        )
                    )
                )
            }
            list
        }
    }

    fun addTable(docId: Int, table: DocTable) {
        getTables(docId).add(table)
    }

    fun removeTable(docId: Int, tableId: String) {
        val list = getTables(docId)
        val index = list.indexOfFirst { it.id == tableId }
        if (index != -1) {
            list.removeAt(index)
        }
    }

    fun updateTable(docId: Int, table: DocTable) {
        val list = getTables(docId)
        val index = list.indexOfFirst { it.id == table.id }
        if (index != -1) {
            list[index] = table
        }
    }
}
