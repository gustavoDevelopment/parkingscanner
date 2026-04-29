package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class LoadScannerUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(name: String) = repository.loadScanner(name)
}
