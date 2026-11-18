# FolioPDF

Estudio móvil Android nativo construido con Kotlin, Jetpack Compose y Material Design 3 para gestionar, transformar, leer y firmar documentos PDF con una identidad visual propia (*Estudio Editorial Coral & Grafito*) y sincronización en la nube con tu cuenta de Google.

> Especificación técnica y de producto (Spec-Driven Development): [`specs/foliopdf-spec.md`](specs/foliopdf-spec.md)

## Funcionalidades implementadas

- **Especificación SDD y arquitectura base:** Documento de especificación en `specs/foliopdf-spec.md`, esquema en `firebase-blueprint.json` y reglas zero-trust en `firestore.rules` validadas con pruebas unitarias locales (`firestore.test.js`).
- **Autenticación con Google y sincronización en la nube:** Inicio de sesión con Google vía Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + auto-login silencioso) y sincronización reactiva en Cloud Firestore aislada por usuario (`/users/{uid}/...`).
- **Identidad visual propia e icono adaptativo:** Tema *Estudio Editorial Coral & Grafito* con tipografía local (*Plus Jakarta Sans* + *Inter*), modo claro/oscuro, ilustración editorial e icono adaptativo personalizado.
- **Unir PDF (`MergePdfScreen`):** Selección múltiple de documentos PDF mediante Storage Access Framework, reordenación interactiva (subir/bajar/quitar), asignación de nombre/carpeta y fusión sin pérdida vectorial usando `PDFMergerUtility` (`pdfbox-android`).
- **Convertir PNG / JPEG a PDF (`ImagesToPdfScreen`):** Selección múltiple sin permisos invasivos usando *Android Photo Picker* (`PickMultipleVisualMedia`), corrección automática de orientación EXIF, reordenación de miniaturas, opciones de tamaño de página (*A4*, *Carta*, *Ajustar a imagen*) y márgenes (*Sin margen*, *Normal*).
- **Bóveda de Firmas (`SignaturesVaultScreen` & `SignatureDrawingDialog`):** Lienzo táctil con suavizado Bézier, selector de color de tinta (grafito, azul, coral) y grosor, deshacer/limpiar y guardado sincronizado de múltiples firmas en Firestore.
- **Firmar PDF con ubicación libre (`SignPdfScreen`):** Visor por páginas, bandeja de firmas guardadas o creación rápida en sitio, arrastre libre sobre cualquier punto de la página, ajuste de escala y estampado permanente con `PDPageContentStream` respetando la rotación de página.
- **Lector de PDF integrado (`PdfReaderScreen`):** Renderizado nativo con `android.graphics.pdf.PdfRenderer`, gestos de zoom (*pinch-to-zoom* y doble toque), deslizador de páginas, modo de lectura oscura y accesos directos para firmar, compartir (`FileProvider`) o exportar copia al dispositivo.
- **Organizador intuitivo de archivos (`FilesOrganizerScreen`):** Carpetas personalizadas con selector de color, marcado de favoritos, búsqueda en tiempo real, filtros por categoría (*Todos*, *Unidos*, *Imágenes*, *Firmados*, *Importados*), ordenación y menú completo de acciones (abrir, firmar, mover a carpeta, renombrar, compartir, exportar y eliminar).
- **Modo de Pruebas QA (Usuario Test con 5 toques):** Puerta trasera de acceso rápido para pruebas en la pantalla de bienvenida (`AuthScreen`): al pulsar 5 veces consecutivas sobre la fila *"Une PDFs y convierte PNG/JPEG"* (dentro de una ventana de 3 segundos), se abre un diálogo para ingresar con un nombre de usuario de prueba (`LocalTestLibraryRepository` con almacenamiento local aislado en JSON en `filesDir/test_sandbox/`), permitiendo probar todas las herramientas de PDF, firmas y organización sin cuenta de Google ni escribir en Firestore, con indicador y salida en la pestaña *Cuenta*.
