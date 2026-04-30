package com.parkingscanner.data.model

import org.json.JSONObject
import java.util.Date

data class TicketDto(
    val id: String = "",
    val boleta: String = "",
    val inmueble: String = "",
    val vigilanteIngreso: String = "",
    val vigilanteIngresoCodigo: Int = 0,
    val vigilanteSalida: String = "",
    val vigilanteSalidaCodigo: Int = 0,
    val placa: String = "",
    val tipoVehiculo: String = "",
    val tipoVehiculoCodigo: Int = 0,
    val fechaEntrada: String = "",
    val fechaSalida: String = "",
    val tiempo: String = "",
    val total: String = "",
    val extractedText: String,
    val data: JSONObject,
    val medioPagoCodigo: Int = 0,
    val imagePath: String = "",
    val imageUrl: String = "",
    val timestamp: String
) {
    companion object {
        fun fromJson(json: JSONObject): TicketDto {
            return TicketDto(
                id = json.optString("id", ""),
                boleta = json.optString("boleta", ""),
                inmueble = json.optString("inmueble", ""),
                vigilanteIngreso = json.optString("vigilanteIngreso", ""),
                vigilanteIngresoCodigo = json.optInt("vigilanteIngresoCodigo", 0),
                vigilanteSalida = json.optString("vigilanteSalida", ""),
                vigilanteSalidaCodigo = json.optInt("vigilanteSalidaCodigo", 0),
                placa = json.optString("placa", ""),
                tipoVehiculo = json.optString("tipoVehiculo", ""),
                tipoVehiculoCodigo = json.optInt("tipoVehiculoCodigo", 0),
                fechaEntrada = json.optString("fechaEntrada", ""),
                fechaSalida = json.optString("fechaSalida", ""),
                tiempo = json.optString("tiempo", ""),
                total = json.optString("total", ""),
                extractedText = json.optString("extractedText", ""),
                data = json.optJSONObject("data") ?: JSONObject(),
                medioPagoCodigo = json.optInt("medioPagoCodigo", 0),
                imagePath = json.optString("imagePath", ""),
                imageUrl = json.optString("imageUrl", ""),
                timestamp = json.optString("timestamp", System.currentTimeMillis().toString())
            )
        }

        fun toJson(dto: TicketDto): JSONObject {
            val json = JSONObject()
            json.put("id", dto.id)
            json.put("boleta", dto.boleta)
            json.put("inmueble", dto.inmueble)
            json.put("vigilanteIngreso", dto.vigilanteIngreso)
            json.put("vigilanteIngresoCodigo", dto.vigilanteIngresoCodigo)
            json.put("vigilanteSalida", dto.vigilanteSalida)
            json.put("vigilanteSalidaCodigo", dto.vigilanteSalidaCodigo)
            json.put("placa", dto.placa)
            json.put("tipoVehiculo", dto.tipoVehiculo)
            json.put("tipoVehiculoCodigo", dto.tipoVehiculoCodigo)
            json.put("fechaEntrada", dto.fechaEntrada)
            json.put("fechaSalida", dto.fechaSalida)
            json.put("tiempo", dto.tiempo)
            json.put("total", dto.total)
            json.put("extractedText", dto.extractedText)
            json.put("data", dto.data)
            json.put("medioPagoCodigo", dto.medioPagoCodigo)
            json.put("imagePath", dto.imagePath)
            json.put("imageUrl", dto.imageUrl)
            json.put("timestamp", dto.timestamp)
            return json
        }
    }

    fun toDomain(): com.parkingscanner.domain.model.Ticket {
        val dataMap = mutableMapOf<String, String>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            dataMap[key] = data.optString(key, "")
        }
        val ticketId = id.ifEmpty { "ticket_${timestamp}_${boleta}" }
        return com.parkingscanner.domain.model.Ticket(
            id = ticketId,
            boleta = boleta,
            inmueble = inmueble,
            vigilanteIngreso = vigilanteIngreso,
            vigilanteIngresoCodigo = vigilanteIngresoCodigo,
            vigilanteSalida = vigilanteSalida,
            vigilanteSalidaCodigo = vigilanteSalidaCodigo,
            placa = placa,
            tipoVehiculo = tipoVehiculo,
            tipoVehiculoCodigo = tipoVehiculoCodigo,
            fechaEntrada = fechaEntrada,
            fechaSalida = fechaSalida,
            tiempo = tiempo,
            total = total,
            extractedText = extractedText,
            data = dataMap,
            medioPagoCodigo = medioPagoCodigo,
            imagePath = imagePath,
            imageUrl = imageUrl,
            timestamp = try { Date(timestamp.toLong()) } catch (_: Exception) { Date() }
        )
    }
}
