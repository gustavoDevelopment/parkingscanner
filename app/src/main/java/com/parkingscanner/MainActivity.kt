package com.parkingscanner

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.parkingscanner.data.repository.ScannerRepositoryImpl
import com.parkingscanner.domain.model.ScannerSummary
import com.parkingscanner.domain.usecase.*
import com.parkingscanner.presentation.viewmodel.ScannerViewModel
import com.parkingscanner.presentation.viewmodel.ScannerViewModelFactory
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var scannerListView: android.widget.ListView
    private lateinit var fabNewScanner: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabCatalog: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabGlobalIndex: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var viewModel: ScannerViewModel

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
            ExportToCsvUseCase(repository),
            RenameScannerUseCase(repository)
        )
        viewModel = ViewModelProvider(this, factory)[ScannerViewModel::class.java]

        scannerListView = findViewById(R.id.scannerListView)
        fabNewScanner = findViewById(R.id.fabNewScanner)
        fabCatalog = findViewById(R.id.fabCatalog)
        fabGlobalIndex = findViewById(R.id.fabGlobalIndex)

        fabNewScanner.setOnClickListener { showScannerFormDialog(null) }
        fabCatalog.setOnClickListener {
            startActivity(Intent(this, CatalogManagementActivity::class.java))
        }
        fabGlobalIndex.setOnClickListener {
            startActivity(Intent(this, GlobalIndexActivity::class.java))
        }

        viewModel.uiState.observe(this, Observer { state ->
            if (state.scannerSummaries.isNotEmpty()) {
                updateScannerList(state.scannerSummaries)
            }
        })

        viewModel.loadScannerList()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadScannerList()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Cerrar sesión")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        return super.onOptionsItemSelected(item)
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
            },
            onEditClick = { name ->
                showScannerFormDialog(name)
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

    // Shared form for create (editingName=null) and rename (editingName=existing name)
    private fun showScannerFormDialog(editingName: String?) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = ((currentYear - 2)..(currentYear + 1)).map { it.toString() }
        val days = (1..31).map { it.toString().padStart(2, '0') }
        val months = listOf(
            "01 - Enero", "02 - Febrero", "03 - Marzo", "04 - Abril",
            "05 - Mayo", "06 - Junio", "07 - Julio", "08 - Agosto",
            "09 - Septiembre", "10 - Octubre", "11 - Noviembre", "12 - Diciembre"
        )
        val monthCodes = (1..12).map { it.toString().padStart(2, '0') }

        // Parse existing name to pre-populate spinners: YYYY(DD-MM A DD-MM)
        val parsed = editingName?.let {
            Regex("""^(\d{4})\((\d{2})-(\d{2}) A (\d{2})-(\d{2})\)$""").matchEntire(it)?.groupValues
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        fun makeSpinner(items: List<String>, selectedIndex: Int = 0) = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selectedIndex.coerceIn(0, items.size - 1))
        }

        val cal = Calendar.getInstance()

        val descLabel = TextView(this).apply { text = "Descripción" }
        val descInput = EditText(this).apply {
            hint = "Descripcion del recorrido"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val yearLabel = TextView(this).apply { text = "Año" }
        val yearSpinner = makeSpinner(years,
            if (parsed != null) years.indexOf(parsed[1]).coerceAtLeast(0)
            else years.indexOf(currentYear.toString()).coerceAtLeast(0))

        val startLabel = TextView(this).apply { text = "Fecha inicio" }
        val startRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        val startDaySpinner = makeSpinner(days,
            if (parsed != null) days.indexOf(parsed[2]) else cal.get(Calendar.DAY_OF_MONTH) - 1)
        val startMonthSpinner = makeSpinner(months,
            if (parsed != null) monthCodes.indexOf(parsed[3]) else cal.get(Calendar.MONTH))
        startDaySpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        startMonthSpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        startRow.addView(startDaySpinner)
        startRow.addView(startMonthSpinner)

        val endLabel = TextView(this).apply { text = "Fecha fin" }
        val endRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        val endDaySpinner = makeSpinner(days,
            if (parsed != null) days.indexOf(parsed[4]) else cal.get(Calendar.DAY_OF_MONTH) - 1)
        val endMonthSpinner = makeSpinner(months,
            if (parsed != null) monthCodes.indexOf(parsed[5]) else cal.get(Calendar.MONTH))
        endDaySpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        endMonthSpinner.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        endRow.addView(endDaySpinner)
        endRow.addView(endMonthSpinner)

        layout.addView(descLabel); layout.addView(descInput)
        layout.addView(yearLabel); layout.addView(yearSpinner)
        layout.addView(startLabel); layout.addView(startRow)
        layout.addView(endLabel); layout.addView(endRow)

        val title = if (editingName == null) "Nueva Fecha de Recogida" else "Editar Fecha de Recogida"
        val btnLabel = if (editingName == null) "Crear" else "Guardar"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(btnLabel) { _, _ ->
                val desc = descInput.text.toString().trim()
                val year = yearSpinner.selectedItem.toString()
                val startDay = days[startDaySpinner.selectedItemPosition]
                val startMonth = monthCodes[startMonthSpinner.selectedItemPosition]
                val endDay = days[endDaySpinner.selectedItemPosition]
                val endMonth = monthCodes[endMonthSpinner.selectedItemPosition]
                val newName = "$year($startDay-$startMonth A $endDay-$endMonth)"

                if (editingName == null) {
                    if (desc.isEmpty()) {
                        Toast.makeText(this, "La descripción es requerida", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val existing = viewModel.uiState.value?.let { state ->
                        state.scannerSummaries.map { it.name }.ifEmpty { state.scannerNames }
                    } ?: emptyList()
                    if (existing.contains(newName)) {
                        Toast.makeText(this, "Esta fecha de recogida ya existe", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.createScanner(newName, desc)
                    }
                } else {
                    android.util.Log.d("RENAME", "btn: '$editingName' -> '$newName'")
                    if (newName == editingName) {
                        Toast.makeText(this, "El nombre no cambió", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.renameScanner(editingName, newName)
                        Toast.makeText(this, "Renombrando...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
