package com.parkingscanner

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.parkingscanner.data.repository.ScannerRepositoryImpl
import com.parkingscanner.GlobalIndexActivity
import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.usecase.*
import com.parkingscanner.presentation.viewmodel.ScannerViewModel
import com.parkingscanner.presentation.viewmodel.ScannerViewModelFactory
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ScannerViewModel
    private lateinit var scannerListView: ListView
    private lateinit var fabNewScanner: FloatingActionButton
    private lateinit var fabCatalog: FloatingActionButton
    private lateinit var fabGlobalIndex: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val repository = ScannerRepositoryImpl(this)
        val factory = ScannerViewModelFactory(
            CreateScannerUseCase(repository),
            LoadScannerUseCase(repository),
            LoadAllScannersUseCase(repository),
            LoadAllScannerSummariesUseCase(repository),
            AddTicketUseCase(repository),
            DeleteTicketUseCase(repository),
            UpdateTicketUseCase(repository),
            DeleteAllTicketsUseCase(repository),
            DeleteAllScannersUseCase(repository),
            CloseScannerUseCase(repository),
            DeleteScannerUseCase(repository),
            ExportToCsvUseCase(repository)
        )
        viewModel = ViewModelProvider(this, factory)[ScannerViewModel::class.java]

        scannerListView = findViewById(R.id.scannerListView)
        fabNewScanner = findViewById(R.id.fabNewScanner)
        fabCatalog = findViewById(R.id.fabCatalog)
        fabGlobalIndex = findViewById(R.id.fabGlobalIndex)

        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadScannerList()
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this, Observer { state ->
            val summaries = state.scannerSummaries.ifEmpty {
                state.scannerNames.map { ScannerSummary(it, 0, 0.0) }
            }
            updateScannerList(summaries)
            state.error?.let { error ->
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    private fun setupListeners() {
        fabNewScanner.setOnClickListener { showNewScannerDialog() }
        fabCatalog.setOnClickListener {
            startActivity(Intent(this, CatalogManagementActivity::class.java))
        }
        fabGlobalIndex.setOnClickListener {
            startActivity(Intent(this, GlobalIndexActivity::class.java))
        }
    }

    private fun updateScannerList(summaries: List<ScannerSummary>) {
        scannerListView.adapter = ScannerAdapter(
            context = this,
            summaries = summaries,
            onItemClick = { name ->
                val intent = Intent(this, ScannerDetailActivity::class.java)
                intent.putExtra("SCANNER_NAME", name)
                startActivity(intent)
            },
            onDeleteClick = { name ->
                showDeleteScannerDialog(name)
            }
        )
    }

    private fun showDeleteScannerDialog(name: String) {
        val input = EditText(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Eliminar fecha de recogida")
            .setMessage("¿Eliminar '$name'?")
            .setView(input)
            .setPositiveButton("Eliminar") { _, _ ->
                if (input.text.toString() == "shark") {
                    viewModel.deleteScanner(name)
                    Toast.makeText(this, "Fecha de recogida eliminada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showNewScannerDialog() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = ((currentYear - 2)..(currentYear + 1)).map { it.toString() }
        val days = (1..31).map { it.toString().padStart(2, '0') }
        val months = listOf(
            "01 - Enero", "02 - Febrero", "03 - Marzo", "04 - Abril",
            "05 - Mayo", "06 - Junio", "07 - Julio", "08 - Agosto",
            "09 - Septiembre", "10 - Octubre", "11 - Noviembre", "12 - Diciembre"
        )
        val monthCodes = (1..12).map { it.toString().padStart(2, '0') }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        fun makeSpinner(items: List<String>, selectedIndex: Int = 0) = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selectedIndex)
        }

        val descLabel = TextView(this).apply { text = "Descripción" }
        val descInput = EditText(this).apply {
            hint = "Descripcion del recorrido"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val yearLabel = TextView(this).apply { text = "Año" }
        val yearSpinner = makeSpinner(years, years.indexOf(currentYear.toString()).coerceAtLeast(0))

        val startLabel = TextView(this).apply { text = "Fecha inicio" }
        val startRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val cal = Calendar.getInstance()
        val startDaySpinner = makeSpinner(days, cal.get(Calendar.DAY_OF_MONTH) - 1)
        val startMonthSpinner = makeSpinner(months, cal.get(Calendar.MONTH))
        startDaySpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        startMonthSpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        startRow.addView(startDaySpinner)
        startRow.addView(startMonthSpinner)

        val endLabel = TextView(this).apply { text = "Fecha fin" }
        val endRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val endDaySpinner = makeSpinner(days, cal.get(Calendar.DAY_OF_MONTH) - 1)
        val endMonthSpinner = makeSpinner(months, cal.get(Calendar.MONTH))
        endDaySpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        endMonthSpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        endRow.addView(endDaySpinner)
        endRow.addView(endMonthSpinner)

        layout.addView(descLabel)
        layout.addView(descInput)
        layout.addView(yearLabel)
        layout.addView(yearSpinner)
        layout.addView(startLabel)
        layout.addView(startRow)
        layout.addView(endLabel)
        layout.addView(endRow)

        AlertDialog.Builder(this)
            .setTitle("Nueva Fecha de Recogida")
            .setView(layout)
            .setPositiveButton("Crear") { _, _ ->
                val desc = descInput.text.toString().trim()
                val year = yearSpinner.selectedItem.toString()
                val startDay = days[startDaySpinner.selectedItemPosition]
                val startMonth = monthCodes[startMonthSpinner.selectedItemPosition]
                val endDay = days[endDaySpinner.selectedItemPosition]
                val endMonth = monthCodes[endMonthSpinner.selectedItemPosition]
                val start = "$startDay-$startMonth"
                val end = "$endDay-$endMonth"
                if (desc.isEmpty()) {
                    Toast.makeText(this, "La descripción es requerida", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = "$year($start A $end)"
                val existing = viewModel.uiState.value?.let { state ->
                    state.scannerSummaries.map { it.name }.ifEmpty { state.scannerNames }
                } ?: emptyList()
                if (existing.contains(name)) {
                    Toast.makeText(this, "Esta fecha de recogida ya existe", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createScanner(name, desc)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
