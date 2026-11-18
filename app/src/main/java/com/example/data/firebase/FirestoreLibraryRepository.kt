package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.FolderItem
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.model.SavedSignature
import com.example.data.repository.LibraryRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreLibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : LibraryRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a tus datos.")
        check(uid == expectedUserId) { "La sesión activa cambió; operación cancelada." }
        return uid
    }

    // --- FOLDERS ---

    override fun observeFolders(userId: String): Flow<List<FolderItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/folders"
        emitAll(
            db.collection("users").document(uid).collection("folders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FolderItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun createFolder(userId: String, name: String, colorHex: String): Result<String> {
        var docPath = "users/$userId/folders"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim()
            require(cleanName.length in 1..50) { "El nombre debe tener entre 1 y 50 caracteres." }
            require(HEX_COLOR_REGEX.matches(colorHex)) { "Color de carpeta inválido." }

            val docRef = db.collection("users").document(uid).collection("folders").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "colorHex" to colorHex,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateFolder(
        userId: String,
        folderId: String,
        name: String,
        colorHex: String
    ): Result<Unit> {
        var docPath = "users/$userId/folders/$folderId"
        return try {
            val uid = requireUserId(userId)
            require(folderId.isNotBlank()) { "ID de carpeta inválido." }
            val cleanName = name.trim()
            require(cleanName.length in 1..50) { "El nombre debe tener entre 1 y 50 caracteres." }
            require(HEX_COLOR_REGEX.matches(colorHex)) { "Color de carpeta inválido." }

            val docRef = db.collection("users").document(uid)
                .collection("folders").document(folderId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "colorHex" to colorHex,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteFolder(userId: String, folderId: String): Result<Unit> {
        var docPath = "users/$userId/folders/$folderId"
        return try {
            val uid = requireUserId(userId)
            require(folderId.isNotBlank()) { "ID de carpeta inválido." }

            // Unlink documents currently inside this folder so no dangling folderId remains
            val docsSnap = db.collection("users").document(uid).collection("documents")
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            for (doc in docsSnap.documents) {
                doc.reference.update(
                    mapOf(
                        "folderId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val folderRef = db.collection("users").document(uid)
                .collection("folders").document(folderId)
            docPath = folderRef.path
            folderRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- DOCUMENTS ---

    override fun observeDocuments(userId: String): Flow<List<PdfDocumentItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/documents"
        emitAll(
            db.collection("users").document(uid).collection("documents")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PdfDocumentItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
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
    ): Result<String> {
        var docPath = "users/$userId/documents"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(120).ifEmpty { "Documento PDF" }
            val cleanFileName = fileName.trim().take(160).ifEmpty { "documento.pdf" }
            val validPages = pageCount.coerceIn(1L, 50000L)
            val validSize = fileSizeBytes.coerceIn(0L, 500_000_000L)
            val cleanLocalPath = localPath.take(400)

            val docRef = db.collection("users").document(uid).collection("documents").document()
            docPath = docRef.path
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to cleanTitle,
                "fileName" to cleanFileName,
                "category" to category.firestoreValue,
                "favorite" to false,
                "pageCount" to validPages,
                "fileSizeBytes" to validSize,
                "localPath" to cleanLocalPath,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!folderId.isNullOrBlank()) {
                payload["folderId"] = folderId
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun renameDocument(userId: String, documentId: String, title: String): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..120) { "El título debe tener entre 1 y 120 caracteres." }
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleFavorite(userId: String, documentId: String, favorite: Boolean): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun moveDocumentToFolder(
        userId: String,
        documentId: String,
        folderId: String?
    ): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            val folderValue: Any = if (folderId.isNullOrBlank()) FieldValue.delete() else folderId
            docRef.update(
                mapOf(
                    "folderId" to folderValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateLastOpened(userId: String, documentId: String): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "lastOpenedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun relinkLocalFile(
        userId: String,
        documentId: String,
        localPath: String,
        pageCount: Long,
        fileSizeBytes: Long
    ): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "localPath" to localPath.take(400),
                    "pageCount" to pageCount.coerceIn(1L, 50000L),
                    "fileSizeBytes" to fileSizeBytes.coerceIn(0L, 500_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteDocument(userId: String, documentId: String): Result<Unit> {
        var docPath = "users/$userId/documents/$documentId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("documents").document(documentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- SIGNATURES ---

    override fun observeSignatures(userId: String): Flow<List<SavedSignature>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/signatures"
        emitAll(
            db.collection("users").document(uid).collection("signatures")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedSignature::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun addSignature(
        userId: String,
        name: String,
        inkColorHex: String,
        imageBase64: String,
        aspectRatio: Double
    ): Result<String> {
        var docPath = "users/$userId/signatures"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim()
            require(cleanName.length in 1..60) { "El nombre de la firma debe tener entre 1 y 60 caracteres." }
            require(HEX_COLOR_REGEX.matches(inkColorHex)) { "Color de tinta inválido." }
            require(imageBase64.length in 10..300_000) { "La firma excede el tamaño permitido para sincronización." }
            require(aspectRatio > 0.0 && aspectRatio <= 20.0) { "Proporción de firma inválida." }

            val docRef = db.collection("users").document(uid).collection("signatures").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "inkColorHex" to inkColorHex,
                "imageBase64" to imageBase64,
                "aspectRatio" to aspectRatio,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun renameSignature(userId: String, signatureId: String, name: String): Result<Unit> {
        var docPath = "users/$userId/signatures/$signatureId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim()
            require(cleanName.length in 1..60) { "El nombre de la firma debe tener entre 1 y 60 caracteres." }
            val docRef = db.collection("users").document(uid)
                .collection("signatures").document(signatureId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteSignature(userId: String, signatureId: String): Result<Unit> {
        var docPath = "users/$userId/signatures/$signatureId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("signatures").document(signatureId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
