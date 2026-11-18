package com.example.data.repository

import android.content.Context
import com.example.data.model.FolderItem
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.model.SavedSignature
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class LocalSandboxStore(
    val folders: List<LocalFolderDto> = emptyList(),
    val documents: List<LocalDocumentDto> = emptyList(),
    val signatures: List<LocalSignatureDto> = emptyList()
)

@Serializable
private data class LocalFolderDto(
    val id: String,
    val userId: String,
    val name: String,
    val colorHex: String,
    val createdAtSec: Long,
    val updatedAtSec: Long
)

@Serializable
private data class LocalDocumentDto(
    val id: String,
    val userId: String,
    val title: String,
    val fileName: String,
    val folderId: String? = null,
    val category: String,
    val favorite: Boolean = false,
    val pageCount: Long,
    val fileSizeBytes: Long,
    val localPath: String,
    val createdAtSec: Long,
    val updatedAtSec: Long,
    val lastOpenedAtSec: Long? = null
)

@Serializable
private data class LocalSignatureDto(
    val id: String,
    val userId: String,
    val name: String,
    val inkColorHex: String,
    val imageBase64: String,
    val aspectRatio: Double,
    val createdAtSec: Long,
    val updatedAtSec: Long
)

class LocalTestLibraryRepository(
    context: Context,
    testUserId: String
) : LibraryRepository {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val storageFile: File
    private val storeFlow: MutableStateFlow<LocalSandboxStore>

    init {
        val dir = File(context.applicationContext.filesDir, "test_sandbox")
        if (!dir.exists()) dir.mkdirs()
        val safeKey = testUserId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifEmpty { "test_user" }
        storageFile = File(dir, "$safeKey.json")
        storeFlow = MutableStateFlow(loadInitialStore())
    }

    private fun loadInitialStore(): LocalSandboxStore {
        if (!storageFile.exists()) return LocalSandboxStore()
        return runCatching {
            json.decodeFromString<LocalSandboxStore>(storageFile.readText())
        }.getOrDefault(LocalSandboxStore())
    }

    private fun persist(store: LocalSandboxStore) {
        runCatching {
            storageFile.writeText(json.encodeToString(store))
        }
        storeFlow.value = store
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L

    override fun observeFolders(userId: String): Flow<List<FolderItem>> =
        storeFlow.map { store ->
            store.folders
                .map {
                    FolderItem(
                        id = it.id,
                        userId = it.userId,
                        name = it.name,
                        colorHex = it.colorHex,
                        createdAt = Timestamp(it.createdAtSec, 0),
                        updatedAt = Timestamp(it.updatedAtSec, 0)
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun createFolder(userId: String, name: String, colorHex: String): Result<String> =
        mutex.withLock {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.length in 1..50) { "El nombre debe tener entre 1 y 50 caracteres." }
                val id = "tf_${UUID.randomUUID().toString().take(8)}"
                val now = nowSec()
                val dto = LocalFolderDto(id, userId, cleanName, colorHex, now, now)
                persist(storeFlow.value.copy(folders = storeFlow.value.folders + dto))
                id
            }
        }

    override suspend fun updateFolder(
        userId: String,
        folderId: String,
        name: String,
        colorHex: String
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.length in 1..50) { "El nombre debe tener entre 1 y 50 caracteres." }
            val now = nowSec()
            val updated = storeFlow.value.folders.map {
                if (it.id == folderId) it.copy(name = cleanName, colorHex = colorHex, updatedAtSec = now)
                else it
            }
            persist(storeFlow.value.copy(folders = updated))
        }
    }

    override suspend fun deleteFolder(userId: String, folderId: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val now = nowSec()
                val docs = storeFlow.value.documents.map {
                    if (it.folderId == folderId) it.copy(folderId = null, updatedAtSec = now) else it
                }
                val folders = storeFlow.value.folders.filterNot { it.id == folderId }
                persist(storeFlow.value.copy(folders = folders, documents = docs))
            }
        }

    override fun observeDocuments(userId: String): Flow<List<PdfDocumentItem>> =
        storeFlow.map { store ->
            store.documents
                .map {
                    PdfDocumentItem(
                        id = it.id,
                        userId = it.userId,
                        title = it.title,
                        fileName = it.fileName,
                        folderId = it.folderId,
                        category = it.category,
                        favorite = it.favorite,
                        pageCount = it.pageCount,
                        fileSizeBytes = it.fileSizeBytes,
                        localPath = it.localPath,
                        createdAt = Timestamp(it.createdAtSec, 0),
                        updatedAt = Timestamp(it.updatedAtSec, 0),
                        lastOpenedAt = it.lastOpenedAtSec?.let { sec -> Timestamp(sec, 0) }
                    )
                }
                .sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    override suspend fun addDocument(
        userId: String,
        title: String,
        fileName: String,
        folderId: String?,
        category: PdfCategory,
        pageCount: Long,
        fileSizeBytes: Long,
        localPath: String
    ): Result<String> = mutex.withLock {
        runCatching {
            val id = "td_${UUID.randomUUID().toString().take(8)}"
            val now = nowSec()
            val dto = LocalDocumentDto(
                id = id,
                userId = userId,
                title = title.trim().take(120).ifEmpty { "Documento PDF" },
                fileName = fileName.trim().take(160).ifEmpty { "documento.pdf" },
                folderId = folderId?.takeIf { it.isNotBlank() },
                category = category.firestoreValue,
                favorite = false,
                pageCount = pageCount.coerceAtLeast(1L),
                fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
                localPath = localPath,
                createdAtSec = now,
                updatedAtSec = now
            )
            persist(storeFlow.value.copy(documents = storeFlow.value.documents + dto))
            id
        }
    }

    override suspend fun renameDocument(userId: String, documentId: String, title: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val cleanTitle = title.trim()
                require(cleanTitle.length in 1..120) { "El título debe tener entre 1 y 120 caracteres." }
                val now = nowSec()
                val updated = storeFlow.value.documents.map {
                    if (it.id == documentId) it.copy(title = cleanTitle, updatedAtSec = now) else it
                }
                persist(storeFlow.value.copy(documents = updated))
            }
        }

    override suspend fun toggleFavorite(userId: String, documentId: String, favorite: Boolean): Result<Unit> =
        mutex.withLock {
            runCatching {
                val now = nowSec()
                val updated = storeFlow.value.documents.map {
                    if (it.id == documentId) it.copy(favorite = favorite, updatedAtSec = now) else it
                }
                persist(storeFlow.value.copy(documents = updated))
            }
        }

    override suspend fun moveDocumentToFolder(
        userId: String,
        documentId: String,
        folderId: String?
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val now = nowSec()
            val updated = storeFlow.value.documents.map {
                if (it.id == documentId) it.copy(folderId = folderId?.takeIf { f -> f.isNotBlank() }, updatedAtSec = now)
                else it
            }
            persist(storeFlow.value.copy(documents = updated))
        }
    }

    override suspend fun updateLastOpened(userId: String, documentId: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val now = nowSec()
                val updated = storeFlow.value.documents.map {
                    if (it.id == documentId) it.copy(lastOpenedAtSec = now, updatedAtSec = now) else it
                }
                persist(storeFlow.value.copy(documents = updated))
            }
        }

    override suspend fun relinkLocalFile(
        userId: String,
        documentId: String,
        localPath: String,
        pageCount: Long,
        fileSizeBytes: Long
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val now = nowSec()
            val updated = storeFlow.value.documents.map {
                if (it.id == documentId) {
                    it.copy(
                        localPath = localPath,
                        pageCount = pageCount.coerceAtLeast(1L),
                        fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
                        updatedAtSec = now
                    )
                } else it
            }
            persist(storeFlow.value.copy(documents = updated))
        }
    }

    override suspend fun deleteDocument(userId: String, documentId: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val docs = storeFlow.value.documents.filterNot { it.id == documentId }
                persist(storeFlow.value.copy(documents = docs))
            }
        }

    override fun observeSignatures(userId: String): Flow<List<SavedSignature>> =
        storeFlow.map { store ->
            store.signatures
                .map {
                    SavedSignature(
                        id = it.id,
                        userId = it.userId,
                        name = it.name,
                        inkColorHex = it.inkColorHex,
                        imageBase64 = it.imageBase64,
                        aspectRatio = it.aspectRatio,
                        createdAt = Timestamp(it.createdAtSec, 0),
                        updatedAt = Timestamp(it.updatedAtSec, 0)
                    )
                }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun addSignature(
        userId: String,
        name: String,
        inkColorHex: String,
        imageBase64: String,
        aspectRatio: Double
    ): Result<String> = mutex.withLock {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.length in 1..60) { "El nombre debe tener entre 1 y 60 caracteres." }
            val id = "ts_${UUID.randomUUID().toString().take(8)}"
            val now = nowSec()
            val dto = LocalSignatureDto(id, userId, cleanName, inkColorHex, imageBase64, aspectRatio, now, now)
            persist(storeFlow.value.copy(signatures = storeFlow.value.signatures + dto))
            id
        }
    }

    override suspend fun renameSignature(userId: String, signatureId: String, name: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.length in 1..60) { "El nombre debe tener entre 1 y 60 caracteres." }
                val now = nowSec()
                val updated = storeFlow.value.signatures.map {
                    if (it.id == signatureId) it.copy(name = cleanName, updatedAtSec = now) else it
                }
                persist(storeFlow.value.copy(signatures = updated))
            }
        }

    override suspend fun deleteSignature(userId: String, signatureId: String): Result<Unit> =
        mutex.withLock {
            runCatching {
                val sigs = storeFlow.value.signatures.filterNot { it.id == signatureId }
                persist(storeFlow.value.copy(signatures = sigs))
            }
        }
}
