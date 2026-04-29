package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class CloseScannerUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(scannerName: String) = repository.closeScanner(scannerName)
}
