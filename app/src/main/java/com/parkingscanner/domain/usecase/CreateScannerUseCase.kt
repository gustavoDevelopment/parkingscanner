package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class CreateScannerUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(name: String, description: String = "") = repository.createScanner(name, description)
}
