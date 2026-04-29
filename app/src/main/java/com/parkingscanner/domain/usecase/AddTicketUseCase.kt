package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.model.Ticket
import com.parkingscanner.domain.repository.ScannerRepository

class AddTicketUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(scannerName: String, ticket: Ticket) = repository.addTicket(scannerName, ticket)
}
