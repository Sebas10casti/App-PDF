package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PdfCategory(val firestoreValue: String, val labelEs: String) {
    MERGED("merged", "Unidos"),
    CONVERTED("converted", "Imágenes"),
    SIGNED("signed", "Firmados"),
    IMPORTED("imported", "Importados");

    companion object {
        fun fromFirestore(value: String?): PdfCategory {
            return entries.firstOrNull { it.firestoreValue == value } ?: IMPORTED
        }
    }
}

enum class PageSizeOption(val labelEs: String) {
    A4("A4"),
    LETTER("Carta"),
    FIT_IMAGE("Ajustar a imagen")
}

enum class MarginOption(val labelEs: String) {
    NONE("Sin margen"),
    STANDARD("Normal")
}

enum class DocumentSortOrder(val labelEs: String) {
    RECENT("Más recientes"),
    NAME_ASC("Nombre A-Z"),
    SIZE_DESC("Mayor tamaño")
}

@Keep
@IgnoreExtraProperties
data class FolderItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#E84855",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PdfDocumentItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val fileName: String = "",
    val folderId: String? = null,
    val category: String = PdfCategory.IMPORTED.firestoreValue,
    val favorite: Boolean = false,
    val pageCount: Long = 1L,
    val fileSizeBytes: Long = 0L,
    val localPath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val lastOpenedAt: Timestamp? = null
) {
    val categoryEnum: PdfCategory
        get() = PdfCategory.fromFirestore(category)
}

@Keep
@IgnoreExtraProperties
data class SavedSignature(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val inkColorHex: String = "#1E2430",
    val imageBase64: String = "",
    val aspectRatio: Double = 2.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SignaturePlacement(
    val pageIndex: Int = 0,
    val xFraction: Float = 0.55f,
    val yFraction: Float = 0.72f,
    val widthFraction: Float = 0.30f
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
