# Especificación de Producto y Arquitectura (Spec-Driven Development) — FolioPDF

> **Versión:** 1.0.0  
> **Estado:** Aprobado para implementación  
> **Plataforma:** Android (Kotlin, Jetpack Compose, Material Design 3)  
> **Backend:** Firebase Auth (Google Sign-In vía Credential Manager) + Cloud Firestore

---

## 1. Propósito y Alcance

**FolioPDF** es un estudio móvil nativo para gestionar, transformar, leer y firmar documentos PDF con una **identidad visual 100% propia** (*Estudio Editorial Coral & Grafito*) y organización sincronizada en la nube vinculada a la cuenta de Google del usuario.

### 1.1 Requisitos Funcionales (RF)

| ID | Capacidad | Descripción y Criterios de Aceptación |
| :--- | :--- | :--- |
| **RF-01** | **Autenticación con Google** | Inicio de sesión silencioso al iniciar + botón explícito "Continuar con Google" vía `CredentialManager` (`GetSignInWithGoogleOption`). Ninguna pantalla ni consulta a Firestore se ejecuta sin sesión autenticada. Cierre de sesión seguro limpiando credenciales y oyentes. |
| **RF-02** | **Unir PDF** | Selección múltiple de PDFs mediante Storage Access Framework (`OpenMultipleDocuments`). Visualización de orden con número de páginas y tamaño, controles de reordenación (subir/bajar/eliminar), nombre personalizado y carpeta destino. Fusión sin pérdida vectorial mediante `PDFMergerUtility`. |
| **RF-03** | **PNG / JPEG a PDF** | Selección múltiple de imágenes usando **Android Photo Picker** (`PickMultipleVisualMedia`, cero permisos de almacenamiento). Reordenación de miniaturas, configuración de formato de página (*A4*, *Carta*, *Ajustar a imagen*) y margen (*Sin margen*, *Estándar*). Generación multipágina con `PDDocument`. |
| **RF-04** | **Bóveda de Firmas** | Lienzo táctil de alta precisión (`Canvas` con suavizado cuadrático) con selector de color de tinta (grafito, azul estilográfica, coral) y grosor de trazo, botones deshacer/limpiar y guardado con nombre personalizado. Sincronización multi-dispositivo en Firestore (`/users/{uid}/signatures`). |
| **RF-05** | **Firmar PDF (Ubicación Libre)** | Visor de página del PDF objetivo, selector de página, bandeja de firmas guardadas y superposición flotante interactiva que permite arrastrar libremente la firma y ajustar su escala en cualquier punto de la página. Estampado permanente en coordenadas PDF exactas vía `PDPageContentStream`. |
| **RF-06** | **Lector de PDF** | Renderizado de páginas con `android.graphics.pdf.PdfRenderer`, gestos de zoom (*pinch-to-zoom* y doble toque) y paneo, indicador y deslizador rápido de páginas, modo lectura nocturna (inversión suave), y accesos directos para firmar, exportar o compartir (`FileProvider`). |
| **RF-07** | **Organizador de Archivos** | Carpetas personalizadas con color, marcado de favoritos, búsqueda por nombre en tiempo real y filtros por categoría (*Todos*, *Favoritos*, *Unidos*, *Imágenes*, *Firmados*, *Importados*). Acciones por documento: abrir, firmar, mover de carpeta, renombrar, exportar (`CreateDocument`), compartir y eliminar. |

---

## 2. Requisitos No Funcionales y Restricciones de Diseño

- **Identidad Visual Propia (Estudio Editorial Coral & Grafito):**
  - Paleta clara: fondo Lino Marfil (`#F9F7F4`), tarjetas Papel Puro (`#FFFFFF`) con borde fino (`#E8E3DC`), primario Coral Bermellón (`#E84855`), secundario Pizarra Grafito (`#1E2430`).
  - Paleta oscura: fondo Carbón Profundo (`#14171F`), tarjetas Pizarra Nocturna (`#1E232E`), acento Coral suave (`#FF7580`).
  - Codificación cromática de herramientas: Unir (`#E84855`), Imágenes (`#D9822B`), Firmar (`#1B8A6B`), Lector (`#3B5BDB`).
  - Tipografía local: `Plus Jakarta Sans` (display/títulos) + `Inter` (cuerpo/metadatos).
