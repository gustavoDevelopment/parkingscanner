package com.parkingscanner.domain.model

import java.util.Date

data class Ticket(
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
    val data: Map<String, String> = emptyMap(),
    val medioPagoCodigo: Int = 0,
    val timestamp: Date = Date()
)
