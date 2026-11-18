package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DocumentSortOrder
import com.example.data.model.FolderItem
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDocumentItem
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.data.repository.LibraryRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val pdfEngine: PdfEngineService,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    private val _selectedCategory = MutableStateFlow<PdfCategory?>(null)
    val selectedCategory: StateFlow<PdfCategory?> = _selectedCategory.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _sortOrder = MutableStateFlow(DocumentSortOrder.RECENT)
    val sortOrder: StateFlow<DocumentSortOrder> = _sortOrder.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val foldersState: StateFlow<UiState<List<FolderItem>>> =
        repository.observeFolders(currentUserId)
            .map<List<FolderItem>, UiState<List<FolderItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing folders", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar las carpetas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val documentsState: StateFlow<UiState<List<PdfDocumentItem>>> =
        repository.observeDocuments(currentUserId)
            .map<List<PdfDocumentItem>, UiState<List<PdfDocumentItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing documents", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar los documentos"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val filteredDocumentsState: StateFlow<UiState<List<PdfDocumentItem>>> =
        combine(
            documentsState,
            searchQuery,
            selectedFolderId,
            selectedCategory,
            combine(favoritesOnly, sortOrder) { fav, sort -> fav to sort }
        ) { docsUi, query, folderId, category, (onlyFavs, sort) ->
            when (docsUi) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> docsUi
                is UiState.Success -> {
                    val q = query.trim().lowercase()
                    val filtered = docsUi.data.filter { doc ->
                        val matchesQuery = q.isEmpty() ||
                            doc.title.lowercase().contains(q) ||
                            doc.fileName.lowercase().contains(q)
                        val matchesFolder = folderId == null || doc.folderId == folderId
                        val matchesCategory = category == null || doc.categoryEnum == category
                        val matchesFav = !onlyFavs || doc.favorite
                        matchesQuery && matchesFolder && matchesCategory && matchesFav
                    }
                    val sorted = when (sort) {
                        DocumentSortOrder.RECENT -> filtered.sortedByDescending {
                            it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
                        }
                        DocumentSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
                        DocumentSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes }
                    }
                    UiState.Success(sorted)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = if (_selectedFolderId.value == folderId) null else folderId
    }

    fun selectCategory(category: PdfCategory?) {
        _selectedCategory.value = category
    }

    fun toggleFavoritesFilter() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    fun setSortOrder(order: DocumentSortOrder) {
        _sortOrder.value = order
    }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.createFolder(currentUserId, name, colorHex)
                .onSuccess { _events.emit("Carpeta \"$name\" creada") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al crear carpeta") }
        }
    }

    fun updateFolder(folderId: String, name: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateFolder(currentUserId, folderId, name, colorHex)
                .onSuccess { _events.emit("Carpeta actualizada") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al actualizar carpeta") }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
            repository.deleteFolder(currentUserId, folderId)
                .onSuccess { _events.emit("Carpeta eliminada") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al eliminar carpeta") }
        }
    }

    fun importPdf(uri: Uri, targetFolderId: String? = _selectedFolderId.value, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val imported = pdfEngine.importPdfFromUri(uri)
                val docId = repository.addDocument(
                    userId = currentUserId,
                    title = imported.title,
                    fileName = imported.fileName,
                    folderId = targetFolderId,
                    category = PdfCategory.IMPORTED,
                    pageCount = imported.pageCount,
                    fileSizeBytes = imported.fileSizeBytes,
                    localPath = imported.localPath
                ).getOrThrow()
                docId
            }.onSuccess { docId ->
                _events.emit("PDF importado correctamente")
                onImported(docId)
            }.onFailure {
                _events.emit(it.localizedMessage ?: "No se pudo importar el PDF")
            }
        }
    }

    fun relinkDocumentPdf(documentId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val imported = pdfEngine.importPdfFromUri(uri)
                repository.relinkLocalFile(
                    userId = currentUserId,
                    documentId = documentId,
                    localPath = imported.localPath,
                    pageCount = imported.pageCount,
                    fileSizeBytes = imported.fileSizeBytes
                ).getOrThrow()
            }.onSuccess {
                _events.emit("Archivo local vinculado")
            }.onFailure {
                _events.emit(it.localizedMessage ?: "No se pudo vincular el PDF")
            }
        }
    }

    fun toggleFavorite(doc: PdfDocumentItem) {
        viewModelScope.launch {
            repository.toggleFavorite(currentUserId, doc.id, !doc.favorite)
                .onFailure { _events.emit(it.localizedMessage ?: "Error al actualizar favorito") }
        }
    }

    fun renameDocument(documentId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(currentUserId, documentId, newTitle)
                .onSuccess { _events.emit("Documento renombrado") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al renombrar") }
        }
    }

    fun moveDocumentToFolder(documentId: String, folderId: String?) {
        viewModelScope.launch {
            repository.moveDocumentToFolder(currentUserId, documentId, folderId)
                .onSuccess { _events.emit("Documento movido") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al mover documento") }
        }
    }

    fun markDocumentOpened(documentId: String) {
        viewModelScope.launch {
            repository.updateLastOpened(currentUserId, documentId)
        }
    }

    fun deleteDocument(doc: PdfDocumentItem) {
        viewModelScope.launch {
            repository.deleteDocument(currentUserId, doc.id)
                .onSuccess {
                    pdfEngine.deleteLocalPdfFile(doc.localPath)
                    _events.emit("Documento eliminado")
                }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al eliminar documento") }
        }
    }

    fun exportDocument(doc: PdfDocumentItem, destinationUri: Uri) {
        viewModelScope.launch {
            runCatching {
                pdfEngine.exportPdfToUri(doc.localPath, destinationUri)
            }.onSuccess {
                _events.emit("Copia guardada en el dispositivo")
            }.onFailure {
                _events.emit(it.localizedMessage ?: "No se pudo exportar el PDF")
            }
        }
    }

    private companion object {
        const val TAG = "LibraryVM"
    }
}
