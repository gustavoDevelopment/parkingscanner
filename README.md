# Parking Scanner

Simple Android app that uses camera to capture receipts/parking tickets and extracts text using OCR (ML Kit).

## Features
- Camera preview with CameraX
- Text extraction using ML Kit Text Recognition
- Create scanner session with custom name format YYYY(DD-MM A DD-MM)
- Capture multiple tickets and add to scanner session
- Auto-generated JSON file with all captured tickets
- Close scanner to generate plain text file with key:value format
- Files uploaded to server for backup
- Catalog system for vigilantes, vehicles, and payment methods
- CSV export with specific format using codes
- Ticket editing functionality
- Payment method selection before adding ticket

## Ticket Structure

The app captures parking tickets with the following structure:

```
Carrera 103 B # 82-92
9035
INMUEBLE: 2401
TARIFA: HORA
VIGILANTE ENTRADA: BANIA
JUMULAY JAIMES BECERRA
VIGILANTE SALIDA:Claudia
Gonsalez
PLACA: 
TIPO VEHICULO: MOTO/AUTOMOVIL/ ELECTRICA
FECHA ENTRADA: 20/04/2026
23:31:29
FECHA SALIDA:20/04/2026
23:31:29
TIEMPO: 16 HORA
TIEMPO GRACIA: 0
VALOR A PAGAR: $ 1.000,00
IMPUESTO $0,00
TOTAL A PAGAR: $ 1.000,00
```

**Important:** The payment method (MEDIO DE PAGO) is NOT included in the ticket. The app prompts the user to select the payment method from the catalog before adding the ticket.

## Catalogs

The app uses predefined catalogs with codes:

### Vigilantes (Security Guards)
1. Sara Daniela SanMiguel Vera
2. Bania Jumalay Jiames Becerra
3. Claudia Gonzales

### Vehículos (Vehicles)
1. Automovil
2. Moto
3. Electrica

### Medios de Pago (Payment Methods)
1. Efectivo (Cash)
2. QR
3. ANULADO (Void)

## CSV Export Format

The CSV is generated with the following structure using codes:

```
BOLETA No,APARTAMENTO,Vigilante Entrada,Vigilante Salida,VEHICULO,Fecha Entrada,Fecha Salida,TIEMPO,VALOR,MEDIO DE PAGO
Codigo,Codigo,Nombres y Apellidos,Codigo,Nombres y Apellidos,Codigo,Placa,Tipo,codigo,Tipo
```

The CSV also includes a total sum of all ticket values at the end.

## Requirements
- Android Studio
- Android SDK (API 24+)
- Gradle 8.1.0

## Setup

1. Copy `local.properties.template` to `local.properties` and update `sdk.dir` to your Android SDK path:
   ```bash
   cp local.properties.template local.properties
   # Edit local.properties and set: sdk.dir=/Users/your_username/Library/Android/sdk
   ```

2. Open project in Android Studio or build from command line:
   ```bash
   cd personals/parkingScanner
   ./gradlew assembleDebug
   ```

3. Install APK on device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Build APK

```bash
cd personals/parkingScanner
./gradlew assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. Tap "New Scanner" button
2. Enter scanner name in format: YYYY(DD-MM A DD-MM) - Example: 2024(01-15 A 01-20)
3. Camera starts automatically
4. Point camera at receipt/ticket and tap "Capture"
5. App extracts text and prompts to add to scanner
6. Tap "Add" to add ticket to current scanner
7. Repeat steps 4-6 for multiple tickets
8. Tap "Close Scanner" when done
9. App generates plain text file with key:value format
10. JSON saved to: `/data/data/com.parkingscanner/files/ParkingScanner/YYYY(DD-MM A DD-MM).json`
11. Text file saved to: `/data/data/com.parkingscanner/files/ParkingScanner/YYYY(DD-MM A DD-MM).txt`
12. Files uploaded to server: `http://192.168.1.3:8000`

## JSON Output Format

```json
{
    "scannerName": "2024(01-15 A 01-20)",
    "tickets": [
        {
            "extractedText": "...OCR extracted text...",
            "timestamp": "2024-01-15T14:30:45.000Z"
        }
    ],
    "createdAt": "2024-01-15T14:30:45.000Z",
    "lastUpdated": "2024-01-15T14:30:45.000Z"
}
```

## Plain Text Output Format

```
Scanner:2024(01-15 A 01-20)
Created:2024-01-15T14:30:45.000Z
TotalTickets:1
==================================================

---Ticket1---
timestamp:2024-01-15T14:30:45.000Z
extractedText:...OCR extracted text...
```

## Notes

- OCR accuracy depends on image quality and lighting
- Saved files are in app internal storage (not accessible without root)
- Filename must match format: YYYY(DD-MM to DD-MM) - validation enforced
- For external storage access, add additional permissions and modify save logic
# parkingscanner