- **Cumplimiento Google Play:** Cero permisos `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`. Uso estricto de `PickVisualMedia` y Storage Access Framework.
- **Diseño Adaptativo y Edge-to-Edge:** Uso obligatorio de `enableEdgeToEdge()`, `WindowInsets` seguros, `NavigationBar` en pantallas compactas y `NavigationRail` + ancho acotado (`600.dp`) en pantallas medianas/expandidas. Targets táctiles mínimos de `48.dp`.

---

## 3. Especificación de Modelo de Datos (Firestore)

Todas las colecciones están aisladas por usuario bajo `/users/{userId}/...` con validación estricta en `firestore.rules` y verificadas contra el emulador local (`firestore.test.js`).

### 3.1 `FolderItem` (`/users/{userId}/folders/{folderId}`)
- `userId: String` (igual a `request.auth.uid`)
- `name: String` (1..50 caracteres)
- `colorHex: String` (hexadecimal `#RRGGBB`)
- `createdAt: Timestamp` (`FieldValue.serverTimestamp()`, inmutable)
- `updatedAt: Timestamp` (`FieldValue.serverTimestamp()`)

### 3.2 `PdfDocumentItem` (`/users/{userId}/documents/{documentId}`)
- `userId: String` (igual a `request.auth.uid`)
- `title: String` (1..120 caracteres)
- `fileName: String` (1..160 caracteres)
- `folderId: String?` (opcional, referencia a carpeta)
- `category: String` (`"merged" | "converted" | "signed" | "imported"`)
- `favorite: Boolean`
- `pageCount: Long` (`>= 1`)
- `fileSizeBytes: Long` (`>= 0`)
- `localPath: String` (ruta local del binario en `filesDir/pdfs/`)
- `createdAt: Timestamp` (inmutable)
- `updatedAt: Timestamp`
- `lastOpenedAt: Timestamp?` (opcional)

### 3.3 `SavedSignature` (`/users/{userId}/signatures/{signatureId}`)
- `userId: String` (igual a `request.auth.uid`)
- `name: String` (1..60 caracteres)
- `inkColorHex: String`
- `imageBase64: String` (PNG transparente optimizado, <= 300,000 caracteres)
- `aspectRatio: Double` (`> 0`)
- `createdAt: Timestamp` (inmutable)
- `updatedAt: Timestamp`

---

## 4. Arquitectura de Software

```
UI (Compose + Material 3 + Navigation Compose)
  ├── AuthScreen (CredentialManager Google Sign-In)
  └── FolioAppShell (key = uid)
        ├── HomeToolsScreen (Hub de herramientas + Recientes)
        ├── FilesOrganizerScreen (Carpetas, Búsqueda, Filtros, Favoritos)
        ├── SignaturesVaultScreen (Bóveda + Lienzo de Firma)
        ├── AccountScreen (Perfil Google, Estado de Sync, Cerrar Sesión)
        ├── MergePdfScreen (Unir PDFs)
        ├── ImagesToPdfScreen (PNG/JPEG -> PDF)
        ├── PdfReaderScreen (Lector PdfRenderer + Zoom + Modo Oscuro)
        └── SignPdfScreen (Colocación libre de firma + Estampado)
              │
              ▼
ViewModels (StateFlow + UiState<T>)
  ├── LibraryViewModel
  ├── SignatureViewModel
  └── PdfToolsViewModel
              │
              ▼
Servicios y Repositorios
  ├── FirestoreLibraryRepository (snapshots() + Result<T> + handleFirestoreError)
  └── PdfEngineService (PDFBox-Android + PdfRenderer + FileProvider)
```

---

## 5. Criterios de Verificación

1. Reglas `firestore.rules` validadas con `node --test firestore.test.js` contra el emulador local de Firestore (aislamiento entre usuarios, rechazo sin autenticación, validación de esquema y consultas).
2. Compilación limpia con `gradle :app:compileDebugKotlin` y `compile_applet`.
3. Documentación de cada funcionalidad agregada registrada en `/readme.md`.
