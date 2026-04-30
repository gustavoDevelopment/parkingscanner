package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class UpdateDescriptionUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke(name: String, description: String): Result<Unit> =
        repository.updateDescription(name, description)
}
