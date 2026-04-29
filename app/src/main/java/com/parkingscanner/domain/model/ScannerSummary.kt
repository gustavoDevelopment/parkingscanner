package com.parkingscanner.domain.model

data class ScannerSummary(
    val name: String,
    val ticketCount: Int,
    val computaTotal: Double,
    val description: String = ""
)
