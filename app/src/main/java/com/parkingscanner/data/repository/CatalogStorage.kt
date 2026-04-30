package com.parkingscanner.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.parkingscanner.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CatalogStorage(private val context: Context) {

    private val catalogFile = File(File(context.filesDir, "ParkingScanner"), "catalogos.json")

    private fun db() = FirebaseFirestore.getInstance()

    // ── Load ─────────────────────────────────────────────────────────────────

    fun load() {
        if (!catalogFile.exists()) {
            if (!loadFromFirestore()) save()
            return
        }
        try {
            parseAndApply(JSONObject(catalogFile.readText()))
        } catch (_: Exception) {}
    }

    // ── Save (local + Firestore) ──────────────────────────────────────────────

    fun save() {
        try {
            catalogFile.parentFile?.mkdirs()
            val json = buildJson()
            catalogFile.writeText(json.toString(2))
            syncToFirestore()
        } catch (_: Exception) {}
    }

    // ── Firestore sync ────────────────────────────────────────────────────────

    private fun syncToFirestore() {
        try {
            val db = db()
            var batch = db.batch()
            var count = 0

            fun flush() { if (count > 0) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 } }
            fun add(colName: String, docId: String, data: Map<String, Any>) {
                batch.set(db.collection("catalogs").document(colName).collection("items").document(docId), data, SetOptions.merge())
                if (++count == 500) flush()
            }

            CatalogoVigilantes.vigilantes.forEach { v ->
                add("vigilantes", v.codigo.toString(), mapOf("codigo" to v.codigo, "nombre" to v.nombre))
            }
            CatalogoVehiculos.vehiculos.forEach { v ->
                add("vehiculos", v.codigo.toString(), mapOf("codigo" to v.codigo, "tipo" to v.tipo))
            }
            CatalogoMediosPago.mediosPago.forEach { m ->
                add("mediosPago", m.codigo.toString(), mapOf("codigo" to m.codigo, "tipo" to m.tipo, "computa" to m.computa))
            }
            flush()
        } catch (_: Exception) {}
    }

    private fun loadFromFirestore(): Boolean {
        return try {
            val db = db()
            val vigilantesSnap = Tasks.await(db.collection("catalogs").document("vigilantes").collection("items").get())
            val vehiculosSnap  = Tasks.await(db.collection("catalogs").document("vehiculos").collection("items").get())
            val mediosSnap     = Tasks.await(db.collection("catalogs").document("mediosPago").collection("items").get())

            if (vigilantesSnap.isEmpty && vehiculosSnap.isEmpty && mediosSnap.isEmpty) return false

            if (!vigilantesSnap.isEmpty) {
                CatalogoVigilantes.vigilantes = vigilantesSnap.documents.mapNotNull { doc ->
                    val codigo = (doc.getLong("codigo") ?: return@mapNotNull null).toInt()
                    val nombre = doc.getString("nombre") ?: return@mapNotNull null
                    Vigilante(codigo, nombre)
                }.sortedBy { it.codigo }.toMutableList()
            }

            if (!vehiculosSnap.isEmpty) {
                CatalogoVehiculos.vehiculos = vehiculosSnap.documents.mapNotNull { doc ->
                    val codigo = (doc.getLong("codigo") ?: return@mapNotNull null).toInt()
                    val tipo = doc.getString("tipo") ?: return@mapNotNull null
                    TipoVehiculo(codigo, tipo)
                }.sortedBy { it.codigo }.toMutableList()
            }

            if (!mediosSnap.isEmpty) {
                CatalogoMediosPago.mediosPago = mediosSnap.documents.mapNotNull { doc ->
                    val codigo = (doc.getLong("codigo") ?: return@mapNotNull null).toInt()
                    val tipo = doc.getString("tipo") ?: return@mapNotNull null
                    val isQr = tipo.contains("qr", ignoreCase = true)
                    val computa = if (isQr) false else (doc.getBoolean("computa") ?: true)
                    MedioPago(codigo, tipo, computa)
                }.sortedBy { it.codigo }.toMutableList()
            }

            val json = buildJson()
            catalogFile.parentFile?.mkdirs()
            catalogFile.writeText(json.toString(2))
            true
        } catch (_: Exception) { false }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildJson(): JSONObject {
        val json = JSONObject()

        val vigilantesArr = JSONArray()
        CatalogoVigilantes.vigilantes.forEach { v ->
            vigilantesArr.put(JSONObject().apply { put("codigo", v.codigo); put("nombre", v.nombre) })
        }
        json.put("vigilantes", vigilantesArr)

        val vehiculosArr = JSONArray()
        CatalogoVehiculos.vehiculos.forEach { v ->
            vehiculosArr.put(JSONObject().apply { put("codigo", v.codigo); put("tipo", v.tipo) })
        }
        json.put("vehiculos", vehiculosArr)

        val mediosArr = JSONArray()
        CatalogoMediosPago.mediosPago.forEach { m ->
            mediosArr.put(JSONObject().apply { put("codigo", m.codigo); put("tipo", m.tipo); put("computa", m.computa) })
        }
        json.put("mediosPago", mediosArr)

        return json
    }

    private fun parseAndApply(json: JSONObject) {
        json.optJSONArray("vigilantes")?.let { arr ->
            CatalogoVigilantes.vigilantes = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Vigilante(obj.getInt("codigo"), obj.getString("nombre"))
            }.toMutableList()
        }
        json.optJSONArray("vehiculos")?.let { arr ->
            CatalogoVehiculos.vehiculos = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TipoVehiculo(obj.getInt("codigo"), obj.getString("tipo"))
            }.toMutableList()
        }
        json.optJSONArray("mediosPago")?.let { arr ->
            CatalogoMediosPago.mediosPago = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val tipo = obj.getString("tipo")
                val isQr = tipo.contains("qr", ignoreCase = true)
                MedioPago(obj.getInt("codigo"), tipo, if (isQr) false else obj.optBoolean("computa", true))
            }.toMutableList()
            save()
        }
    }
}
