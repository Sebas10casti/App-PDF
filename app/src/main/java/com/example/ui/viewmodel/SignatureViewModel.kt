package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedSignature
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.data.repository.LibraryRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignatureViewModel(
    private val repository: LibraryRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val signaturesState: StateFlow<UiState<List<SavedSignature>>> =
        repository.observeSignatures(currentUserId)
            .map<List<SavedSignature>, UiState<List<SavedSignature>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing signatures", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar las firmas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun saveSignature(
        name: String,
        inkColorHex: String,
        bitmap: Bitmap,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                PdfEngineService.encodeBitmapToBase64(bitmap)
            }
            val ratio = (bitmap.width.toDouble() / bitmap.height.toDouble().coerceAtLeast(1.0))
                .coerceIn(0.2, 20.0)
            repository.addSignature(
                userId = currentUserId,
                name = name,
                inkColorHex = inkColorHex,
                imageBase64 = base64,
                aspectRatio = ratio
            ).onSuccess { id ->
                _events.emit("Firma guardada y sincronizada")
                onSaved(id)
            }.onFailure {
                _events.emit(it.localizedMessage ?: "No se pudo guardar la firma")
            }
        }
    }

    fun renameSignature(signatureId: String, newName: String) {
        viewModelScope.launch {
            repository.renameSignature(currentUserId, signatureId, newName)
                .onSuccess { _events.emit("Nombre de firma actualizado") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al renombrar firma") }
        }
    }

    fun deleteSignature(signatureId: String) {
        viewModelScope.launch {
            repository.deleteSignature(currentUserId, signatureId)
                .onSuccess { _events.emit("Firma eliminada") }
                .onFailure { _events.emit(it.localizedMessage ?: "Error al eliminar firma") }
        }
    }

    private companion object {
        const val TAG = "SignatureVM"
    }
}
