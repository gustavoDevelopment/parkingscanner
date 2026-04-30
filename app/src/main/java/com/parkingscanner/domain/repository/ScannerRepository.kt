package com.parkingscanner.domain.repository

import com.parkingscanner.domain.model.Scanner
import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.model.Ticket
import java.io.File

interface ScannerRepository {
    suspend fun createScanner(name: String, description: String = ""): Result<Unit>
    suspend fun loadScanner(name: String): Result<Scanner>
    suspend fun loadAllScannerSummaries(): Result<List<ScannerSummary>>
    suspend fun loadAllScanners(): Result<List<String>>
    suspend fun addTicket(scannerName: String, ticket: Ticket): Result<Unit>
    suspend fun deleteTicket(scannerName: String, ticketId: String): Result<Unit>
    suspend fun updateTicket(scannerName: String, ticket: Ticket): Result<Unit>
    suspend fun deleteAllTickets(scannerName: String): Result<Unit>
    suspend fun closeScanner(scannerName: String): Result<Unit>
    suspend fun deleteScanner(name: String): Result<Unit>
    suspend fun renameScanner(oldName: String, newName: String): Result<Unit>
    suspend fun updateDescription(name: String, description: String): Result<Unit>
    suspend fun deleteAllScanners(): Result<Unit>
    suspend fun exportToTxt(scannerName: String): Result<File>
    suspend fun exportToCsv(scannerName: String): Result<File>
}
