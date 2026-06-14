package com.example.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.UUID

data class DocPicture(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val pageIndex: Int = 0,
    val x: Dp = 50.dp,
    val y: Dp = 100.dp,
    val width: Dp = 180.dp,
    val height: Dp = 180.dp,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val elevation: Dp = 0.dp,
    val isGrayscale: Boolean = false,
    val isInverted: Boolean = false,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val borderWidthDp: Dp = 0.dp,
    val borderRadiusDp: Dp = 0.dp,
    val borderColorHex: String = "#CCCCCC",
    val cropLeft: Float = 0f,
    val cropRight: Float = 0f,
    val cropTop: Float = 0f,
    val cropBottom: Float = 0f
)

object DocPictureRepository {
    private val picturesMap = mutableStateMapOf<Int, SnapshotStateList<DocPicture>>()

    fun getPictures(docId: Int): SnapshotStateList<DocPicture> {
        return picturesMap.getOrPut(docId) { mutableStateListOf() }
    }

    fun addPicture(docId: Int, picture: DocPicture) {
        getPictures(docId).add(picture)
    }

    fun removePicture(docId: Int, pictureId: String) {
        val list = getPictures(docId)
        val index = list.indexOfFirst { it.id == pictureId }
        if (index != -1) {
            list.removeAt(index)
        }
    }

    fun updatePicture(docId: Int, picture: DocPicture) {
        val list = getPictures(docId)
        val index = list.indexOfFirst { it.id == picture.id }
        if (index != -1) {
            list[index] = picture
        }
    }
}
