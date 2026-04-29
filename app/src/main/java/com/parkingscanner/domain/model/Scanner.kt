package com.parkingscanner.domain.model

import java.util.Date

data class Scanner(
    val name: String,
    val tickets: List<Ticket> = emptyList(),
    val createdAt: Date = Date(),
    val isClosed: Boolean = false
)
