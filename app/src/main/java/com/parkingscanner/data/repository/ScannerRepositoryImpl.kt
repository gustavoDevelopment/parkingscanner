package com.parkingscanner.data.repository

import android.content.Context
import com.parkingscanner.data.model.TicketDto
import com.parkingscanner.domain.model.CatalogoMediosPago
import com.parkingscanner.domain.model.Scanner
import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.model.Ticket
import com.parkingscanner.domain.repository.ScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ScannerRepositoryImpl(private val context: Context) : ScannerRepository {

    private val parkingScannerDir: File by lazy {
        File(context.filesDir, "ParkingScanner")
    }

    init {
        if (!parkingScannerDir.exists()) {
            parkingScannerDir.mkdirs()
        }
    }

    override suspend fun createScanner(name: String, description: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$name.json")
            if (!file.exists()) {
                val jsonArray = JSONArray()
                file.writeText(jsonArray.toString())
            }
            if (description.isNotEmpty()) {
                val descFile = File(parkingScannerDir, "descriptions.json")
                val descObj = if (descFile.exists()) JSONObject(descFile.readText()) else JSONObject()
                descObj.put(name, description)
                descFile.writeText(descObj.toString())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadScanner(name: String): Result<Scanner> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$name.json")
            val content = file.readText()
            val jsonArray = JSONArray(content)
            
            val tickets = mutableListOf<Ticket>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val dto = TicketDto.fromJson(json)
                tickets.add(dto.toDomain())
            }
            
            Result.success(Scanner(name, tickets, Date(file.lastModified())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAllScannerSummaries(): Result<List<ScannerSummary>> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = parkingScannerDir.listFiles { _, name ->
                name.endsWith(".json") && name != "tickets.json" && name != "catalogos.json"
                    && name != "descriptions.json" && name != "global_index.json"
            } ?: emptyArray()

            val descFile = File(parkingScannerDir, "descriptions.json")
            val descObj = if (descFile.exists()) JSONObject(descFile.readText()) else JSONObject()

            val summaries = jsonFiles
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    val name = file.nameWithoutExtension
                    try {
                        val jsonArray = JSONArray(file.readText())
                        var computaTotal = 0.0
                        for (i in 0 until jsonArray.length()) {
                            val ticket = jsonArray.getJSONObject(i)
                            val medioPagoCodigo = ticket.optInt("medioPagoCodigo", 0)
                            val medioPago = CatalogoMediosPago.obtenerPorCodigo(medioPagoCodigo)
                            if (medioPago?.computa == true) {
                                computaTotal += effectiveAmount(
                                ticket.optString("total", ""),
                                ticket.optString("tiempo", "")
                            )
                            }
                        }
                        val description = descObj.optString(name, "")
                        ScannerSummary(name, jsonArray.length(), computaTotal, description)
                    } catch (e: Exception) {
                        ScannerSummary(name, 0, 0.0)
                    }
                }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAllScanners(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = parkingScannerDir.listFiles { _, name ->
                name.endsWith(".json") && name != "tickets.json" && name != "catalogos.json"
                    && name != "descriptions.json" && name != "global_index.json"
            } ?: emptyArray()
            
            val names = jsonFiles
                .sortedByDescending { it.lastModified() }
                .map { it.nameWithoutExtension }
            
            Result.success(names)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTicket(scannerName: String, ticket: Ticket): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val content = file.readText()
            val jsonArray = JSONArray(content)

            jsonArray.put(TicketDto.toJson(ticket.toDto()))

            file.writeText(jsonArray.toString())
            GlobalTicketIndex(context).addTicket(ticket.boleta, scannerName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun closeScanner(scannerName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val content = file.readText()
            val jsonArray = JSONArray(content)
            
            // Generate TXT file
            val txtFile = File(parkingScannerDir, "$scannerName.txt")
            val txtWriter = FileWriter(txtFile)
            txtWriter.write("Scanner:$scannerName\n")
            txtWriter.write("Created:${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())}\n")
            txtWriter.write("TotalTickets:${jsonArray.length()}\n")
            txtWriter.write("=".repeat(50))
            txtWriter.write("\n\n")
            
            for (i in 0 until jsonArray.length()) {
                val ticket = jsonArray.getJSONObject(i)
                txtWriter.write("---Ticket${i + 1}---\n")
                txtWriter.write("timestamp:${ticket.optString("timestamp", "")}\n")
                txtWriter.write("medioPagoCodigo:${ticket.optInt("medioPagoCodigo", 0)}\n")
                txtWriter.write("boleta:${ticket.optString("boleta", "")}\n")
                txtWriter.write("extractedText:${ticket.optString("extractedText", "")}\n")
                txtWriter.write("\n")
            }
            txtWriter.close()
            
            // Generate CSV file
            val csvFile = File(parkingScannerDir, "$scannerName.csv")
            val csvWriter = FileWriter(csvFile)
            fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
            csvWriter.write("Boleta,Inmueble,VigilanteIngreso,VigilanteSalida,Placa,TipoVehiculo,FechaEntrada,FechaSalida,Tiempo,Total,MedioPagoCodigo,Timestamp\n")

            for (i in 0 until jsonArray.length()) {
                val t = jsonArray.getJSONObject(i)
                csvWriter.write(listOf(
                    q(t.optString("boleta", "")),
                    q(t.optString("inmueble", "")),
                    q(t.optString("vigilanteIngreso", "")),
                    q(t.optString("vigilanteSalida", "")),
                    q(t.optString("placa", "")),
                    q(t.optString("tipoVehiculo", "")),
                    q(t.optString("fechaEntrada", "")),
                    q(t.optString("fechaSalida", "")),
                    q(t.optString("tiempo", "")),
                    q(t.optString("total", "")),
                    t.optInt("medioPagoCodigo", 0).toString(),
                    q(t.optString("timestamp", ""))
                ).joinToString(",") + "\n")
            }
            csvWriter.close()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteScanner(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$name.json")
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllScanners(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = parkingScannerDir.listFiles { _, name ->
                name.endsWith(".json") && name != "tickets.json" && name != "catalogos.json"
                    && name != "descriptions.json" && name != "global_index.json"
            } ?: emptyArray()

            jsonFiles.forEach { file ->
                file.delete()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTicket(scannerName: String, ticketId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val jsonArray = JSONArray(file.readText())

            var boletaToRemove = ""
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val ticket = jsonArray.getJSONObject(i)
                if (ticket.getString("id") != ticketId) {
                    newArray.put(ticket)
                } else {
                    boletaToRemove = ticket.optString("boleta", "")
                }
            }

            GlobalTicketIndex(context).removeTicket(boletaToRemove, scannerName)
            file.writeText(newArray.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTicket(scannerName: String, ticket: Ticket): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val jsonArray = JSONArray(file.readText())

            for (i in 0 until jsonArray.length()) {
                val ticketJson = jsonArray.getJSONObject(i)
                if (ticketJson.getString("id") == ticket.id) {
                    ticketJson.put("boleta", ticket.boleta)
                    ticketJson.put("inmueble", ticket.inmueble)
                    ticketJson.put("vigilanteIngreso", ticket.vigilanteIngreso)
                    ticketJson.put("vigilanteSalida", ticket.vigilanteSalida)
                    ticketJson.put("placa", ticket.placa)
                    ticketJson.put("tipoVehiculo", ticket.tipoVehiculo)
                    ticketJson.put("fechaEntrada", ticket.fechaEntrada)
                    ticketJson.put("fechaSalida", ticket.fechaSalida)
                    ticketJson.put("tiempo", ticket.tiempo)
                    ticketJson.put("total", ticket.total)
                    ticketJson.put("extractedText", ticket.extractedText)
                    ticketJson.put("data", org.json.JSONObject(ticket.data as Map<*, *>))
                    ticketJson.put("medioPagoCodigo", ticket.medioPagoCodigo)
                    break
                }
            }

            file.writeText(jsonArray.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllTickets(scannerName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Scanner file not found"))
            }

            // Eliminar todos los tickets vaciando el JSONArray
            val emptyArray = org.json.JSONArray()
            file.writeText(emptyArray.toString(4))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportToTxt(scannerName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.txt")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportToCsv(scannerName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val jsonFile = File(parkingScannerDir, "$scannerName.json")
            if (!jsonFile.exists()) {
                return@withContext Result.failure(Exception("Scanner file not found"))
            }

            val content = jsonFile.readText()
            val jsonObject = org.json.JSONObject(content)
            val ticketsArray = jsonObject.getJSONArray("tickets")

            val csvFile = File(parkingScannerDir, "$scannerName.csv")
            val writer = csvFile.bufferedWriter()

            // Escribir encabezados según estructura especificada
            writer.write("BOLETA No,APARTAMENTO,Vigilante Entrada,Vigilante Salida,VEHICULO,Fecha Entrada,Fecha Salida,TIEMPO,VALOR,MEDIO DE PAGO\n")
            writer.write("Codigo,Codigo,Nombres y Apellidos,Codigo,Nombres y Apellidos,Codigo,Placa,Tipo,codigo,Tipo\n")

            var totalSumatoria = 0.0

            // Escribir datos de tickets con códigos
            for (i in 0 until ticketsArray.length()) {
                val ticket = ticketsArray.getJSONObject(i)
                
                // Obtener códigos de los catálogos
                val vigilanteIngresoNombre = ticket.optString("vigilanteIngreso", "")
                val vigilanteSalidaNombre = ticket.optString("vigilanteSalida", "")
                val tipoVehiculoNombre = ticket.optString("tipoVehiculo", "")
                val medioPagoCodigo = ticket.optInt("medioPagoCodigo", 0)
                
                // Buscar códigos en catálogos
                val vigilanteIngresoCodigo = com.parkingscanner.domain.model.CatalogoVigilantes.buscarPorNombre(vigilanteIngresoNombre)?.codigo ?: ""
                val vigilanteSalidaCodigo = com.parkingscanner.domain.model.CatalogoVigilantes.buscarPorNombre(vigilanteSalidaNombre)?.codigo ?: ""
                val tipoVehiculoCodigo = com.parkingscanner.domain.model.CatalogoVehiculos.buscarPorTipo(tipoVehiculoNombre)?.codigo ?: ""
                val medioPagoTipo = com.parkingscanner.domain.model.CatalogoMediosPago.obtenerPorCodigo(medioPagoCodigo)?.tipo ?: ""
                
                // Calcular valor total (solo medios de pago que computan)
                val valorStr = ticket.optString("total", "").replace("@", "").trim()
                val valor = valorStr.replace(",", "").toDoubleOrNull() ?: 0.0
                val medioPagoObj = com.parkingscanner.domain.model.CatalogoMediosPago.obtenerPorCodigo(medioPagoCodigo)
                if (medioPagoObj?.computa == true) {
                    totalSumatoria += valor
                }

                // Escribir fila con códigos
                val row = "${ticket.optString("boleta", "")}," +
                          "${ticket.optString("inmueble", "")}," +
                          "$vigilanteIngresoCodigo,$vigilanteIngresoNombre," +
                          "$vigilanteSalidaCodigo,$vigilanteSalidaNombre," +
                          "$tipoVehiculoCodigo,${ticket.optString("placa", "")},$tipoVehiculoNombre," +
                          "${ticket.optString("fechaEntrada", "")}," +
                          "${ticket.optString("fechaSalida", "")}," +
                          "${ticket.optString("tiempo", "")}," +
                          "$valorStr,$medioPagoCodigo,$medioPagoTipo\n"
                
                writer.write(row)
            }

            // Escribir sumatoria total
            writer.write("\nSUMATORIA TOTAL: $totalSumatoria\n")

            writer.close()
            Result.success(csvFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun effectiveAmount(total: String, tiempo: String): Double {
    val hours = Regex("\\d+").find(tiempo)?.value?.toDoubleOrNull() ?: 0.0
    val fromTiempo = hours * 1000.0
    val fromTotal = parseColombianAmount(total)
    return when {
        fromTiempo > 0.0 -> fromTiempo
        else -> fromTotal
    }
}

fun parseColombianAmount(raw: String): Double {
    val cleaned = raw.replace("$", "").replace("@", "").replace("S", "").trim()
    if (cleaned.isEmpty()) return 0.0
    return if (cleaned.contains(",")) {
        cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    } else {
        cleaned.replace(".", "").toDoubleOrNull() ?: 0.0
    }
}

// Extension functions
fun Ticket.toDto(): TicketDto {
    return TicketDto(
        id = id,
        boleta = boleta,
        inmueble = inmueble,
        vigilanteIngreso = vigilanteIngreso,
        vigilanteSalida = vigilanteSalida,
        placa = placa,
        tipoVehiculo = tipoVehiculo,
        fechaEntrada = fechaEntrada,
        fechaSalida = fechaSalida,
        tiempo = tiempo,
        total = total,
        extractedText = extractedText,
        data = org.json.JSONObject(data as Map<*, *>),
        medioPagoCodigo = medioPagoCodigo,
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(timestamp)
    )
}

fun TicketDto.toDomain(): Ticket {
    val dataMap = mutableMapOf<String, String>()
    val keys = data.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        dataMap[key] = data.optString(key, "")
    }
    
    return Ticket(
        id = id,
        boleta = boleta,
        total = total,
        vigilanteIngreso = vigilanteIngreso,
        vigilanteSalida = vigilanteSalida,
        extractedText = extractedText,
        data = dataMap,
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(timestamp) ?: Date()
    )
}
