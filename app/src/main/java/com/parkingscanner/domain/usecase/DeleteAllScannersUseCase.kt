package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class DeleteAllScannersUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.deleteAllScanners()
    }
}
