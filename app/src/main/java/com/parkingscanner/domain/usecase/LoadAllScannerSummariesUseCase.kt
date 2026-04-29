package com.parkingscanner.domain.usecase

import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.repository.ScannerRepository

class LoadAllScannerSummariesUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke(): Result<List<ScannerSummary>> = repository.loadAllScannerSummaries()
}
