package com.parkingscanner.data.model

import org.json.JSONObject
import java.util.Date

data class TicketDto(
    val id: String = "",
    val boleta: String = "",
    val inmueble: String = "",
    val vigilanteIngreso: String = "",
    val vigilanteSalida: String = "",
    val placa: String = "",
    val tipoVehiculo: String = "",
    val fechaEntrada: String = "",
    val fechaSalida: String = "",
    val tiempo: String = "",
    val total: String = "",
    val extractedText: String,
    val data: JSONObject,
    val medioPagoCodigo: Int = 0,
    val timestamp: String
) {
    companion object {
        fun fromJson(json: JSONObject): TicketDto {
            return TicketDto(
                id = json.optString("id", ""),
                boleta = json.optString("boleta", ""),
                inmueble = json.optString("inmueble", ""),
                vigilanteIngreso = json.optString("vigilanteIngreso", ""),
                vigilanteSalida = json.optString("vigilanteSalida", ""),
                placa = json.optString("placa", ""),
                tipoVehiculo = json.optString("tipoVehiculo", ""),
                fechaEntrada = json.optString("fechaEntrada", ""),
                fechaSalida = json.optString("fechaSalida", ""),
                tiempo = json.optString("tiempo", ""),
                total = json.optString("total", ""),
                extractedText = json.getString("extractedText"),
                data = json.optJSONObject("data") ?: JSONObject(),
                medioPagoCodigo = json.optInt("medioPagoCodigo", 0),
                timestamp = json.getString("timestamp")
            )
        }
        
        fun toJson(ticketDto: TicketDto): JSONObject {
            val json = JSONObject()
            json.put("id", ticketDto.id)
            json.put("boleta", ticketDto.boleta)
            json.put("inmueble", ticketDto.inmueble)
            json.put("vigilanteIngreso", ticketDto.vigilanteIngreso)
            json.put("vigilanteSalida", ticketDto.vigilanteSalida)
            json.put("placa", ticketDto.placa)
            json.put("tipoVehiculo", ticketDto.tipoVehiculo)
            json.put("fechaEntrada", ticketDto.fechaEntrada)
            json.put("fechaSalida", ticketDto.fechaSalida)
            json.put("tiempo", ticketDto.tiempo)
            json.put("total", ticketDto.total)
            json.put("extractedText", ticketDto.extractedText)
            json.put("data", ticketDto.data)
            json.put("medioPagoCodigo", ticketDto.medioPagoCodigo)
            json.put("timestamp", ticketDto.timestamp)
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
        
        // Generar ID si está vacío
        val ticketId = if (id.isEmpty()) {
            "ticket_${timestamp}_${boleta}"
        } else {
            id
        }
        
        return com.parkingscanner.domain.model.Ticket(
            id = ticketId,
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
            data = dataMap,
            medioPagoCodigo = medioPagoCodigo,
            timestamp = try {
                Date(timestamp.toLong())
            } catch (e: Exception) {
                Date()
            }
        )
    }
}
