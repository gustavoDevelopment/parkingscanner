package com.parkingscanner.data.service

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrService {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener {
                Log.e("OcrService", "Text recognition failed", it)
                continuation.resumeWithException(it)
            }
    }

    fun transformToKeyValue(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableMapOf<String, String>()

        var i = 0
        var boletaFound = false

        while (i < lines.size) {
            val line = lines[i]
            val lower = line.lowercase()

            // ── Dirección + boleta ──────────────────────────────────────────
            if (!boletaFound && (lower.contains("carrera") || lower.contains("calle") || lower.contains("avenida") || lower.contains("#"))) {
                // El número de boleta es la siguiente línea que sea solo dígitos
                if (i + 1 < lines.size && lines[i + 1].matches(Regex("\\d+"))) {
                    result["boleta"] = lines[i + 1]
                    boletaFound = true
                    i += 2
                    continue
                }
                i++; continue
            }

            // Número solo al inicio (boleta sin dirección previa)
            if (!boletaFound && line.matches(Regex("\\d{3,6}"))) {
                result["boleta"] = line
                boletaFound = true
                i++; continue
            }

            // ── TOTAL A PAGAR ───────────────────────────────────────────────
            if (lower.contains("total") && lower.contains("pagar")) {
                val v = valueAfterColon(line)
                    .ifEmpty { numberOnNextLine(lines, i) }
                    .replace("@", "").trim()
                if (v.isNotEmpty()) result["total"] = v
                break // último campo relevante
            }

            // ── VALOR A PAGAR ───────────────────────────────────────────────
            if (lower.contains("valor") && lower.contains("pagar")) {
                val v = valueAfterColon(line).replace("@", "").trim()
                if (v.isNotEmpty()) result["valorAPagar"] = v
                i++; continue
            }

            // ── INMUEBLE ────────────────────────────────────────────────────
            if (lower.contains("inmueble")) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["inmueble"] = v
                i++; continue
            }

            // ── TARIFA ──────────────────────────────────────────────────────
            if (lower.startsWith("tarifa")) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["tarifa"] = v
                i++; continue
            }

            // ── VIGILANTE ENTRADA / INGRESO ─────────────────────────────────
            if (lower.contains("vigilante") && (lower.contains("entrada") || lower.contains("ingreso"))) {
                val nombre = extractMultiLineName(lines, i, valueAfterColon(line))
                if (nombre.isNotEmpty()) result["vigilanteIngreso"] = nombre
                i += if (nombre.contains(" ") || valueAfterColon(line).isEmpty()) 2 else 1
                continue
            }

            // ── VIGILANTE SALIDA ────────────────────────────────────────────
            if (lower.contains("vigilante") && lower.contains("salida")) {
                val nombre = extractMultiLineName(lines, i, valueAfterColon(line))
                if (nombre.isNotEmpty()) result["vigilanteSalida"] = nombre
                i += if (nombre.contains(" ") || valueAfterColon(line).isEmpty()) 2 else 1
                continue
            }

            // ── FECHA ENTRADA ───────────────────────────────────────────────
            if (lower.contains("fecha") && lower.contains("entrada")) {
                val v = valueAfterColon(line).ifEmpty { numberOnNextLine(lines, i) }
                if (v.isNotEmpty()) result["fechaEntrada"] = v
                i++; continue
            }

            // ── FECHA SALIDA ────────────────────────────────────────────────
            if (lower.contains("fecha") && lower.contains("salida")) {
                val v = valueAfterColon(line).ifEmpty { numberOnNextLine(lines, i) }
                if (v.isNotEmpty()) result["fechaSalida"] = v
                i++; continue
            }

            // ── TIEMPO GRACIA ───────────────────────────────────────────────
            if (lower.contains("tiempo") && lower.contains("gracia")) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["tiempoGracia"] = v
                i++; continue
            }

            // ── TIEMPO ──────────────────────────────────────────────────────
            if (lower.startsWith("tiempo")) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["tiempo"] = v
                i++; continue
            }

            // ── PLACA ───────────────────────────────────────────────────────
            if (lower.contains("placa")) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["placa"] = v
                i++; continue
            }

            // ── TIPO VEHICULO ───────────────────────────────────────────────
            if (lower.contains("tipo") && (lower.contains("vehiculo") || lower.contains("vehículo"))) {
                val v = valueAfterColon(line)
                if (v.isNotEmpty()) result["tipoVehiculo"] = v
                i++; continue
            }

            // ── IMPUESTO ────────────────────────────────────────────────────
            if (lower.contains("impuesto")) {
                val v = valueAfterColon(line).replace("@", "").trim()
                if (v.isNotEmpty()) result["impuesto"] = v
                i++; continue
            }

            i++
        }

        return org.json.JSONObject(result as Map<String, Any>).toString(4)
    }

    private fun valueAfterColon(line: String): String {
        val idx = line.indexOf(":")
        return if (idx >= 0 && idx < line.length - 1) line.substring(idx + 1).trim() else ""
    }

    private fun numberOnNextLine(lines: List<String>, i: Int): String {
        if (i + 1 >= lines.size) return ""
        val next = lines[i + 1].trim()
        val nextLower = next.lowercase()
        // Reject lines that look like new field labels
        val isLabel = nextLower.contains("vigilante") || nextLower.contains("fecha") ||
                nextLower.contains("tiempo") || nextLower.contains("valor") ||
                nextLower.contains("total") || nextLower.contains("impuesto") ||
                nextLower.contains("tarifa") || nextLower.contains("placa") ||
                nextLower.contains("tipo") || nextLower.contains("inmueble") ||
                nextLower.contains("carrera") || nextLower.contains("calle")
        return if (!isLabel && next.isNotEmpty()) next else ""
    }

    /**
     * Combines value on the same line as the label with the next line when the name
     * spans two lines (e.g. "VIGILANTE ENTRADA: BANIA\nJUMULAY JAIMES BECERRA").
     */
    private fun extractMultiLineName(lines: List<String>, i: Int, sameLineValue: String): String {
        if (i + 1 >= lines.size) return sameLineValue

        val nextLine = lines[i + 1].trim()
        val nextLower = nextLine.lowercase()
        val isKeyword = nextLower.contains("vigilante") || nextLower.contains("fecha") ||
                nextLower.contains("tiempo") || nextLower.contains("valor") ||
                nextLower.contains("total") || nextLower.contains("impuesto") ||
                nextLower.contains("tarifa") || nextLower.contains("placa") ||
                nextLower.contains("tipo") || nextLower.contains("inmueble") ||
                nextLine.matches(Regex("\\d+"))

        return if (!isKeyword && nextLine.isNotEmpty()) {
            if (sameLineValue.isNotEmpty()) "$sameLineValue $nextLine" else nextLine
        } else {
            sameLineValue
        }
    }
}
