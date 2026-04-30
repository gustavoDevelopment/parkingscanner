package com.parkingscanner.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GlobalTicketIndex(context: Context) {

    private val file = File(File(context.filesDir, "ParkingScanner"), "global_index.json")
    private val fsIndex get() = FirebaseFirestore.getInstance().collection("globalIndex")

    // ── Local + Firestore write ───────────────────────────────────────────────

    fun addTicket(boleta: String, scannerName: String) {
        if (boleta.isBlank()) return
        val index = load()
        val arr = index.optJSONArray(boleta) ?: JSONArray()
        val existing = (0 until arr.length()).map { arr.getString(it) }
        if (scannerName !in existing) arr.put(scannerName)
        index.put(boleta, arr)
        file.writeText(index.toString())
        syncBoleta(boleta, arr)
    }

    fun removeTicket(boleta: String, scannerName: String) {
        if (boleta.isBlank()) return
        val index = load()
        val arr = index.optJSONArray(boleta) ?: return
        val remaining = (0 until arr.length()).map { arr.getString(it) }.filter { it != scannerName }
        if (remaining.isEmpty()) {
            index.remove(boleta)
            deleteBoleta(boleta)
        } else {
            val newArr = JSONArray().also { a -> remaining.forEach { a.put(it) } }
            index.put(boleta, newArr)
            syncBoleta(boleta, newArr)
        }
        file.writeText(index.toString())
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    fun load(): JSONObject {
        return if (file.exists()) try { JSONObject(file.readText()) } catch (_: Exception) { JSONObject() }
        else JSONObject()
    }

    // ── Rebuild from local JSON files + sync to Firestore ────────────────────

    fun rebuild() {
        val dir = file.parentFile ?: return
        val index = JSONObject()
        val jsonFiles = dir.listFiles { _, name ->
            name.endsWith(".json") && name != "tickets.json" && name != "catalogos.json"
                && name != "descriptions.json" && name != "global_index.json"
        } ?: return

        for (jsonFile in jsonFiles) {
            val scannerName = jsonFile.nameWithoutExtension
            try {
                val arr = JSONArray(jsonFile.readText())
                for (i in 0 until arr.length()) {
                    val boleta = arr.getJSONObject(i).optString("boleta", "").trim()
                    if (boleta.isBlank()) continue
                    val existing = index.optJSONArray(boleta) ?: JSONArray()
                    val names = (0 until existing.length()).map { existing.getString(it) }
                    if (scannerName !in names) existing.put(scannerName)
                    index.put(boleta, existing)
                }
            } catch (_: Exception) {}
        }
        file.writeText(index.toString())
        syncAllToFirestore(index)
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    data class GlobalStats(
        val totalTickets: Int,
        val missingConsecutive: List<Int>,
        val compensated: Map<String, List<String>>
    )

    fun computeStats(): GlobalStats {
        val index = load()
        val keys = index.keys()
        val allBoletas = mutableListOf<Int>()
        val compensated = mutableMapOf<String, List<String>>()
        while (keys.hasNext()) {
            val key = keys.next()
            key.toIntOrNull()?.let { allBoletas.add(it) }
            val arr = index.getJSONArray(key)
            if (arr.length() > 1) compensated[key] = (0 until arr.length()).map { arr.getString(it) }
        }
        val sorted = allBoletas.sorted()
        val missing = if (sorted.size >= 2) {
            val set = sorted.toHashSet()
            (sorted.first()..sorted.last()).filter { it !in set }
        } else emptyList()
        return GlobalStats(allBoletas.size, missing, compensated)
    }

    // ── Firestore helpers ─────────────────────────────────────────────────────

    private fun syncBoleta(boleta: String, scanners: JSONArray) {
        try {
            val list = (0 until scanners.length()).map { scanners.getString(it) }
            Tasks.await(fsIndex.document(boleta).set(
                mapOf("scanners" to list, "lastUpdated" to Timestamp.now()), SetOptions.merge()
            ))
        } catch (_: Exception) {}
    }

    private fun deleteBoleta(boleta: String) {
        try { Tasks.await(fsIndex.document(boleta).delete()) } catch (_: Exception) {}
    }

    private fun syncAllToFirestore(index: JSONObject) {
        try {
            val db = FirebaseFirestore.getInstance()
            var batch = db.batch()
            var count = 0
            val keys = index.keys()
            while (keys.hasNext()) {
                val boleta = keys.next()
                val arr = index.optJSONArray(boleta) ?: continue
                val list = (0 until arr.length()).map { arr.getString(it) }
                batch.set(fsIndex.document(boleta),
                    mapOf("scanners" to list, "lastUpdated" to Timestamp.now()), SetOptions.merge())
                if (++count == 500) { Tasks.await(batch.commit()); batch = db.batch(); count = 0 }
            }
            if (count > 0) Tasks.await(batch.commit())
        } catch (_: Exception) {}
    }
}
