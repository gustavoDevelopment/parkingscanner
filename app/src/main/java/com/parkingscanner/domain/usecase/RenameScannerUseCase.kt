package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository

class RenameScannerUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke(oldName: String, newName: String): Result<Unit> =
        repository.renameScanner(oldName, newName)
}
