# Análisis del Proyecto ParkingScanner

**Fecha de análisis:** 28 de abril de 2026

## Estructura del Proyecto
- **App Android**: `app/` - Aplicación nativa en Kotlin
- **Web App**: `web/` - Aplicación web con JavaScript/Python

## App Android

### Tecnologías
- Kotlin 1.9.0, Gradle 8.1.0
- CameraX 1.3.0 (cámara)
- ML Kit Text Recognition 16.0.0 (OCR)
- Coroutines 1.7.3 (async)
- ViewBinding habilitado
- Min SDK 24, Target SDK 34
- Namespace: `com.parkingscanner`

### Dependencias principales
```gradle
- androidx.core:core-ktx:1.12.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.10.0
- androidx.constraintlayout:constraintlayout:2.1.4
- androidx.camera:camera-core:1.3.0
- androidx.camera:camera-camera2:1.3.0
- androidx.camera:camera-lifecycle:1.3.0
- androidx.camera:camera-view:1.3.0
- com.google.mlkit:text-recognition:16.0.0
- kotlinx-coroutines-android:1.7.3
```

### Funcionalidades principales
- Vista de cámara en tiempo real (cámara trasera)
- Captura de fotos con CameraX
- Extracción de texto con ML Kit Text Recognition
- Validación de nombre de archivo: formato `YYYY(DD-MM to DD-MM)`
- Guardado en almacenamiento interno como JSON
- Ver archivos guardados
- Compartir archivos JSON via FileProvider
- Eliminar archivos

### Flujo de la aplicación
1. Solicitar permiso de cámara (runtime)
2. Iniciar cámara (CameraSelector.DEFAULT_BACK_CAMERA)
3. Capturar foto (ImageCapture)
4. Procesar imagen con ML Kit OCR (InputImage.fromMediaImage)
5. Mostrar diálogo para ingresar nombre de archivo
6. Validar formato con Regex: `^\d{4}\(\d{2}-\d{2} to \d{2}-\d{2}\)$`
7. Guardar JSON en `filesDir/` con estructura:
   ```json
   {
       "filename": "2024(01-15 to 01-20)",
       "extractedText": "...",
       "timestamp": "2024-01-15 14:30:45"
   }
   ```
8. Mostrar resultado y permitir nuevo escaneo

### Permisos en AndroidManifest
- `android.hardware.camera.any`
- `android.permission.CAMERA`
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `android.permission.READ_EXTERNAL_STORAGE`
- FileProvider configurado para compartir archivos

### Archivo principal
- `MainActivity.kt` (319 líneas)
  - Usa coroutines para operaciones I/O
  - ActivityResultContracts para permisos
  - ProcessCameraProvider para CameraX
  - AlertDialogs para interacción con usuario

## Web App

### Tecnologías
- Tesseract.js 5 (OCR en navegador)
- Python http.server (servidor local simple)
- LocalStorage (persistencia en navegador)
- HTML5/CSS3 con diseño moderno
- JavaScript ES6+

### Archivos
- `index.html` (256 líneas) - UI con gradientes y diseño responsive
- `app.js` (246 líneas) - Lógica de la aplicación
- `server.py` (25 líneas) - Servidor HTTP en puerto 8000

### Funcionalidades
- Cámara web usando `navigator.mediaDevices.getUserMedia`
- Captura de foto a canvas
- Preview de imagen capturada
- OCR con Tesseract.js (idioma inglés)
- Validación de nombre (mismo formato que Android)
- Descarga JSON como archivo
- Guardado en LocalStorage bajo clave `parkingTickets`
- Ver, descargar, eliminar archivos desde LocalStorage
- Servidor accesible desde red local (0.0.0.0:8000)

### Estructura de datos en LocalStorage
```json
[
    {
        "filename": "2024(01-15 to 01-20)",
        "data": {
            "filename": "2024(01-15 to 01-20)",
            "extractedText": "...",
            "timestamp": "2024-01-15T14:30:45.000Z"
        },
        "savedAt": "2024-01-15T14:30:45.000Z"
    }
]
```

### Diferencias clave entre Android y Web
- **OCR**: Android usa ML Kit (nativo, más rápido), Web usa Tesseract.js (browser, más lento)
- **Almacenamiento**: Android guarda en archivos del sistema, Web usa LocalStorage
- **Acceso**: Android requiere instalación APK, Web es accesible vía navegador
- **Compartir**: Android tiene integración nativa, Web descarga archivo

## Propósito del Proyecto
Escanear recibos/tickets de estacionamiento, extraer texto mediante OCR y organizar los registros por rangos de fechas para llevar control de gastos de parking. El formato de nombre `YYYY(DD-MM to DD-MM)` sugiere que se usa para registrar períodos de estacionamiento (ej: tickets semanales o mensuales).

## Configuración de Build
- Gradle 8.1.0
- Java 8 compatibility
- Proguard habilitado en release
- `local.properties` requiere `sdk.dir` configurado

## Comandos útiles
```bash
# Copiar template de local.properties
cp local.properties.template local.properties

# Build APK
./gradlew assembleDebug

# APK output
app/build/outputs/apk/debug/app-debug.apk

# Instalar en dispositivo
adb install app/build/outputs/apk/debug/app-debug.apk

# Iniciar servidor web
cd web
python3 server.py
```
