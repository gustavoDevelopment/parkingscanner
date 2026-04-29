package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class DeleteAllTicketsUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(scannerName: String): Result<Unit> {
        return repository.deleteAllTickets(scannerName)
    }
}
