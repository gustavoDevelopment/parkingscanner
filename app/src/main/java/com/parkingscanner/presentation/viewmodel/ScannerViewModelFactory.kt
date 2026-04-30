package com.parkingscanner.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parkingscanner.domain.usecase.*

class ScannerViewModelFactory(
    private val createScannerUseCase: CreateScannerUseCase,
    private val loadScannerUseCase: LoadScannerUseCase,
    private val loadAllScannersUseCase: LoadAllScannersUseCase,
    private val loadAllScannerSummariesUseCase: LoadAllScannerSummariesUseCase,
    private val addTicketUseCase: AddTicketUseCase,
    private val deleteTicketUseCase: DeleteTicketUseCase,
    private val updateTicketUseCase: UpdateTicketUseCase,
    private val deleteAllTicketsUseCase: DeleteAllTicketsUseCase,
    private val deleteAllScannersUseCase: DeleteAllScannersUseCase,
    private val closeScannerUseCase: CloseScannerUseCase,
    private val deleteScannerUseCase: DeleteScannerUseCase,
    private val exportToCsvUseCase: ExportToCsvUseCase,
    private val renameScannerUseCase: RenameScannerUseCase,
    private val updateDescriptionUseCase: UpdateDescriptionUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScannerViewModel(
                createScannerUseCase,
                loadScannerUseCase,
                loadAllScannersUseCase,
                loadAllScannerSummariesUseCase,
                addTicketUseCase,
                deleteTicketUseCase,
                updateTicketUseCase,
                deleteAllTicketsUseCase,
                deleteAllScannersUseCase,
                closeScannerUseCase,
                deleteScannerUseCase,
                exportToCsvUseCase,
                renameScannerUseCase,
                updateDescriptionUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
