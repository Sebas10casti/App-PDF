package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object FilesRoute

@Serializable
data object SignaturesRoute

@Serializable
data object AccountRoute

@Serializable
data object MergePdfRoute

@Serializable
data object ImagesToPdfRoute

@Serializable
data class PdfReaderRoute(val documentId: String)

@Serializable
data class SignPdfRoute(val documentId: String)
