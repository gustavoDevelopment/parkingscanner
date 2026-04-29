package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class LoadAllScannersUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke() = repository.loadAllScanners()
}
