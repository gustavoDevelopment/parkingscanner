package com.parkingscanner.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GlobalTicketIndex(context: Context) {
    private val file = File(File(context.filesDir, "ParkingScanner"), "global_index.json")

    fun addTicket(boleta: String, scannerName: String) {
        if (boleta.isBlank()) return
        val index = load()
        val arr = index.optJSONArray(boleta) ?: JSONArray()
        val existing = (0 until arr.length()).map { arr.getString(it) }
        if (scannerName !in existing) arr.put(scannerName)
        index.put(boleta, arr)
        file.writeText(index.toString())
    }

    fun removeTicket(boleta: String, scannerName: String) {
        if (boleta.isBlank()) return
        val index = load()
        val arr = index.optJSONArray(boleta) ?: return
        val remaining = (0 until arr.length()).map { arr.getString(it) }.filter { it != scannerName }
        if (remaining.isEmpty()) index.remove(boleta) else {
            val newArr = JSONArray()
            remaining.forEach { newArr.put(it) }
            index.put(boleta, newArr)
        }
        file.writeText(index.toString())
    }

    fun load(): JSONObject {
        return if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }

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
    }

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
            val num = key.toIntOrNull()
            if (num != null) allBoletas.add(num)
            val arr = index.getJSONArray(key)
            if (arr.length() > 1) {
                compensated[key] = (0 until arr.length()).map { arr.getString(it) }
            }
        }

        val sorted = allBoletas.sorted()
        val missing = if (sorted.size >= 2) {
            val min = sorted.first()
            val max = sorted.last()
            val set = sorted.toHashSet()
            (min..max).filter { it !in set }
        } else emptyList()

        return GlobalStats(allBoletas.size, missing, compensated)
    }
}
