package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.repository.ScannerRepository
import java.io.File

class ExportToCsvUseCase(
    private val repository: ScannerRepository
) {
    suspend operator fun invoke(scannerName: String): Result<File> {
        return repository.exportToCsv(scannerName)
    }
}
