package com.example.data.repository

import com.example.data.model.FolderItem
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.model.SavedSignature
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeFolders(userId: String): Flow<List<FolderItem>>
    suspend fun createFolder(userId: String, name: String, colorHex: String): Result<String>
    suspend fun updateFolder(userId: String, folderId: String, name: String, colorHex: String): Result<Unit>
    suspend fun deleteFolder(userId: String, folderId: String): Result<Unit>

    fun observeDocuments(userId: String): Flow<List<PdfDocumentItem>>
    suspend fun addDocument(
        userId: String,
        title: String,
        fileName: String,
        folderId: String?,
        category: PdfCategory,
        pageCount: Long,
        fileSizeBytes: Long,
        localPath: String
    ): Result<String>
    suspend fun renameDocument(userId: String, documentId: String, title: String): Result<Unit>
    suspend fun toggleFavorite(userId: String, documentId: String, favorite: Boolean): Result<Unit>
    suspend fun moveDocumentToFolder(userId: String, documentId: String, folderId: String?): Result<Unit>
    suspend fun updateLastOpened(userId: String, documentId: String): Result<Unit>
    suspend fun relinkLocalFile(
        userId: String,
        documentId: String,
        localPath: String,
        pageCount: Long,
        fileSizeBytes: Long
    ): Result<Unit>
    suspend fun deleteDocument(userId: String, documentId: String): Result<Unit>

    fun observeSignatures(userId: String): Flow<List<SavedSignature>>
    suspend fun addSignature(
        userId: String,
        name: String,
        inkColorHex: String,
        imageBase64: String,
        aspectRatio: Double
    ): Result<String>
    suspend fun renameSignature(userId: String, signatureId: String, name: String): Result<Unit>
    suspend fun deleteSignature(userId: String, signatureId: String): Result<Unit>
}
