package com.parkingscanner.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
        if (!parkingScannerDir.exists()) parkingScannerDir.mkdirs()
    }

    // ── Firestore helpers ────────────────────────────────────────────────────

    private fun scannersRef() =
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("scanners")
        }

    private fun ticketsRef(scannerName: String) =
        scannersRef()?.document(scannerName)?.collection("tickets")

    private fun fsAddTicket(scannerName: String, ticketJson: JSONObject) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        android.util.Log.d("FS_SYNC", "fsAddTicket uid=$uid scanner=$scannerName")
        if (uid == null) { android.util.Log.e("FS_SYNC", "fsAddTicket: no user logged in"); return }
        try {
            val id = ticketJson.optString("id")
            if (id.isEmpty()) { android.util.Log.e("FS_SYNC", "fsAddTicket: empty id"); return }
            val data = jsonObjectToMap(ticketJson).toMutableMap()
            val tsMillis = ticketJson.optString("timestamp").toLongOrNull()
            if (tsMillis != null) data["timestamp"] = Timestamp(tsMillis / 1000, ((tsMillis % 1000) * 1_000_000).toInt())
            Tasks.await(ticketsRef(scannerName)?.document(id)?.set(data) ?: return)
            android.util.Log.d("FS_SYNC", "fsAddTicket: OK id=$id")
            updateScannerMeta(scannerName)
        } catch (e: Exception) { android.util.Log.e("FS_SYNC", "fsAddTicket error", e) }
    }

    private fun fsDeleteTicket(scannerName: String, ticketId: String) {
        try {
            Tasks.await(ticketsRef(scannerName)?.document(ticketId)?.delete() ?: return)
            updateScannerMeta(scannerName)
        } catch (_: Exception) {}
    }

    private fun fsUpdateTicket(scannerName: String, ticketJson: JSONObject) {
        try {
            val id = ticketJson.optString("id")
            if (id.isEmpty()) return
            val data = jsonObjectToMap(ticketJson)
            Tasks.await(ticketsRef(scannerName)?.document(id)?.set(data) ?: return)
        } catch (_: Exception) {}
    }

    private fun fsFullSync(scannerName: String, jsonArray: JSONArray, description: String = "") {
        try {
            val ref = scannersRef() ?: return
            val tRef = ref.document(scannerName).collection("tickets")
            val db = FirebaseFirestore.getInstance()

            // Delete existing tickets in batches of 500
            val existing = Tasks.await(tRef.get())
            var batch = db.batch()
            var count = 0
            for (doc in existing.documents) {
                batch.delete(doc.reference)
                if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
            }
            if (count > 0) Tasks.await(batch.commit())

            // Write new tickets in batches of 500
            batch = db.batch()
            count = 0
            for (i in 0 until jsonArray.length()) {
                val t = jsonArray.getJSONObject(i)
                val id = t.optString("id")
                if (id.isEmpty()) continue
                val tData = jsonObjectToMap(t).toMutableMap()
                val tsMs = t.optString("timestamp").toLongOrNull()
                if (tsMs != null) tData["timestamp"] = Timestamp(tsMs / 1000, ((tsMs % 1000) * 1_000_000).toInt())
                batch.set(tRef.document(id), tData)
                if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
            }
            if (count > 0) Tasks.await(batch.commit())

            // Update scanner document metadata
            val meta = mutableMapOf<String, Any>(
                "ticketCount" to jsonArray.length(),
                "lastUpdated" to Timestamp.now()
            )
            if (description.isNotEmpty()) meta["description"] = description
            Tasks.await(ref.document(scannerName).set(meta, SetOptions.merge()))
        } catch (_: Exception) {}
    }

    private fun updateScannerMeta(scannerName: String) {
        try {
            val ref = scannersRef() ?: return
            val count = Tasks.await(ref.document(scannerName).collection("tickets").get()).size()
            Tasks.await(ref.document(scannerName).set(
                mapOf("ticketCount" to count, "lastUpdated" to Timestamp.now()),
                SetOptions.merge()
            ))
        } catch (_: Exception) {}
    }

    private fun fsLoadTickets(scannerName: String): JSONArray? {
        return try {
            val snapshot = Tasks.await(ticketsRef(scannerName)?.get() ?: return null)
            if (snapshot.isEmpty) return null
            val arr = JSONArray()
            for (doc in snapshot.documents) {
                val obj = JSONObject()
                doc.data?.forEach { (k, v) -> obj.put(k, v) }
                arr.put(obj)
            }
            arr
        } catch (_: Exception) { null }
    }

    private fun fsRestoreAll() {
        try {
            val ref = scannersRef() ?: return
            val scanners = Tasks.await(ref.get())
            val descFile = File(parkingScannerDir, "descriptions.json")
            val descObj = if (descFile.exists()) JSONObject(descFile.readText()) else JSONObject()
            for (scannerDoc in scanners.documents) {
                val name = scannerDoc.id
                val tickets = Tasks.await(ref.document(name).collection("tickets").get())
                if (tickets.isEmpty) continue
                val arr = JSONArray()
                for (doc in tickets.documents) {
                    val obj = JSONObject()
                    doc.data?.forEach { (k, v) -> obj.put(k, v) }
                    arr.put(obj)
                }
                File(parkingScannerDir, "$name.json").writeText(arr.toString())
                scannerDoc.getString("description")?.let { if (it.isNotEmpty()) descObj.put(name, it) }
            }
            if (scanners.documents.isNotEmpty()) descFile.writeText(descObj.toString())
        } catch (_: Exception) {}
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { key -> map[key] = toFirestoreValue(obj.opt(key)) }
        return map
    }

    private fun toFirestoreValue(value: Any?): Any? = when {
        value == null || value === org.json.JSONObject.NULL -> null
        value is JSONObject -> jsonObjectToMap(value)
        value is org.json.JSONArray -> (0 until value.length()).map { toFirestoreValue(value.opt(it)) }
        else -> value
    }

    // ── ID migration: timestamp IDs → ticket_{boleta} ────────────────────────

    fun migrateTicketIds() {
        val files = parkingScannerDir.listFiles { _, n ->
            n.endsWith(".json") && n != "tickets.json" && n != "catalogos.json"
                && n != "descriptions.json" && n != "global_index.json"
        } ?: return

        for (file in files) {
            try {
                val arr = JSONArray(file.readText())
                var changed = false
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val oldId = t.optString("id", "")
                    val boleta = t.optString("boleta", "").trim()
                    if (boleta.isBlank()) continue
                    val newId = "ticket_$boleta"
                    if (oldId != newId) {
                        t.put("id", newId)
                        changed = true
                    }
                }
                if (changed) file.writeText(arr.toString())
            } catch (_: Exception) {}
        }
        android.util.Log.d("FS_SYNC", "migrateTicketIds done")
    }

    // ── Repository operations ────────────────────────────────────────────────

    override suspend fun createScanner(name: String, description: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$name.json")
            if (!file.exists()) file.writeText(JSONArray().toString())
            if (description.isNotEmpty()) {
                val descFile = File(parkingScannerDir, "descriptions.json")
                val descObj = if (descFile.exists()) JSONObject(descFile.readText()) else JSONObject()
                descObj.put(name, description)
                descFile.writeText(descObj.toString())
            }
            val meta = mutableMapOf<String, Any>("ticketCount" to 0, "lastUpdated" to Timestamp.now())
            if (description.isNotEmpty()) meta["description"] = description
            try { Tasks.await(scannersRef()?.document(name)?.set(meta, SetOptions.merge()) ?: return@withContext Result.success(Unit)) } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadScanner(name: String): Result<Scanner> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$name.json")
            val jsonArray = if (file.exists()) {
                val arr = JSONArray(file.readText())
                // Sync if Firestore is behind local (first upgrade, or missed adds)
                try {
                    val fsCount = Tasks.await(ticketsRef(name)?.get() ?: throw Exception())?.size() ?: 0
                    android.util.Log.d("FS_SYNC", "loadScanner $name: local=${arr.length()} fs=$fsCount")
                    if (fsCount < arr.length()) fsFullSync(name, arr)
                } catch (e: Exception) { android.util.Log.e("FS_SYNC", "loadScanner check error", e) }
                arr
            } else {
                val fromFs = fsLoadTickets(name)
                    ?: return@withContext Result.failure(Exception("Scanner not found"))
                file.writeText(fromFs.toString())
                fromFs
            }

            val tickets = mutableListOf<Ticket>()
            for (i in 0 until jsonArray.length()) {
                tickets.add(TicketDto.fromJson(jsonArray.getJSONObject(i)).toDomain())
            }
            Result.success(Scanner(name, tickets, Date(file.lastModified())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAllScannerSummaries(): Result<List<ScannerSummary>> = withContext(Dispatchers.IO) {
        try {
            var jsonFiles = parkingScannerDir.listFiles { _, n ->
                n.endsWith(".json") && n != "tickets.json" && n != "catalogos.json"
                    && n != "descriptions.json" && n != "global_index.json"
            } ?: emptyArray()

            if (jsonFiles.isEmpty()) {
                fsRestoreAll()
                jsonFiles = parkingScannerDir.listFiles { _, n ->
                    n.endsWith(".json") && n != "tickets.json" && n != "catalogos.json"
                        && n != "descriptions.json" && n != "global_index.json"
                } ?: emptyArray()
            }

            val descFile = File(parkingScannerDir, "descriptions.json")
            val descObj = if (descFile.exists()) JSONObject(descFile.readText()) else JSONObject()

            val summaries = jsonFiles.sortedByDescending { it.lastModified() }.map { file ->
                val name = file.nameWithoutExtension
                try {
                    val jsonArray = JSONArray(file.readText())
                    var computaTotal = 0.0
                    for (i in 0 until jsonArray.length()) {
                        val ticket = jsonArray.getJSONObject(i)
                        val medioPago = CatalogoMediosPago.obtenerPorCodigo(ticket.optInt("medioPagoCodigo", 0))
                        if (medioPago?.computa == true) {
                            computaTotal += effectiveAmount(ticket.optString("total", ""), ticket.optString("tiempo", ""))
                        }
                    }
                    ScannerSummary(name, jsonArray.length(), computaTotal, descObj.optString(name, ""))
                } catch (_: Exception) { ScannerSummary(name, 0, 0.0) }
            }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAllScanners(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = parkingScannerDir.listFiles { _, n ->
                n.endsWith(".json") && n != "tickets.json" && n != "catalogos.json"
                    && n != "descriptions.json" && n != "global_index.json"
            } ?: emptyArray()
            Result.success(jsonFiles.sortedByDescending { it.lastModified() }.map { it.nameWithoutExtension })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTicket(scannerName: String, ticket: Ticket): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val jsonArray = JSONArray(file.readText())
            val ticketJson = TicketDto.toJson(ticket.toDto())
            jsonArray.put(ticketJson)
            file.writeText(jsonArray.toString())
            GlobalTicketIndex(context).addTicket(ticket.boleta, scannerName)
            fsAddTicket(scannerName, ticketJson)
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
                val t = jsonArray.getJSONObject(i)
                if (t.getString("id") != ticketId) newArray.put(t)
                else boletaToRemove = t.optString("boleta", "")
            }
            GlobalTicketIndex(context).removeTicket(boletaToRemove, scannerName)
            file.writeText(newArray.toString())
            fsDeleteTicket(scannerName, ticketId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTicket(scannerName: String, ticket: Ticket): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            val jsonArray = JSONArray(file.readText())
            var updatedJson = JSONObject()
            for (i in 0 until jsonArray.length()) {
                val t = jsonArray.getJSONObject(i)
                if (t.getString("id") == ticket.id) {
                    val dto = ticket.toDto()
                    t.put("boleta", dto.boleta)
                    t.put("inmueble", dto.inmueble)
                    t.put("vigilanteIngreso", dto.vigilanteIngreso)
                    t.put("vigilanteIngresoCodigo", dto.vigilanteIngresoCodigo)
                    t.put("vigilanteSalida", dto.vigilanteSalida)
                    t.put("vigilanteSalidaCodigo", dto.vigilanteSalidaCodigo)
                    t.put("placa", dto.placa)
                    t.put("tipoVehiculo", dto.tipoVehiculo)
                    t.put("tipoVehiculoCodigo", dto.tipoVehiculoCodigo)
                    t.put("fechaEntrada", dto.fechaEntrada)
                    t.put("fechaSalida", dto.fechaSalida)
                    t.put("tiempo", dto.tiempo)
                    t.put("total", dto.total)
                    t.put("extractedText", dto.extractedText)
                    t.put("data", dto.data)
                    t.put("medioPagoCodigo", dto.medioPagoCodigo)
                    updatedJson = t
                    break
                }
            }
            file.writeText(jsonArray.toString())
            fsUpdateTicket(scannerName, updatedJson)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllTickets(scannerName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(parkingScannerDir, "$scannerName.json")
            if (!file.exists()) return@withContext Result.failure(Exception("Scanner file not found"))
            file.writeText(JSONArray().toString(4))
            // Batch delete all tickets in Firestore
            try {
                val tRef = ticketsRef(scannerName) ?: return@withContext Result.success(Unit)
                val db = FirebaseFirestore.getInstance()
                val existing = Tasks.await(tRef.get())
                var batch = db.batch()
                var count = 0
                for (doc in existing.documents) {
                    batch.delete(doc.reference)
                    if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
                }
                if (count > 0) Tasks.await(batch.commit())
                Tasks.await(scannersRef()?.document(scannerName)?.set(
                    mapOf("ticketCount" to 0, "lastUpdated" to Timestamp.now()), SetOptions.merge()
                ) ?: return@withContext Result.success(Unit))
            } catch (_: Exception) {}
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

            val csvFile = File(parkingScannerDir, "$scannerName.csv")
            val csvWriter = FileWriter(csvFile)
            fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
            csvWriter.write("Boleta,Inmueble,VigilanteIngreso,VigilanteSalida,Placa,TipoVehiculo,FechaEntrada,FechaSalida,Tiempo,Total,MedioPagoCodigo,Timestamp\n")
            for (i in 0 until jsonArray.length()) {
                val t = jsonArray.getJSONObject(i)
                csvWriter.write(listOf(
                    q(t.optString("boleta", "")), q(t.optString("inmueble", "")),
                    q(t.optString("vigilanteIngreso", "")), q(t.optString("vigilanteSalida", "")),
                    q(t.optString("placa", "")), q(t.optString("tipoVehiculo", "")),
                    q(t.optString("fechaEntrada", "")), q(t.optString("fechaSalida", "")),
                    q(t.optString("tiempo", "")), q(t.optString("total", "")),
                    t.optInt("medioPagoCodigo", 0).toString(), q(t.optString("timestamp", ""))
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
            File(parkingScannerDir, "$name.json").takeIf { it.exists() }?.delete()
            // Delete Firestore tickets subcollection + scanner doc
            try {
                val ref = scannersRef() ?: return@withContext Result.success(Unit)
                val db = FirebaseFirestore.getInstance()
                val existing = Tasks.await(ref.document(name).collection("tickets").get())
                var batch = db.batch()
                var count = 0
                for (doc in existing.documents) {
                    batch.delete(doc.reference)
                    if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
                }
                if (count > 0) Tasks.await(batch.commit())
                Tasks.await(ref.document(name).delete())
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameScanner(oldName: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("RENAME", "start: '$oldName' -> '$newName'")

            // Rename local JSON file (copy+delete since renameTo can fail cross-volume)
            val oldFile = File(parkingScannerDir, "$oldName.json")
            val newFile = File(parkingScannerDir, "$newName.json")
            if (oldFile.exists()) {
                newFile.writeText(oldFile.readText())
                oldFile.delete()
                android.util.Log.d("RENAME", "file renamed, newFile.exists=${newFile.exists()}")
            } else {
                android.util.Log.e("RENAME", "oldFile not found: ${oldFile.absolutePath}")
            }

            // Update descriptions.json
            val descFile = File(parkingScannerDir, "descriptions.json")
            if (descFile.exists()) {
                val obj = JSONObject(descFile.readText())
                if (obj.has(oldName)) {
                    obj.put(newName, obj.getString(oldName))
                    obj.remove(oldName)
                    descFile.writeText(obj.toString())
                }
            }

            // Update global_index.json
            GlobalTicketIndex(context).renameScanner(oldName, newName)

            // Firestore: copy tickets to new doc, delete old
            try {
                val ref = scannersRef() ?: throw Exception("not logged in")
                val db = FirebaseFirestore.getInstance()
                val oldTickets = Tasks.await(ref.document(oldName).collection("tickets").get())
                android.util.Log.d("RENAME", "fs tickets to move: ${oldTickets.size()}")
                val newTicketsRef = ref.document(newName).collection("tickets")
                var batch = db.batch()
                var count = 0
                for (doc in oldTickets.documents) {
                    val data = doc.data ?: continue
                    batch.set(newTicketsRef.document(doc.id), data)
                    batch.delete(doc.reference)
                    if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
                }
                if (count > 0) Tasks.await(batch.commit())
                val oldMeta = Tasks.await(ref.document(oldName).get()).data ?: emptyMap<String, Any>()
                Tasks.await(ref.document(newName).set(oldMeta + mapOf("lastUpdated" to Timestamp.now()), SetOptions.merge()))
                Tasks.await(ref.document(oldName).delete())
                android.util.Log.d("RENAME", "fs rename done")
            } catch (e: Exception) { android.util.Log.e("RENAME", "fs rename error", e) }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("RENAME", "rename failed", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteAllScanners(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = parkingScannerDir.listFiles { _, n ->
                n.endsWith(".json") && n != "tickets.json" && n != "catalogos.json"
                    && n != "descriptions.json" && n != "global_index.json"
            } ?: emptyArray()
            jsonFiles.forEach { it.delete() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportToTxt(scannerName: String): Result<File> = withContext(Dispatchers.IO) {
        try { Result.success(File(parkingScannerDir, "$scannerName.txt")) }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun exportToCsv(scannerName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val jsonFile = File(parkingScannerDir, "$scannerName.json")
            if (!jsonFile.exists()) return@withContext Result.failure(Exception("Scanner file not found"))
            val jsonArray = JSONArray(jsonFile.readText())
            val csvFile = File(parkingScannerDir, "$scannerName.csv")
            val writer = csvFile.bufferedWriter()
            fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
            writer.write("Boleta,Inmueble,VigilanteIngreso,VigilanteSalida,Placa,TipoVehiculo,FechaEntrada,FechaSalida,Tiempo,Total,MedioPagoCodigo,Timestamp\n")
            for (i in 0 until jsonArray.length()) {
                val t = jsonArray.getJSONObject(i)
                writer.write(listOf(
                    q(t.optString("boleta", "")), q(t.optString("inmueble", "")),
                    q(t.optString("vigilanteIngreso", "")), q(t.optString("vigilanteSalida", "")),
                    q(t.optString("placa", "")), q(t.optString("tipoVehiculo", "")),
                    q(t.optString("fechaEntrada", "")), q(t.optString("fechaSalida", "")),
                    q(t.optString("tiempo", "")), q(t.optString("total", "")),
                    t.optInt("medioPagoCodigo", 0).toString(), q(t.optString("timestamp", ""))
                ).joinToString(",") + "\n")
            }
            writer.close()
            Result.success(csvFile)
        } catch (e: Exception) { Result.failure(e) }
    }
}

fun effectiveAmount(total: String, tiempo: String): Double {
    val fromTotal = parseColombianAmount(total)
    if (fromTotal > 0.0) return fromTotal
    val lower = tiempo.lowercase()
    val numbers = Regex("\\d+").findAll(tiempo).map { it.value.toDouble() }.toList()
    if (numbers.isEmpty()) return 0.0
    val fromTiempo = when {
        lower.contains("minuto") && !lower.contains("hora") -> numbers[0] / 60.0 * 1000.0
        lower.contains("hora") && lower.contains("minuto") && numbers.size >= 2 -> (numbers[0] + numbers[1] / 60.0) * 1000.0
        else -> numbers[0] * 1000.0
    }
    return fromTiempo
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

fun Ticket.toDto(): TicketDto {
    // Resolve codes from names if code is 0 (for backward compat with old tickets)
    val vigIngCodigo = if (vigilanteIngresoCodigo != 0) vigilanteIngresoCodigo
        else com.parkingscanner.domain.model.CatalogoVigilantes.buscarPorNombre(vigilanteIngreso)?.codigo ?: 0
    val vigSalCodigo = if (vigilanteSalidaCodigo != 0) vigilanteSalidaCodigo
        else com.parkingscanner.domain.model.CatalogoVigilantes.buscarPorNombre(vigilanteSalida)?.codigo ?: 0
    val tipoCodigo = if (tipoVehiculoCodigo != 0) tipoVehiculoCodigo
        else com.parkingscanner.domain.model.CatalogoVehiculos.buscarPorTipo(tipoVehiculo)?.codigo ?: 0
    // Resolve names from codes (ensures names are always current with catalog)
    val vigIngNombre = com.parkingscanner.domain.model.CatalogoVigilantes.obtenerPorCodigo(vigIngCodigo)?.nombre ?: vigilanteIngreso
    val vigSalNombre = com.parkingscanner.domain.model.CatalogoVigilantes.obtenerPorCodigo(vigSalCodigo)?.nombre ?: vigilanteSalida
    val tipoNombre = com.parkingscanner.domain.model.CatalogoVehiculos.obtenerPorCodigo(tipoCodigo)?.tipo ?: tipoVehiculo
    return TicketDto(
        id = id, boleta = boleta, inmueble = inmueble,
        vigilanteIngreso = vigIngNombre, vigilanteIngresoCodigo = vigIngCodigo,
        vigilanteSalida = vigSalNombre, vigilanteSalidaCodigo = vigSalCodigo,
        placa = placa, tipoVehiculo = tipoNombre, tipoVehiculoCodigo = tipoCodigo,
        fechaEntrada = fechaEntrada, fechaSalida = fechaSalida,
        tiempo = tiempo, total = total, extractedText = extractedText,
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
        id = id, boleta = boleta, total = total,
        vigilanteIngreso = vigilanteIngreso, vigilanteSalida = vigilanteSalida,
        extractedText = extractedText, data = dataMap,
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(timestamp) ?: Date()
    )
}
