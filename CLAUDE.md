# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build and install in one step
./gradlew installDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

**Setup:** Copy `local.properties.template` to `local.properties` and set `sdk.dir` to your Android SDK path.

## Architecture

The app follows Clean Architecture with three layers:

**Domain layer** (`domain/`): Pure Kotlin models and use cases. Each operation has its own use case class (e.g., `AddTicketUseCase`, `ExportToCsvUseCase`). The repository interface `ScannerRepository` defines the contract.

**Data layer** (`data/`): `ScannerRepositoryImpl` persists scanners as JSON arrays in app-internal storage at `filesDir/ParkingScanner/<scannerName>.json`. Closing a scanner generates `.txt` and `.csv` files from the JSON. `CameraService` wraps CameraX; `OcrService` wraps ML Kit text recognition.

**Presentation layer** (`presentation/`): `ScannerViewModel` holds `ScannerUiState` (LiveData) and dispatches to use cases via coroutines. `ScannerViewModelFactory` manually wires dependencies (no DI framework). Activities use ViewBinding.

## Key Domain Concepts

- **Scanner** (UI: "Fecha de Recogida"): A session named `YYYY(DD-MM A DD-MM)` (e.g., `2024(01-15 A 01-20)`). Stored as a JSON array of tickets.
- **Ticket**: A parking receipt with fields: `boleta`, `inmueble`, `vigilanteIngreso`, `vigilanteSalida`, `placa`, `tipoVehiculo`, `fechaEntrada`, `fechaSalida`, `tiempo`, `total`, `medioPagoCodigo`, `extractedText`.
- **Catalogs**: Persisted via `CatalogStorage` to `catalogos.json`. Singleton objects `CatalogoVigilantes`, `CatalogoVehiculos`, `CatalogoMediosPago` in `domain/model/Catalogs.kt`. Payment method (`medioPago`) is selected by the user in a dialog before adding the ticket — it is NOT stored on the ticket during OCR.
- **MedioPago.computa**: Boolean flag indicating whether the payment method counts toward the total. `false` means anulado/cancelled.
- **ScannerSummary**: `(name, ticketCount, computaTotal, description)` — used in the main list.

## File Storage

All files go to `context.filesDir/ParkingScanner/`:
- `<name>.json` — active scanner (JSONArray of tickets)
- `<name>.txt` — generated on close
- `<name>.csv` — generated on close and via export
- `descriptions.json` — maps scanner name → description string
- `catalogos.json` — persisted catalogs (CatalogStorage)
- `global_index.json` — maps boleta number → list of scanner names (GlobalTicketIndex)

## Key Behaviors & Bug Fixes Applied

- **Total parsing**: Strip all non-digits with `replace(Regex("[^\\d]"), "")` before parsing — Colombian format uses `.` as thousands separator.
- **Duplicate boleta check**: Both sides trimmed: `it.boleta.trim() == newBoleta.trim()`.
- **Duplicate scanner name check**: Uses `state.scannerSummaries.map{it.name}.ifEmpty { state.scannerNames }` to handle empty-list edge case.
- **OcrService tipoVehiculo**: Matches both `"vehiculo"` and `"vehículo"` (accented).
- **Pie chart classification**: Check `tipo.contains("qr")` BEFORE checking `!computa` to avoid QR being classified as anulado.
- **GlobalTicketIndex**: Entries with the same boleta in multiple scanners are shown in orange (`#FF9800`) in GlobalIndexActivity.

## UI Terminology

- App title: **"Parking Scanner"**
- Main list header: **"Fechas de Recogida"** (was "Recorridas")
- Dialog titles use: "Fecha de Recogida" / "Nueva Fecha de Recogida"
- Global index column: "Fecha de Recogida"

## FAB Colors

All `+` FABs use `app:backgroundTint="#1976D2"` (blue). The close/action button uses `#4CAF50` (green, intentional).
