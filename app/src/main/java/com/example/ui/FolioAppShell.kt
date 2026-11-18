package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.firebase.FirestoreLibraryRepository
import com.example.data.model.PdfDocumentItem
import com.example.data.model.UiState
import com.example.data.pdf.PdfEngineService
import com.example.data.repository.LibraryRepository
import com.example.data.repository.LocalTestLibraryRepository
import com.example.ui.navigation.AccountRoute
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ImagesToPdfRoute
import com.example.ui.navigation.MergePdfRoute
import com.example.ui.navigation.PdfReaderRoute
import com.example.ui.navigation.SignPdfRoute
import com.example.ui.navigation.SignaturesRoute
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.FilesOrganizerScreen
import com.example.ui.screens.HomeToolsScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SignPdfScreen
import com.example.ui.screens.SignaturesVaultScreen
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.SignatureViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private data class TopLevelNavItem<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun FolioAppShell(
    currentUserId: String,
    userName: String?,
    isTestMode: Boolean = false,
    onTestSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val pdfEngine = remember(appContext) { PdfEngineService(appContext) }

    val repository: LibraryRepository = remember(currentUserId, isTestMode) {
        if (isTestMode) {
            LocalTestLibraryRepository(appContext, currentUserId)
        } else {
            val db = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
            FirestoreLibraryRepository(db)
        }
    }

    val libraryViewModel: LibraryViewModel = viewModel(
        key = "lib_${isTestMode}_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                LibraryViewModel(repository, PdfEngineService(app), currentUserId)
            }
        }
    )

    val signatureViewModel: SignatureViewModel = viewModel(
        key = "sig_${isTestMode}_$currentUserId",
        factory = viewModelFactory {
            initializer {
                SignatureViewModel(repository, currentUserId)
            }
        }
    )

    val toolsViewModel: PdfToolsViewModel = viewModel(
        key = "tools_${isTestMode}_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                PdfToolsViewModel(repository, PdfEngineService(app), currentUserId)
            }
        }
    )

    val foldersState by libraryViewModel.foldersState.collectAsStateWithLifecycle()
    val documentsState by libraryViewModel.documentsState.collectAsStateWithLifecycle()
    val filteredDocsState by libraryViewModel.filteredDocumentsState.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFolderId by libraryViewModel.selectedFolderId.collectAsStateWithLifecycle()
    val selectedCategory by libraryViewModel.selectedCategory.collectAsStateWithLifecycle()
    val favoritesOnly by libraryViewModel.favoritesOnly.collectAsStateWithLifecycle()
    val sortOrder by libraryViewModel.sortOrder.collectAsStateWithLifecycle()
    val signaturesState by signatureViewModel.signaturesState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(signatureViewModel) {
        signatureViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var docPendingExport by remember { mutableStateOf<PdfDocumentItem?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val target = docPendingExport
        docPendingExport = null
        if (destUri != null && target != null) {
            libraryViewModel.exportDocument(target, destUri)
        }
    }

    val shareDocument: (PdfDocumentItem) -> Unit = { doc ->
        runCatching {
            val intent = pdfEngine.createShareIntent(doc.localPath, doc.title)
            context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("No se pudo compartir este PDF") }
        }
    }

    val triggerExport: (PdfDocumentItem) -> Unit = { doc ->
        docPendingExport = doc
        val suggested = if (doc.fileName.endsWith(".pdf", ignoreCase = true)) {
            doc.fileName
        } else {
            "${doc.title}.pdf"
        }
        exportLauncher.launch(suggested)
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val topLevelItems = remember {
        listOf(
            TopLevelNavItem(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopLevelNavItem(FilesRoute, R.string.nav_files, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files"),
            TopLevelNavItem(SignaturesRoute, R.string.nav_signatures, Icons.Filled.Draw, Icons.Outlined.Draw, "nav_signatures"),
            TopLevelNavItem(AccountRoute, R.string.nav_account, Icons.Filled.Person, Icons.Outlined.Person, "nav_account")
        )
    }

    val showTopLevelChrome = currentDest == null ||
        currentDest.hasRoute<HomeRoute>() ||
        currentDest.hasRoute<FilesRoute>() ||
        currentDest.hasRoute<SignaturesRoute>() ||
        currentDest.hasRoute<AccountRoute>()

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelChrome && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeToolsScreen(
                                userName = userName,
                                documentsState = documentsState,
                                foldersState = foldersState,
                                onNavigateMerge = { navController.navigate(MergePdfRoute) },
                                onNavigateImagesToPdf = { navController.navigate(ImagesToPdfRoute) },
                                onOpenReader = { docId -> navController.navigate(PdfReaderRoute(docId)) },
                                onOpenSigner = { docId -> navController.navigate(SignPdfRoute(docId)) },
                                onNavigateFiles = { navigateTopLevel(FilesRoute) },
                                onImportPdf = { uri, onDone ->
                                    libraryViewModel.importPdf(uri, null, onDone)
                                },
                                onRelinkPdf = { id, uri -> libraryViewModel.relinkDocumentPdf(id, uri) },
                                onToggleFavorite = { libraryViewModel.toggleFavorite(it) },
                                onRenameDocument = { id, title -> libraryViewModel.renameDocument(id, title) },
                                onMoveDocument = { id, folderId -> libraryViewModel.moveDocumentToFolder(id, folderId) },
                                onShareDocument = shareDocument,
                                onExportDocument = triggerExport,
                                onDeleteDocument = { libraryViewModel.deleteDocument(it) }
                            )
                        }

                        composable<FilesRoute> {
                            FilesOrganizerScreen(
                                searchQuery = searchQuery,
                                selectedFolderId = selectedFolderId,
                                selectedCategory = selectedCategory,
                                favoritesOnly = favoritesOnly,
                                sortOrder = sortOrder,
                                foldersState = foldersState,
                                filteredDocsState = filteredDocsState,
                                onSearchChange = libraryViewModel::setSearchQuery,
                                onSelectFolder = libraryViewModel::selectFolder,
                                onSelectCategory = libraryViewModel::selectCategory,
                                onToggleFavoritesFilter = libraryViewModel::toggleFavoritesFilter,
                                onSortChange = libraryViewModel::setSortOrder,
                                onCreateFolder = libraryViewModel::createFolder,
                                onUpdateFolder = libraryViewModel::updateFolder,
                                onDeleteFolder = libraryViewModel::deleteFolder,
                                onImportPdf = { uri -> libraryViewModel.importPdf(uri) },
                                onRelinkPdf = libraryViewModel::relinkDocumentPdf,
                                onOpenReader = { docId -> navController.navigate(PdfReaderRoute(docId)) },
                                onOpenSigner = { docId -> navController.navigate(SignPdfRoute(docId)) },
                                onToggleFavorite = libraryViewModel::toggleFavorite,
                                onRenameDocument = libraryViewModel::renameDocument,
                                onMoveDocument = libraryViewModel::moveDocumentToFolder,
                                onShareDocument = shareDocument,
                                onExportDocument = triggerExport,
                                onDeleteDocument = libraryViewModel::deleteDocument
                            )
                        }

                        composable<SignaturesRoute> {
                            SignaturesVaultScreen(
                                signaturesState = signaturesState,
                                onSaveSignature = { name, hex, bmp ->
                                    signatureViewModel.saveSignature(name, hex, bmp)
                                },
                                onRenameSignature = signatureViewModel::renameSignature,
                                onDeleteSignature = signatureViewModel::deleteSignature
                            )
                        }

                        composable<AccountRoute> {
                            AccountScreen(
                                documentsState = documentsState,
                                foldersState = foldersState,
                                signaturesState = signaturesState,
                                isTestMode = isTestMode,
                                testUserName = userName,
                                onTestSignOut = onTestSignOut
                            )
                        }

                        composable<MergePdfRoute> {
                            MergePdfScreen(
                                toolsViewModel = toolsViewModel,
                                foldersState = foldersState,
                                onBack = { navController.popBackStack() },
                                onOpenDocument = { docId ->
                                    navController.popBackStack()
                                    navController.navigate(PdfReaderRoute(docId))
                                }
                            )
                        }

                        composable<ImagesToPdfRoute> {
                            ImagesToPdfScreen(
                                toolsViewModel = toolsViewModel,
                                foldersState = foldersState,
                                onBack = { navController.popBackStack() },
                                onOpenDocument = { docId ->
                                    navController.popBackStack()
                                    navController.navigate(PdfReaderRoute(docId))
                                }
                            )
                        }

                        composable<PdfReaderRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PdfReaderRoute>()
                            val allDocs = (documentsState as? UiState.Success)?.data ?: emptyList()
                            val doc = allDocs.firstOrNull { it.id == route.documentId }
                            PdfReaderScreen(
                                document = doc,
                                pdfEngine = pdfEngine,
                                onBack = { navController.popBackStack() },
                                onMarkOpened = libraryViewModel::markDocumentOpened,
                                onSignDocument = { id -> navController.navigate(SignPdfRoute(id)) },
                                onShareDocument = shareDocument,
                                onExportDocument = triggerExport
                            )
                        }

                        composable<SignPdfRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<SignPdfRoute>()
                            val allDocs = (documentsState as? UiState.Success)?.data ?: emptyList()
                            val doc = allDocs.firstOrNull { it.id == route.documentId }
                            SignPdfScreen(
                                document = doc,
                                signaturesState = signaturesState,
                                pdfEngine = pdfEngine,
                                toolsViewModel = toolsViewModel,
                                onSaveNewSignature = signatureViewModel::saveSignature,
                                onBack = { navController.popBackStack() },
                                onOpenSignedDocument = { newDocId ->
                                    navController.popBackStack()
                                    navController.navigate(PdfReaderRoute(newDocId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
