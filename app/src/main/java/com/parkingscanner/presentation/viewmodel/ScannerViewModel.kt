package com.parkingscanner.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkingscanner.domain.model.Scanner
import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.model.Ticket
import com.parkingscanner.domain.usecase.*
import kotlinx.coroutines.launch

data class ScannerUiState(
    val currentScanner: Scanner? = null,
    val scannerNames: List<String> = emptyList(),
    val scannerSummaries: List<ScannerSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ScannerViewModel(
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
    private val exportToCsvUseCase: ExportToCsvUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData(ScannerUiState())
    val uiState: LiveData<ScannerUiState> = _uiState

    private val _extractedText = MutableLiveData<String>()
    val extractedText: LiveData<String> = _extractedText

    private val _generatedJson = MutableLiveData<String>()
    val generatedJson: LiveData<String> = _generatedJson

    init {
        loadScannerList()
    }

    fun loadScannerList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            loadAllScannersUseCase()
                .onSuccess { names ->
                    _uiState.value = _uiState.value?.copy(
                        scannerNames = names,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(isLoading = false, error = error.message)
                }
            loadAllScannerSummariesUseCase()
                .onSuccess { summaries ->
                    _uiState.value = _uiState.value?.copy(scannerSummaries = summaries)
                }
                .onFailure { /* summaries are best-effort */ }
        }
    }

    fun createScanner(name: String, description: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            createScannerUseCase(name, description)
                .onSuccess {
                    loadScannerList()
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        currentScanner = Scanner(name)
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun loadScanner(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            loadScannerUseCase(name)
                .onSuccess { scanner ->
                    _uiState.value = _uiState.value?.copy(
                        currentScanner = scanner,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun addTicket(ticket: Ticket) {
        viewModelScope.launch {
            val scannerName = _uiState.value?.currentScanner?.name ?: return@launch
            _uiState.value = _uiState.value?.copy(isLoading = true)
            addTicketUseCase(scannerName, ticket)
                .onSuccess {
                    loadScanner(scannerName)
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun closeScanner() {
        viewModelScope.launch {
            val scannerName = _uiState.value?.currentScanner?.name ?: return@launch
            _uiState.value = _uiState.value?.copy(isLoading = true)
            closeScannerUseCase(scannerName)
                .onSuccess {
                    loadScannerList()
                    _uiState.value = _uiState.value?.copy(
                        currentScanner = null,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun deleteScanner(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            deleteScannerUseCase(name)
                .onSuccess {
                    loadScannerList()
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch {
            val scannerName = _uiState.value?.currentScanner?.name ?: return@launch
            _uiState.value = _uiState.value?.copy(isLoading = true)
            deleteTicketUseCase(scannerName, ticketId)
                .onSuccess {
                    loadScanner(scannerName)
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun updateTicket(ticket: Ticket) {
        viewModelScope.launch {
            val scannerName = _uiState.value?.currentScanner?.name ?: return@launch
            _uiState.value = _uiState.value?.copy(isLoading = true)
            updateTicketUseCase(scannerName, ticket)
                .onSuccess {
                    loadScanner(scannerName)
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun deleteAllScanners() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            deleteAllScannersUseCase()
                .onSuccess {
                    loadScannerList()
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun deleteAllTickets(scannerName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            deleteAllTicketsUseCase(scannerName)
                .onSuccess {
                    loadScanner(scannerName)
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun exportToCsv(scannerName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            exportToCsvUseCase(scannerName)
                .onSuccess { file ->
                    _uiState.value = _uiState.value?.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun setExtractedText(text: String) {
        _extractedText.value = text
    }

    fun setGeneratedJson(json: String) {
        _generatedJson.value = json
    }

    fun clearError() {
        _uiState.value = _uiState.value?.copy(error = null)
    }
}
