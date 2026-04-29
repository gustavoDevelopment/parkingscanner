package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class DeleteTicketUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(scannerName: String, ticketId: String): Result<Unit> {
        return repository.deleteTicket(scannerName, ticketId)
    }
}
