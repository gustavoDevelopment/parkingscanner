package com.parkingscanner.data.repository

import android.content.Context
import com.parkingscanner.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CatalogStorage(context: Context) {

    private val catalogFile = File(File(context.filesDir, "ParkingScanner"), "catalogos.json")

    fun load() {
        if (!catalogFile.exists()) {
            save()
            return
        }
        try {
            val json = JSONObject(catalogFile.readText())

            json.optJSONArray("vigilantes")?.let { arr ->
                val list = mutableListOf<Vigilante>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Vigilante(obj.getInt("codigo"), obj.getString("nombre")))
                }
                CatalogoVigilantes.vigilantes = list
            }

            json.optJSONArray("vehiculos")?.let { arr ->
                val list = mutableListOf<TipoVehiculo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(TipoVehiculo(obj.getInt("codigo"), obj.getString("tipo")))
                }
                CatalogoVehiculos.vehiculos = list
            }

            json.optJSONArray("mediosPago")?.let { arr ->
                val list = mutableListOf<MedioPago>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tipo = obj.getString("tipo")
                    val isQr = tipo.contains("qr", ignoreCase = true)
                    val computa = if (isQr) false else obj.optBoolean("computa", true)
                    list.add(MedioPago(obj.getInt("codigo"), tipo, computa))
                }
                CatalogoMediosPago.mediosPago = list
                save()
            }
        } catch (e: Exception) {
            // Keep defaults on parse error
        }
    }

    fun save() {
        try {
            catalogFile.parentFile?.mkdirs()
            val json = JSONObject()

            val vigilantesArr = JSONArray()
            CatalogoVigilantes.vigilantes.forEach { v ->
                vigilantesArr.put(JSONObject().apply {
                    put("codigo", v.codigo)
                    put("nombre", v.nombre)
                })
            }
            json.put("vigilantes", vigilantesArr)

            val vehiculosArr = JSONArray()
            CatalogoVehiculos.vehiculos.forEach { v ->
                vehiculosArr.put(JSONObject().apply {
                    put("codigo", v.codigo)
                    put("tipo", v.tipo)
                })
            }
            json.put("vehiculos", vehiculosArr)

            val mediosArr = JSONArray()
            CatalogoMediosPago.mediosPago.forEach { m ->
                mediosArr.put(JSONObject().apply {
                    put("codigo", m.codigo)
                    put("tipo", m.tipo)
                    put("computa", m.computa)
                })
            }
            json.put("mediosPago", mediosArr)

            catalogFile.writeText(json.toString(2))
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
}
