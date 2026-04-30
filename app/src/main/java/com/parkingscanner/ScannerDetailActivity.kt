package com.parkingscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.parkingscanner.ui.PieChartView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.parkingscanner.data.repository.ScannerRepositoryImpl
import com.parkingscanner.domain.model.MedioPago
import com.parkingscanner.domain.model.CatalogoMediosPago
import com.parkingscanner.data.service.CameraService
import com.parkingscanner.data.service.OcrService
import com.parkingscanner.domain.model.Ticket
import com.parkingscanner.domain.usecase.*
import com.parkingscanner.presentation.viewmodel.ScannerViewModel
import com.parkingscanner.presentation.viewmodel.ScannerViewModelFactory
import com.parkingscanner.data.repository.GlobalTicketIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ScannerDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: ScannerViewModel
    private lateinit var cameraService: CameraService
    private lateinit var ocrService: OcrService

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var captureButton: Button
    private lateinit var cancelCameraButton: Button
    private lateinit var retakeButton: Button
    private lateinit var addButton: Button
    private lateinit var fabDeleteAllTickets: FloatingActionButton
    private lateinit var closeScannerButton: Button
    private lateinit var shareFilesButton: Button
    private lateinit var fabAddTicket: FloatingActionButton
    private lateinit var ticketsListView: ListView
    private lateinit var previewDataText: TextView
    private lateinit var scannerNameText: TextView
    
    private lateinit var statsBoletasCount: TextView
    private lateinit var statsTotal: TextView
    private lateinit var pieChart: PieChartView
    private lateinit var legendEfectivo: TextView
    private lateinit var legendQr: TextView
    private lateinit var legendAnulado: TextView
    private lateinit var statsFaltantes: TextView
    private lateinit var btnCorregirTotales: TextView

    private lateinit var cameraStatusText: TextView
    private lateinit var ticketsTitleView: TextView

    private var currentTickets: List<Ticket> = emptyList()
    private var currentImageBitmap: Bitmap? = null
    private var extractedText = ""
    private var generatedJson = ""
    private var selectedMedioPago: MedioPago? = null

    private var isCameraActive = false
    private var lastAnalysisMs = 0L
    private var detectionStreak = 0
    private var autoCapturePending = false
    private val frameRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                startCamera()
            }
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner_detail)

        val scannerName = intent.getStringExtra("SCANNER_NAME") ?: return

        // Manual dependency injection
        val repository = ScannerRepositoryImpl(this)
        cameraService = CameraService(this)
        ocrService = OcrService()
        
        val createScannerUseCase = CreateScannerUseCase(repository)
        val loadScannerUseCase = LoadScannerUseCase(repository)
        val loadAllScannersUseCase = LoadAllScannersUseCase(repository)
        val addTicketUseCase = AddTicketUseCase(repository)
        val deleteTicketUseCase = DeleteTicketUseCase(repository)
        val updateTicketUseCase = UpdateTicketUseCase(repository)
        val deleteAllTicketsUseCase = DeleteAllTicketsUseCase(repository)
        val deleteAllScannersUseCase = DeleteAllScannersUseCase(repository)
        val closeScannerUseCase = CloseScannerUseCase(repository)
        val deleteScannerUseCase = DeleteScannerUseCase(repository)
        val exportToCsvUseCase = ExportToCsvUseCase(repository)
        
        val loadAllScannerSummariesUseCase = com.parkingscanner.domain.usecase.LoadAllScannerSummariesUseCase(repository)

        val factory = ScannerViewModelFactory(
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
            RenameScannerUseCase(repository)
        )
        viewModel = ViewModelProvider(this, factory)[ScannerViewModel::class.java]

        previewView = findViewById(R.id.previewView)
        capturedImageView = findViewById(R.id.capturedImageView)
        captureButton = findViewById(R.id.captureButton)
        cancelCameraButton = findViewById(R.id.cancelCameraButton)
        retakeButton = findViewById(R.id.retakeButton)
        addButton = findViewById(R.id.addButton)
        fabDeleteAllTickets = findViewById(R.id.fabDeleteAllTickets)
        closeScannerButton = findViewById(R.id.closeScannerButton)
        shareFilesButton = findViewById(R.id.shareFilesButton)
        fabAddTicket = findViewById(R.id.fabAddTicket)
        ticketsListView = findViewById(R.id.ticketsListView)
        cameraStatusText = findViewById(R.id.cameraStatusText)
        ticketsTitleView = findViewById(R.id.ticketsTitleView)
        previewDataText = findViewById(R.id.previewDataText)
        scannerNameText = findViewById(R.id.scannerNameText)
        statsBoletasCount = findViewById(R.id.statsBoletasCount)
        statsTotal = findViewById(R.id.statsTotal)
        pieChart = findViewById(R.id.pieChart)
        legendEfectivo = findViewById(R.id.legendEfectivo)
        legendQr = findViewById(R.id.legendQr)
        legendAnulado = findViewById(R.id.legendAnulado)
        statsFaltantes = findViewById(R.id.statsFaltantes)
        btnCorregirTotales = findViewById(R.id.btnCorregirTotales)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCameraActive) cancelCamera() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        scannerNameText.text = scannerName
        Log.d("ScannerDetailActivity", "Scanner name set to: $scannerName")
        Log.d("ScannerDetailActivity", "ScannerNameText visibility: ${scannerNameText.visibility}")

        setupObservers()
        setupListeners()
        
        // Cargar el scanner
        viewModel.loadScanner(scannerName)
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this, Observer { state ->
            state.currentScanner?.let { scanner ->
                updateTicketsList(scanner.tickets)
            }
            state.isLoading?.let { isLoading ->
                if (isLoading) {
                    Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                }
            }
            state.error?.let { error ->
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })

        viewModel.extractedText.observe(this, Observer { text ->
            extractedText = text
            updatePreviewData()
        })

        viewModel.generatedJson.observe(this, Observer { json ->
            generatedJson = json
            updatePreviewData()
        })
    }

    private fun setupListeners() {
        fabAddTicket.setOnClickListener {
            checkCameraPermission()
        }

        captureButton.setOnClickListener {
            capturePhoto()
        }

        cancelCameraButton.setOnClickListener {
            cancelCamera()
        }

        retakeButton.setOnClickListener {
            retakePhoto()
        }

        addButton.setOnClickListener {
            showMedioPagoDialog()
        }

        fabDeleteAllTickets.setOnClickListener {
            showDeleteAllTicketsDialog()
        }

        closeScannerButton.setOnClickListener {
            showCloseScannerDialog()
        }

        shareFilesButton.setOnClickListener {
            shareExportedFiles(scannerNameText.text.toString())
        }
    }

    private fun updateTicketsList(tickets: List<Ticket>) {
        currentTickets = tickets.sortedWith(compareBy { it.boleta.trim().toIntOrNull() ?: Int.MAX_VALUE })
        val adapter = TicketAdapter(
            context = this,
            tickets = currentTickets,
            onDeleteClick = { ticketId ->
                showDeleteTicketDialog(ticketId)
            },
            onItemClick = { ticket ->
                showTicketDetailDialog(ticket)
            }
        )
        ticketsListView.adapter = adapter
        updateStats(currentTickets)
    }

    private fun updateStats(tickets: List<Ticket>) {
        var countEfectivo = 0
        var countQr = 0
        var countAnulado = 0
        var total = 0.0
        var totalEfectivo = 0.0
        var totalQr = 0.0
        var totalAnulado = 0.0

        tickets.forEach { ticket ->
            val medioPago = CatalogoMediosPago.obtenerPorCodigo(ticket.medioPagoCodigo)
            val amt = effectiveAmount(ticket.total, ticket.tiempo)

            // Check tipo name FIRST so QR is never misclassified by computa flag
            when {
                medioPago == null -> { countEfectivo++; totalEfectivo += amt }
                medioPago.tipo.contains("qr", ignoreCase = true) -> { countQr++; totalQr += amt }
                !medioPago.computa || medioPago.tipo.contains("anulado", ignoreCase = true) -> { countAnulado++; totalAnulado += amt }
                else -> { countEfectivo++; totalEfectivo += amt }
            }

            if (medioPago == null || medioPago.computa) {
                total += amt
            }
        }

        val copFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
        statsBoletasCount.text = "${tickets.size} boletas"
        statsTotal.text = "Total: $${copFormat.format(total)}"
        legendEfectivo.text = if (totalEfectivo > 0) "● Efectivo: $countEfectivo ($${copFormat.format(totalEfectivo)})" else "● Efectivo: $countEfectivo"
        legendQr.text = if (totalQr > 0) "● QR: $countQr ($${copFormat.format(totalQr)})" else "● QR: $countQr"
        legendAnulado.text = if (totalAnulado > 0) "● Anulado: $countAnulado ($${copFormat.format(totalAnulado)})" else "● Anulado: $countAnulado"
        legendAnulado.setTextColor(if (countAnulado > 0) android.graphics.Color.parseColor("#FF6D00") else android.graphics.Color.parseColor("#AAAAAA"))
        pieChart.setData(countEfectivo, countQr, countAnulado)

        // Show verify button only when there are enough tickets to check consecutives
        val boletas = tickets.mapNotNull { it.boleta.trim().toIntOrNull() }
        if (boletas.size >= 2) {
            statsFaltantes.visibility = View.VISIBLE
            statsFaltantes.setOnClickListener { showMissingBoletasDialog() }
        } else {
            statsFaltantes.visibility = View.GONE
        }

        // Show fix-totals button if any ticket has total > tiempo-based amount
        val corregibles = tickets.count { t ->
            val tiempoAmt = parseTiempoToAmount(t.tiempo)
            val totalAmt = parseColombianAmount(t.total)
            tiempoAmt > 0 && totalAmt > tiempoAmt
        }
        if (corregibles > 0) {
            btnCorregirTotales.text = "⚡ Corregir $corregibles total(es) por tiempo"
            btnCorregirTotales.visibility = View.VISIBLE
            btnCorregirTotales.setOnClickListener { corregirTotalesPorTiempo() }
        } else {
            btnCorregirTotales.visibility = View.GONE
        }
    }

    private fun corregirTotalesPorTiempo() {
        val toFix = currentTickets.filter { t ->
            val tiempoAmt = parseTiempoToAmount(t.tiempo)
            val totalAmt = parseColombianAmount(t.total)
            tiempoAmt > 0 && totalAmt > tiempoAmt
        }
        if (toFix.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Corregir totales")
            .setMessage("Se corregirán ${toFix.size} ticket(s) cuyo total supera el valor por tiempo.\n\n¿Continuar?")
            .setPositiveButton("Corregir") { _, _ ->
                val corrected = toFix.map { ticket ->
                    ticket.copy(total = parseTiempoToAmount(ticket.tiempo).toLong().toString())
                }
                viewModel.updateTicketsBulk(corrected)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private data class BoletaAnalysis(
        val verdaderamenteFaltantes: List<Int>,
        val enOtraFecha: List<Int>,
        val boletasExcluidas: List<Int>,
        val rangeStart: Int,
        val rangeEnd: Int
    )

    private fun analizarConsecutivos(tickets: List<Ticket>, globalIndex: JSONObject, currentScanner: String): BoletaAnalysis {
        val todas = tickets.mapNotNull { it.boleta.trim().toIntOrNull() }.sorted()
        if (todas.size < 2) return BoletaAnalysis(emptyList(), emptyList(), emptyList(), 0, 0)
        val todasSet = todas.toHashSet()

        // Buscar el primer bloque DENSO: 3+ boletas consecutivas con gap ≤ 1 entre ellas.
        // Antes de ese bloque los boletas son "de transición" (días anteriores) y no se analizan.
        val minRun = 3
        var clusterStart = todas.first()
        if (todas.size >= minRun) {
            outer@ for (i in 0..todas.size - minRun) {
                for (j in i until i + minRun - 1) {
                    if (todas[j + 1] - todas[j] > 1) continue@outer
                }
                clusterStart = todas[i]
                break
            }
        }

        val end = todas.last()
        val excluidas = todas.filter { it < clusterStart }

        // Huecos dentro del rango denso
        val huecos = (clusterStart..end).filter { it !in todasSet }

        // Clasificar cada hueco contra el índice global
        val verdaderamenteFaltantes = huecos.filter { globalIndex.optJSONArray(it.toString()) == null }
        val enOtraFecha = huecos.filter { b ->
            val arr = globalIndex.optJSONArray(b.toString())
            arr != null && (0 until arr.length()).map { arr.getString(it) }.any { it != currentScanner }
        }

        return BoletaAnalysis(verdaderamenteFaltantes, enOtraFecha, excluidas, clusterStart, end)
    }

    private fun showMissingBoletasDialog() {
        val globalIndex = GlobalTicketIndex(this).load()
        val currentScanner = scannerNameText.text.toString()
        val analysis = analizarConsecutivos(currentTickets, globalIndex, currentScanner)

        val totalProblemas = analysis.verdaderamenteFaltantes.size + analysis.enOtraFecha.size
        if (totalProblemas == 0 && analysis.boletasExcluidas.isEmpty()) {
            Toast.makeText(this, "Consecutivos completos en este rango", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("Rango analizado: ${analysis.rangeStart} → ${analysis.rangeEnd}\n")
        if (analysis.boletasExcluidas.isNotEmpty()) {
            sb.append("Excluidos (otra fecha): ${analysis.boletasExcluidas.joinToString(", ")}\n")
        }
        sb.append("\n")

        if (analysis.verdaderamenteFaltantes.isNotEmpty()) {
            sb.append("⬜ Sin capturar en ningún lado (${analysis.verdaderamenteFaltantes.size}):\n")
            analysis.verdaderamenteFaltantes.forEach { sb.append("  $it\n") }
            sb.append("\n")
        }
        if (analysis.enOtraFecha.isNotEmpty()) {
            sb.append("✅ Registrados en otra fecha (${analysis.enOtraFecha.size}):\n")
            analysis.enOtraFecha.forEach { b ->
                val arr = globalIndex.optJSONArray(b.toString())
                val fechas = if (arr != null) (0 until arr.length()).map { arr.getString(it) }
                    .filter { it != currentScanner }.joinToString(", ") else ""
                sb.append("  $b → $fechas\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Consecutivos: ${analysis.verdaderamenteFaltantes.size} faltantes, ${analysis.enOtraFecha.size} en otra fecha")
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("Cerrar", null)
            .show()
    }
    
    private fun showTicketDetailDialog(ticket: Ticket) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // Image (local file first, then URL fallback)
        val localFile = ticket.imagePath.takeIf { it.isNotEmpty() }?.let { File(it) }
        if (localFile?.exists() == true) {
            val imageView = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 600
                ).also { it.bottomMargin = 24 }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(android.graphics.BitmapFactory.decodeFile(localFile.absolutePath))
            }
            layout.addView(imageView)
        }

        val detailText = StringBuilder()
        if (ticket.boleta.isNotEmpty()) detailText.append("Boleta: ${ticket.boleta}\n")
        if (ticket.inmueble.isNotEmpty()) detailText.append("Inmueble: ${ticket.inmueble}\n")
        if (ticket.vigilanteIngreso.isNotEmpty()) detailText.append("Vigilante Ingreso: ${ticket.vigilanteIngreso}\n")
        if (ticket.vigilanteSalida.isNotEmpty()) detailText.append("Vigilante Salida: ${ticket.vigilanteSalida}\n")
        if (ticket.placa.isNotEmpty()) detailText.append("Placa: ${ticket.placa}\n")
        if (ticket.tipoVehiculo.isNotEmpty()) detailText.append("Tipo Vehículo: ${ticket.tipoVehiculo}\n")
        if (ticket.fechaEntrada.isNotEmpty()) detailText.append("Fecha Entrada: ${ticket.fechaEntrada}\n")
        if (ticket.fechaSalida.isNotEmpty()) detailText.append("Fecha Salida: ${ticket.fechaSalida}\n")
        if (ticket.tiempo.isNotEmpty()) detailText.append("Tiempo: ${ticket.tiempo}\n")
        if (ticket.total.isNotEmpty()) detailText.append("Total a Pagar: ${ticket.total}\n")

        layout.addView(android.widget.TextView(this).apply { text = detailText.toString() })

        AlertDialog.Builder(this)
            .setTitle("Detalle del Ticket")
            .setView(layout)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Editar") { _, _ -> showEditTicketDialog(ticket) }
            .show()
    }
    
    private fun showEditTicketDialog(ticket: Ticket) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_ticket, null)

        val boletaInput = dialogView.findViewById<EditText>(R.id.editBoleta)
        val inmuebleInput = dialogView.findViewById<EditText>(R.id.editInmueble)
        val vigilanteIngresoInput = dialogView.findViewById<EditText>(R.id.editVigilanteIngreso)
        val vigilanteSalidaInput = dialogView.findViewById<EditText>(R.id.editVigilanteSalida)
        val placaInput = dialogView.findViewById<EditText>(R.id.editPlaca)
        val tipoVehiculoInput = dialogView.findViewById<EditText>(R.id.editTipoVehiculo)
        val fechaEntradaInput = dialogView.findViewById<EditText>(R.id.editFechaEntrada)
        val fechaSalidaInput = dialogView.findViewById<EditText>(R.id.editFechaSalida)
        val tiempoInput = dialogView.findViewById<EditText>(R.id.editTiempo)
        val totalInput = dialogView.findViewById<EditText>(R.id.editTotal)
        val spinnerMedioPago = dialogView.findViewById<Spinner>(R.id.spinnerMedioPago)

        boletaInput.setText(ticket.boleta)
        inmuebleInput.setText(ticket.inmueble)
        vigilanteIngresoInput.setText(ticket.vigilanteIngreso)
        vigilanteSalidaInput.setText(ticket.vigilanteSalida)
        placaInput.setText(ticket.placa)
        tipoVehiculoInput.setText(ticket.tipoVehiculo)
        fechaEntradaInput.setText(ticket.fechaEntrada)
        fechaSalidaInput.setText(ticket.fechaSalida)
        tiempoInput.setText(ticket.tiempo)
        totalInput.setText(ticket.total)

        val mediosPago = CatalogoMediosPago.mediosPago
        val opcionesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            mediosPago.map { "${it.codigo}. ${it.tipo}" })
        opcionesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMedioPago.adapter = opcionesAdapter
        val currentIndex = mediosPago.indexOfFirst { it.codigo == ticket.medioPagoCodigo }
        if (currentIndex >= 0) spinnerMedioPago.setSelection(currentIndex)

        AlertDialog.Builder(this)
            .setTitle("Editar Ticket")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val medioPagoSeleccionado = mediosPago[spinnerMedioPago.selectedItemPosition]
                val updatedTicket = ticket.copy(
                    boleta = boletaInput.text.toString(),
                    inmueble = inmuebleInput.text.toString(),
                    vigilanteIngreso = vigilanteIngresoInput.text.toString(),
                    vigilanteSalida = vigilanteSalidaInput.text.toString(),
                    placa = placaInput.text.toString(),
                    tipoVehiculo = tipoVehiculoInput.text.toString(),
                    fechaEntrada = fechaEntradaInput.text.toString(),
                    fechaSalida = fechaSalidaInput.text.toString(),
                    tiempo = tiempoInput.text.toString(),
                    total = totalInput.text.toString(),
                    medioPagoCodigo = medioPagoSeleccionado.codigo
                )
                viewModel.updateTicket(updatedTicket)
                Toast.makeText(this, "Ticket actualizado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showDeleteTicketDialog(ticketId: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar Ticket")
        
        val input = EditText(this)
        input.hint = "Contraseña"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)
        
        builder.setPositiveButton("Eliminar") { _, _ ->
            val password = input.text.toString()
            if (password == "shark") {
                viewModel.deleteTicket(ticketId)
                Toast.makeText(this, "Ticket eliminado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showCloseScannerDialog() {
        val scannerName = scannerNameText.text.toString()
        AlertDialog.Builder(this)
            .setTitle("Cerrar Fecha de Recepción")
            .setMessage("¿Estás seguro de cerrar '$scannerName'? Se generarán los archivos TXT y CSV.")
            .setPositiveButton("Cerrar") { _, _ ->
                viewModel.closeScanner()
                Toast.makeText(this, "Fecha de recepción cerrada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteScannerDialog() {
        val scannerName = scannerNameText.text.toString()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar Fecha de Recepción")

        val input = EditText(this)
        input.hint = "Contraseña"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Eliminar") { _, _ ->
            val password = input.text.toString()
            if (password == "shark") {
                viewModel.deleteScanner(scannerName)
                Toast.makeText(this, "Fecha de recepción eliminada", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showDeleteAllTicketsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar Todas las Boletas")
        
        val input = EditText(this)
        input.hint = "Contraseña"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)
        
        builder.setPositiveButton("Eliminar") { _, _ ->
            val password = input.text.toString()
            if (password == "shark") {
                val scannerName = scannerNameText.text.toString()
                viewModel.deleteAllTickets(scannerName)
                Toast.makeText(this, "Todas las boletas eliminadas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "no se puede eliminar", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun updatePreviewData() {
        Log.d("ScannerDetailActivity", "updatePreviewData called, extractedText: $extractedText, generatedJson: $generatedJson")
        
        val displayText = StringBuilder()
        
        if (extractedText.isNotEmpty() && generatedJson.isNotEmpty()) {
            Log.d("ScannerDetailActivity", "updatePreviewData called")
            Log.d("ScannerDetailActivity", "Generated JSON: $generatedJson")
            val jsonObject = JSONObject(generatedJson)
            
            // Mostrar solo los campos específicos solicitados
            val boleta = jsonObject.optString("boleta", "")
            if (boleta.isNotEmpty()) {
                displayText.append("Boleta: $boleta\n")
            }
            
            val inmueble = jsonObject.optString("inmueble", "")
            if (inmueble.isNotEmpty()) {
                displayText.append("Inmueble: $inmueble\n")
            }
            
            val vigilanteIngreso = jsonObject.optString("vigilanteIngreso", "")
            if (vigilanteIngreso.isNotEmpty()) {
                displayText.append("Vigilante Ingreso: $vigilanteIngreso\n")
            }
            
            val vigilanteSalida = jsonObject.optString("vigilanteSalida", "")
            if (vigilanteSalida.isNotEmpty()) {
                displayText.append("Vigilante Salida: $vigilanteSalida\n")
            }
            
            val placa = jsonObject.optString("placa", "")
            if (placa.isNotEmpty()) {
                displayText.append("Placa: $placa\n")
            }
            
            val tipoVehiculo = jsonObject.optString("tipoVehiculo", "")
            if (tipoVehiculo.isNotEmpty()) {
                displayText.append("Tipo Vehículo: $tipoVehiculo\n")
            }
            
            val fechaEntrada = jsonObject.optString("fechaEntrada", "")
            if (fechaEntrada.isNotEmpty()) {
                displayText.append("Fecha Entrada: $fechaEntrada\n")
            }
            
            val fechaSalida = jsonObject.optString("fechaSalida", "")
            if (fechaSalida.isNotEmpty()) {
                displayText.append("Fecha Salida: $fechaSalida\n")
            }
            
            val tiempo = jsonObject.optString("tiempo", "")
            if (tiempo.isNotEmpty()) {
                displayText.append("Tiempo: $tiempo\n")
            }

            val total = jsonObject.optString("total", "")
            if (total.isNotEmpty()) {
                displayText.append("Total a Pagar: $total\n")
            }

            // Cross-validate tiempo vs total
            val tiempoAmt = parseTiempoToAmount(tiempo)
            val totalAmt = parseColombianAmount(total)
            if (tiempoAmt > 0 && totalAmt > 0 && tiempoAmt != totalAmt) {
                val copFmt = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "CO")).apply { maximumFractionDigits = 0 }
                displayText.append("\n⚠️ INCONSISTENCIA: tiempo sugiere \$${copFmt.format(tiempoAmt)} pero total dice \$${copFmt.format(totalAmt)}\n")
                displayText.append("   Verifica el campo incorrecto antes de guardar.\n")
            }
            
            // Mostrar todos los campos adicionales que existan en el JSON
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                // Solo mostrar campos que no estén ya en la lista
                if (key !in listOf("boleta", "inmueble", "vigilanteIngreso", "vigilanteSalida", "placa", "tipoVehiculo", "fechaEntrada", "fechaSalida", "tiempo", "total")) {
                    val value = jsonObject.optString(key, "")
                    if (value.isNotEmpty()) {
                        displayText.append("${key.replaceFirstChar { it.uppercaseChar() }}: $value\n")
                    }
                }
            }
        } else {
            displayText.append("No se pudieron extraer datos del ticket\n")
            displayText.append("Texto extraído: ${extractedText.take(100)}...\n")
            displayText.append("JSON generado: ${generatedJson.take(100)}...")
        }
        
        Log.d("ScannerDetailActivity", "Display text: ${displayText.toString()}")
        Log.d("ScannerDetailActivity", "Setting previewDataText visibility to VISIBLE")
        
        // Hacer visible el buttonRow antes del previewDataText
        findViewById<View>(R.id.buttonRow).visibility = View.VISIBLE
        
        previewDataText.text = displayText.toString()
        previewDataText.visibility = View.VISIBLE
        addButton.isEnabled = true
        Log.d("ScannerDetailActivity", "previewDataText visibility after setting: ${previewDataText.visibility}")
        
        Toast.makeText(this, "Preview actualizado", Toast.LENGTH_SHORT).show()
    }

    private fun checkCameraPermission() {
        Log.d("ScannerDetailActivity", "checkCameraPermission called")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d("ScannerDetailActivity", "Camera permission granted, starting camera")
            lifecycleScope.launch {
                startCamera()
            }
        } else {
            Log.d("ScannerDetailActivity", "Camera permission not granted, requesting permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private suspend fun startCamera() {
        try {
            Log.d("ScannerDetailActivity", "Starting camera initialization")
            cameraService.initializeCamera(previewView, this)
            runOnUiThread {
                isCameraActive = true
                detectionStreak = 0
                autoCapturePending = false
                previewView.visibility = View.VISIBLE
                capturedImageView.visibility = View.GONE
                captureButton.visibility = View.VISIBLE
                cancelCameraButton.visibility = View.VISIBLE
                retakeButton.isEnabled = false
                addButton.isEnabled = false
                fabAddTicket.visibility = View.GONE
                fabDeleteAllTickets.visibility = View.GONE
                previewDataText.visibility = View.GONE
                ticketsTitleView.visibility = View.GONE
                ticketsListView.visibility = View.GONE
                cameraStatusText.text = "Apunta al ticket"
                cameraStatusText.visibility = View.VISIBLE
                findViewById<View>(R.id.buttonRow).visibility = View.GONE
                startAutoAnalysis()
            }
        } catch (e: Exception) {
            Log.e("ScannerDetailActivity", "Camera start failed", e)
            runOnUiThread {
                Toast.makeText(this@ScannerDetailActivity, "Camera start failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoAnalysis() {
        cameraService.setFrameAnalyzer { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null || autoCapturePending) {
                imageProxy.close()
                return@setFrameAnalyzer
            }
            val now = System.currentTimeMillis()
            if (now - lastAnalysisMs < 1500) {
                imageProxy.close()
                return@setFrameAnalyzer
            }
            lastAnalysisMs = now
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            frameRecognizer.process(inputImage).addOnCompleteListener { task ->
                imageProxy.close()
                val text = task.result?.text ?: ""
                val lower = text.lowercase()
                val hasTicket = text.contains(Regex("\\b\\d{4,5}\\b")) &&
                    (lower.contains("vigilante") || lower.contains("placa") || lower.contains("total"))
                runOnUiThread {
                    if (!isCameraActive || autoCapturePending) return@runOnUiThread
                    if (hasTicket) {
                        detectionStreak++
                        cameraStatusText.text = "✓ Ticket detectado — capturando..."
                        cameraStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        if (detectionStreak >= 2) {
                            autoCapturePending = true
                            capturePhoto()
                        }
                    } else {
                        detectionStreak = 0
                        cameraStatusText.text = "Apunta al ticket"
                        cameraStatusText.setTextColor(android.graphics.Color.WHITE)
                    }
                }
            }
        }
    }

    private fun capturePhoto() {
        lifecycleScope.launch {
            try {
                val imageProxy = cameraService.capturePhoto()
                
                val bitmap = imageProxyToBitmap(imageProxy)
                currentImageBitmap = bitmap
                
                runOnUiThread {
                    capturedImageView.setImageBitmap(bitmap)
                    capturedImageView.visibility = View.VISIBLE
                    previewView.visibility = View.GONE
                    captureButton.visibility = View.GONE
                    retakeButton.isEnabled = true
                    addButton.isEnabled = true
                    findViewById<View>(R.id.buttonRow).visibility = View.VISIBLE
                }
                
                processImage(bitmap)
                imageProxy.close()
            } catch (e: Exception) {
                Log.e("ScannerDetailActivity", "Capture failed", e)
                Toast.makeText(this@ScannerDetailActivity, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap) {
        try {
            val text = ocrService.recognizeText(bitmap)
            extractedText = text
            viewModel.setExtractedText(text)
            
            val keyValueJson = ocrService.transformToKeyValue(text)
            generatedJson = keyValueJson
            viewModel.setGeneratedJson(keyValueJson)
            
            Log.d("ScannerDetailActivity", "Extracted text: $text")
            Log.d("ScannerDetailActivity", "Generated JSON: $keyValueJson")
            
            runOnUiThread {
                showPreviewDialog(keyValueJson)
            }
        } catch (e: Exception) {
            Log.e("ScannerDetailActivity", "OCR failed", e)
            runOnUiThread {
                Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showPreviewDialog(jsonData: String) {
        val jsonObject = JSONObject(jsonData)
        
        val message = StringBuilder()
        val boleta = jsonObject.optString("boleta", "")
        if (boleta.isNotEmpty()) {
            message.append("Boleta: $boleta\n")
        }
        
        val inmueble = jsonObject.optString("inmueble", "")
        if (inmueble.isNotEmpty()) {
            message.append("Inmueble: $inmueble\n")
        }
        
        val vigilanteIngreso = jsonObject.optString("vigilanteIngreso", "")
        if (vigilanteIngreso.isNotEmpty()) {
            message.append("Vigilante Ingreso: $vigilanteIngreso\n")
        }
        
        val vigilanteSalida = jsonObject.optString("vigilanteSalida", "")
        if (vigilanteSalida.isNotEmpty()) {
            message.append("Vigilante Salida: $vigilanteSalida\n")
        }
        
        val placa = jsonObject.optString("placa", "")
        if (placa.isNotEmpty()) {
            message.append("Placa: $placa\n")
        }
        
        val tipoVehiculo = jsonObject.optString("tipoVehiculo", "")
        if (tipoVehiculo.isNotEmpty()) {
            message.append("Tipo Vehículo: $tipoVehiculo\n")
        }
        
        val fechaEntrada = jsonObject.optString("fechaEntrada", "")
        if (fechaEntrada.isNotEmpty()) {
            message.append("Fecha Entrada: $fechaEntrada\n")
        }
        
        val fechaSalida = jsonObject.optString("fechaSalida", "")
        if (fechaSalida.isNotEmpty()) {
            message.append("Fecha Salida: $fechaSalida\n")
        }
        
        val tiempo = jsonObject.optString("tiempo", "")
        if (tiempo.isNotEmpty()) {
            message.append("Tiempo: $tiempo\n")
        }
        
        val total = jsonObject.optString("total", "")
        if (total.isNotEmpty()) {
            message.append("Total a Pagar: $total\n")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Previsualización del Ticket")
            .setMessage(message.toString())
            .setPositiveButton("Guardar") { _, _ ->
                showMedioPagoDialog()
            }
            .setNegativeButton("Retomar") { _, _ ->
                retakePhoto()
            }
            .setNeutralButton("Cancelar") { _, _ ->
                resetToInitialState()
            }
            .show()
    }
    
    private fun showMedioPagoDialog() {
        val mediosPago = CatalogoMediosPago.mediosPago
        val opciones = mediosPago.map { "${it.codigo}. ${it.tipo}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Medio de Pago")
            .setItems(opciones) { _, which ->
                val medioPagoSeleccionado = mediosPago[which]
                selectedMedioPago = medioPagoSeleccionado
                addTicketToScanner()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun shareExportedFiles(scannerName: String) {
        val dir = java.io.File(filesDir, "ParkingScanner")
        val uris = arrayListOf<android.net.Uri>()
        listOf("$scannerName.csv", "$scannerName.txt").forEach { name ->
            val f = java.io.File(dir, name)
            if (f.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", f
                )
                uris.add(uri)
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "No hay archivos exportados aún. Primero cierra la fecha.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Compartir archivos de $scannerName"))
    }

    private fun cancelCamera() {
        lifecycleScope.launch {
            cameraService.clearAnalyzer()
            cameraService.releaseCamera()
            runOnUiThread {
                isCameraActive = false
                cameraStatusText.visibility = View.GONE
                ticketsTitleView.visibility = View.VISIBLE
                ticketsListView.visibility = View.VISIBLE
                resetToInitialState()
            }
        }
    }

    private fun resetToInitialState() {
        previewView.visibility = View.GONE
        capturedImageView.visibility = View.GONE
        captureButton.visibility = View.GONE
        cancelCameraButton.visibility = View.GONE
        retakeButton.isEnabled = false
        addButton.isEnabled = false
        fabAddTicket.visibility = View.VISIBLE
        fabDeleteAllTickets.visibility = View.VISIBLE
        previewDataText.visibility = View.GONE
        cameraStatusText.visibility = View.GONE
        ticketsTitleView.visibility = View.VISIBLE
        ticketsListView.visibility = View.VISIBLE
        isCameraActive = false
        findViewById<View>(R.id.buttonRow).visibility = View.GONE
        currentImageBitmap = null
        extractedText = ""
        generatedJson = ""
        
        lifecycleScope.launch {
            cameraService.releaseCamera()
        }
    }

    private fun addTicketToScanner() {
        Log.d("ScannerDetailActivity", "addTicketToScanner called")
        Log.d("ScannerDetailActivity", "Generated JSON: $generatedJson")
        val jsonObject = JSONObject(generatedJson)
        val dataMap = mutableMapOf<String, String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            dataMap[key] = jsonObject.optString(key, "")
        }
        
        val newBoleta = jsonObject.optString("boleta", "").trim()
        Log.d("ScannerDetailActivity", "New boleta: $newBoleta")

        // Obtener tickets actuales para verificar duplicados
        val currentTickets = viewModel.uiState.value?.currentScanner?.tickets ?: emptyList()
        Log.d("ScannerDetailActivity", "Current tickets count: ${currentTickets.size}")
        Log.d("ScannerDetailActivity", "Current tickets: $currentTickets")

        val existingTicket = if (newBoleta.isNotEmpty()) currentTickets.find { it.boleta.trim() == newBoleta } else null
        Log.d("ScannerDetailActivity", "Existing ticket: $existingTicket")

        if (existingTicket != null) {
            Log.d("ScannerDetailActivity", "Boleta already exists, showing error message")
            runOnUiThread {
                Toast.makeText(this, "No se puede agregar: La boleta $newBoleta ya existe", Toast.LENGTH_SHORT).show()
                Log.d("ScannerDetailActivity", "Toast shown")
                resetToInitialState()
            }
            return
        }
        
        val uniqueId = "ticket_${newBoleta.trim()}"

        val rawTotal = run {
            val ocr = jsonObject.optString("total", "")
                .ifEmpty { jsonObject.optString("totalAPagar", "") }
                .ifEmpty { jsonObject.optString("valorAPagar", "") }
            val ocrAmount = parseColombianAmount(ocr)
            if (ocrAmount > 0) {
                // Total legible — úsalo directamente
                ocr
            } else {
                // Total vacío/ilegible — calcula por tiempo
                val tiempo = jsonObject.optString("tiempo", "")
                val tiempoAmount = parseTiempoToAmount(tiempo)
                if (tiempoAmount > 0) tiempoAmount.toLong().toString() else ocr
            }
        }

        // Save image locally
        val imagePath = currentImageBitmap?.let { saveImageLocally(uniqueId, it) } ?: ""

        val ticket = Ticket(
            id = uniqueId,
            boleta = newBoleta,
            inmueble = jsonObject.optString("inmueble", ""),
            vigilanteIngreso = jsonObject.optString("vigilanteIngreso", ""),
            vigilanteSalida = jsonObject.optString("vigilanteSalida", ""),
            placa = jsonObject.optString("placa", ""),
            tipoVehiculo = jsonObject.optString("tipoVehiculo", ""),
            fechaEntrada = jsonObject.optString("fechaEntrada", ""),
            fechaSalida = jsonObject.optString("fechaSalida", ""),
            tiempo = jsonObject.optString("tiempo", ""),
            total = rawTotal,
            extractedText = extractedText,
            data = dataMap,
            medioPagoCodigo = selectedMedioPago?.codigo ?: 0,
            imagePath = imagePath
        )

        
        Log.d("ScannerDetailActivity", "Ticket created with:")
        Log.d("ScannerDetailActivity", "  - boleta: ${ticket.boleta}")
        Log.d("ScannerDetailActivity", "  - inmueble: ${ticket.inmueble}")
        Log.d("ScannerDetailActivity", "  - vigilanteIngreso: ${ticket.vigilanteIngreso}")
        Log.d("ScannerDetailActivity", "  - vigilanteSalida: ${ticket.vigilanteSalida}")
        Log.d("ScannerDetailActivity", "  - placa: ${ticket.placa}")
        Log.d("ScannerDetailActivity", "  - tipoVehiculo: ${ticket.tipoVehiculo}")
        Log.d("ScannerDetailActivity", "  - fechaEntrada: ${ticket.fechaEntrada}")
        Log.d("ScannerDetailActivity", "  - fechaSalida: ${ticket.fechaSalida}")
        Log.d("ScannerDetailActivity", "  - tiempo: ${ticket.tiempo}")
        Log.d("ScannerDetailActivity", "  - total: ${ticket.total}")
        Log.d("ScannerDetailActivity", "  - data: ${ticket.data}")
        
        viewModel.addTicket(ticket)
        
        runOnUiThread {
            Toast.makeText(this, "Ticket agregado", Toast.LENGTH_SHORT).show()
            resetToInitialState()
        }
    }

    private fun retakePhoto() {
        capturedImageView.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        captureButton.visibility = View.VISIBLE
        retakeButton.isEnabled = false
        addButton.isEnabled = false
        findViewById<View>(R.id.buttonRow).visibility = View.GONE
        previewDataText.visibility = View.GONE
        currentImageBitmap = null
        extractedText = ""
        generatedJson = ""
        
        lifecycleScope.launch {
            startCamera()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        return rotatedBitmap
    }

    private fun parseTiempoToAmount(tiempo: String): Double {
        if (tiempo.isBlank()) return 0.0
        val lower = tiempo.lowercase()
        val numbers = Regex("\\d+").findAll(tiempo).map { it.value.toDouble() }.toList()
        if (numbers.isEmpty()) return 0.0
        return when {
            lower.contains("minuto") && !lower.contains("hora") -> numbers[0] / 60.0 * 1000.0
            lower.contains("hora") && lower.contains("minuto") && numbers.size >= 2 ->
                (numbers[0] + numbers[1] / 60.0) * 1000.0
            else -> numbers[0] * 1000.0
        }
    }

    private fun effectiveAmount(total: String, tiempo: String): Double {
        val fromTotal = parseColombianAmount(total)
        val fromTiempo = parseTiempoToAmount(tiempo)
        return when {
            fromTotal > 0.0 -> fromTotal   // total impreso = fuente de verdad
            fromTiempo > 0.0 -> fromTiempo // fallback si OCR no leyó el total
            else -> 0.0
        }
    }

    private fun parseColombianAmount(raw: String): Double {
        val cleaned = raw.replace("$", "").replace("@", "").replace("S", "").trim()
        if (cleaned.isEmpty()) return 0.0
        return if (cleaned.contains(",")) {
            cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        } else {
            cleaned.replace(".", "").toDoubleOrNull() ?: 0.0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraService.releaseCamera()
    }

    private fun saveImageLocally(ticketId: String, bitmap: Bitmap): String {
        return try {
            val imagesDir = File(filesDir, "ParkingScanner/images").also { it.mkdirs() }
            val file = File(imagesDir, "$ticketId.jpg")
            FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        } catch (_: Exception) { "" }
    }

}
