package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MarginOption
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCategory
import com.example.data.model.SignaturePlacement
import com.example.data.pdf.PdfEngineService
import com.example.data.pdf.PdfInputCandidate
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ToolOperationState {
    data object Idle : ToolOperationState
    data class Processing(val message: String) : ToolOperationState
    data class Completed(val documentId: String, val title: String) : ToolOperationState
    data class Failed(val errorMessage: String) : ToolOperationState
}

class PdfToolsViewModel(
    private val repository: LibraryRepository,
    private val pdfEngine: PdfEngineService,
    private val currentUserId: String
) : ViewModel() {

    private val _operationState = MutableStateFlow<ToolOperationState>(ToolOperationState.Idle)
    val operationState: StateFlow<ToolOperationState> = _operationState.asStateFlow()

    private val _mergeCandidates = MutableStateFlow<List<PdfInputCandidate>>(emptyList())
    val mergeCandidates: StateFlow<List<PdfInputCandidate>> = _mergeCandidates.asStateFlow()

    fun resetState() {
        _operationState.value = ToolOperationState.Idle
    }

    fun addMergeUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<PdfInputCandidate>()
            for (uri in uris) {
                runCatching { pdfEngine.inspectPdfUri(uri) }
                    .onSuccess { added.add(it) }
            }
            if (added.isNotEmpty()) {
                _mergeCandidates.value = _mergeCandidates.value + added
            }
        }
    }

    fun moveMergeItemUp(index: Int) {
        val current = _mergeCandidates.value.toMutableList()
        if (index in 1 until current.size) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            _mergeCandidates.value = current
        }
    }

    fun moveMergeItemDown(index: Int) {
        val current = _mergeCandidates.value.toMutableList()
        if (index in 0 until current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            _mergeCandidates.value = current
        }
    }

    fun removeMergeItem(index: Int) {
        val current = _mergeCandidates.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _mergeCandidates.value = current
        }
    }

    fun clearMergeQueue() {
        _mergeCandidates.value = emptyList()
    }

    fun executeMerge(outputTitle: String, folderId: String?) {
        val sources = _mergeCandidates.value
        if (sources.size < 2) {
            _operationState.value = ToolOperationState.Failed("Añade al menos 2 PDFs para unir.")
            return
        }
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Uniendo ${sources.size} documentos PDF...")
            runCatching {
                val result = pdfEngine.mergePdfs(sources, outputTitle)
                val docId = repository.addDocument(
                    userId = currentUserId,
                    title = result.title,
                    fileName = result.fileName,
                    folderId = folderId,
                    category = PdfCategory.MERGED,
                    pageCount = result.pageCount,
                    fileSizeBytes = result.fileSizeBytes,
                    localPath = result.localPath
                ).getOrThrow()
                docId to result.title
            }.onSuccess { (docId, title) ->
                _mergeCandidates.value = emptyList()
                _operationState.value = ToolOperationState.Completed(docId, title)
            }.onFailure { err ->
                _operationState.value = ToolOperationState.Failed(
                    err.localizedMessage ?: "No se pudieron unir los PDFs."
                )
            }
        }
    }

    fun executeImagesToPdf(
        imageUris: List<Uri>,
        outputTitle: String,
        pageSizeOption: PageSizeOption,
        marginOption: MarginOption,
        folderId: String?
    ) {
        if (imageUris.isEmpty()) {
            _operationState.value = ToolOperationState.Failed("Selecciona al menos una imagen PNG o JPEG.")
            return
        }
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Convirtiendo ${imageUris.size} imágenes a PDF...")
            runCatching {
                val result = pdfEngine.imagesToPdf(imageUris, outputTitle, pageSizeOption, marginOption)
                val docId = repository.addDocument(
                    userId = currentUserId,
                    title = result.title,
                    fileName = result.fileName,
                    folderId = folderId,
                    category = PdfCategory.CONVERTED,
                    pageCount = result.pageCount,
                    fileSizeBytes = result.fileSizeBytes,
                    localPath = result.localPath
                ).getOrThrow()
                docId to result.title
            }.onSuccess { (docId, title) ->
                _operationState.value = ToolOperationState.Completed(docId, title)
            }.onFailure { err ->
                _operationState.value = ToolOperationState.Failed(
                    err.localizedMessage ?: "Error al convertir imágenes a PDF."
                )
            }
        }
    }

    fun executeSignPdf(
        sourcePath: String,
        signatureBitmap: Bitmap,
        placement: SignaturePlacement,
        outputTitle: String,
        folderId: String?
    ) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Estampando firma en el documento...")
            runCatching {
                val result = pdfEngine.stampSignatureOnPdf(sourcePath, signatureBitmap, placement, outputTitle)
                val docId = repository.addDocument(
                    userId = currentUserId,
                    title = result.title,
                    fileName = result.fileName,
                    folderId = folderId,
                    category = PdfCategory.SIGNED,
                    pageCount = result.pageCount,
                    fileSizeBytes = result.fileSizeBytes,
                    localPath = result.localPath
                ).getOrThrow()
                docId to result.title
            }.onSuccess { (docId, title) ->
                _operationState.value = ToolOperationState.Completed(docId, title)
            }.onFailure { err ->
                _operationState.value = ToolOperationState.Failed(
                    err.localizedMessage ?: "No se pudo firmar el documento PDF."
                )
            }
        }
    }
}
