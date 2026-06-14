package com.example.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DocShape(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: String, // "rectangle", "round_rectangle", "triangle", "right_triangle", "ellipse", "diamond", "hexagon", "cloud", "heart", "right_arrow", "left_arrow", "up_arrow", "down_arrow", "plus_eq", "minus_eq", "star_5", "smiley"
    val group: String, // "Rectangles", "Basic Shapes", "Block Arrows", "Equation Shapes", "Stars & Banners"
    val x: Dp,
    val y: Dp,
    val width: Dp = 120.dp,
    val height: Dp = 100.dp,
    val fillColorHex: String = "#4F81BD",  // MS Office Elegant Blue
    val borderColorHex: String = "#1B365D",
    val borderWidthDp: Dp = 2.dp,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    
    // Internal Text Features
    val textInside: String = "",
    val textColorHex: String = "#FFFFFF",
    val textSizeSp: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textAlignment: String = "center" // "left", "center", "right"
)

object DocShapeRepository {
    private val shapesMap = mutableMapOf<Int, SnapshotStateList<DocShape>>()

    fun getShapes(docId: Int): SnapshotStateList<DocShape> {
        return shapesMap.getOrPut(docId) {
            val list = mutableStateListOf<DocShape>()
            // Let's add some creative office style shapes by default for the demo document (docId = 1)
            if (docId == 1) {
                list.add(
                    DocShape(
                        id = "default_shape_1",
                        pageIndex = 0,
                        type = "round_rectangle",
                        group = "Rectangles",
                        x = 440.dp,
                        y = 120.dp,
                        width = 160.dp,
                        height = 80.dp,
                        fillColorHex = "#4F81BD",
                        borderColorHex = "#1B365D",
                        borderWidthDp = 2.dp,
                        textInside = "Project Charter",
                        textColorHex = "#FFFFFF",
                        isBold = true
                    )
                )
                list.add(
                    DocShape(
                        id = "default_shape_2",
                        pageIndex = 0,
                        type = "star_5",
                        group = "Stars & Banners",
                        x = 100.dp,
                        y = 660.dp,
                        width = 110.dp,
                        height = 110.dp,
                        fillColorHex = "#F79646", // Warm golden orange
                        borderColorHex = "#A25005",
                        borderWidthDp = 1.5.dp,
                        textInside = "NEW!",
                        textColorHex = "#FFFFFF",
                        textSizeSp = 13f,
                        isBold = true,
                        rotation = 15f
                    )
                )
            }
            list
        }
    }

    fun addShape(docId: Int, shape: DocShape) {
        getShapes(docId).add(shape)
    }

    fun removeShape(docId: Int, shapeId: String) {
        val list = getShapes(docId)
        val index = list.indexOfFirst { it.id == shapeId }
        if (index != -1) {
            list.removeAt(index)
        }
    }

    fun updateShape(docId: Int, shape: DocShape) {
        val list = getShapes(docId)
        val index = list.indexOfFirst { it.id == shape.id }
        if (index != -1) {
            list[index] = shape
        }
    }
}
