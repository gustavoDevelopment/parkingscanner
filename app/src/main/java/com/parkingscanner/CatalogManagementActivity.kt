package com.parkingscanner

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.parkingscanner.data.repository.CatalogStorage
import com.parkingscanner.domain.model.*
import kotlin.concurrent.thread

class CatalogManagementActivity : AppCompatActivity() {

    private lateinit var tabHost: TabHost
    private lateinit var listViewVigilantes: ListView
    private lateinit var listViewVehiculos: ListView
    private lateinit var listViewMediosPago: ListView
    private lateinit var fabAddVigilante: FloatingActionButton
    private lateinit var fabAddVehiculo: FloatingActionButton
    private lateinit var fabAddMedioPago: FloatingActionButton

    private lateinit var catalogStorage: CatalogStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog_management)

        catalogStorage = CatalogStorage(this)

        tabHost = findViewById(R.id.tabHost)
        listViewVigilantes = findViewById(R.id.listViewVigilantes)
        listViewVehiculos = findViewById(R.id.listViewVehiculos)
        listViewMediosPago = findViewById(R.id.listViewMediosPago)
        fabAddVigilante = findViewById(R.id.fabAddVigilante)
        fabAddVehiculo = findViewById(R.id.fabAddVehiculo)
        fabAddMedioPago = findViewById(R.id.fabAddMedioPago)

        setupTabs()
        refreshAllLists()
        setupFabs()
    }

    private fun setupTabs() {
        tabHost.setup()
        tabHost.newTabSpec("vigilantes").apply {
            setContent(R.id.tabVigilantes)
            setIndicator("Vigilantes")
            tabHost.addTab(this)
        }
        tabHost.newTabSpec("vehiculos").apply {
            setContent(R.id.tabVehiculos)
            setIndicator("Vehiculos")
            tabHost.addTab(this)
        }
        tabHost.newTabSpec("mediosPago").apply {
            setContent(R.id.tabMediosPago)
            setIndicator("Medios Pago")
            tabHost.addTab(this)
        }
    }

    private fun refreshAllLists() {
        refreshVigilantesList()
        refreshVehiculosList()
        refreshMediosPagoList()
    }

    private fun refreshVigilantesList() {
        val items = CatalogoVigilantes.vigilantes
        listViewVigilantes.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(pos: Int) = items[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = convertView ?: layoutInflater.inflate(R.layout.catalog_list_item, parent, false)
                v.findViewById<android.widget.TextView>(R.id.catalogCodigo).text = items[pos].codigo.toString()
                v.findViewById<android.widget.TextView>(R.id.catalogNombre).text = items[pos].nombre
                return v
            }
        }
        listViewVigilantes.setOnItemClickListener { _, _, position, _ ->
            showEditVigilanteDialog(CatalogoVigilantes.vigilantes[position])
        }
    }

    private fun refreshVehiculosList() {
        val items = CatalogoVehiculos.vehiculos
        listViewVehiculos.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(pos: Int) = items[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = convertView ?: layoutInflater.inflate(R.layout.catalog_list_item, parent, false)
                v.findViewById<android.widget.TextView>(R.id.catalogCodigo).text = items[pos].codigo.toString()
                v.findViewById<android.widget.TextView>(R.id.catalogNombre).text = items[pos].tipo
                return v
            }
        }
        listViewVehiculos.setOnItemClickListener { _, _, position, _ ->
            showEditVehiculoDialog(CatalogoVehiculos.vehiculos[position])
        }
    }

    private fun refreshMediosPagoList() {
        val items = CatalogoMediosPago.mediosPago
        listViewMediosPago.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(pos: Int) = items[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = convertView ?: layoutInflater.inflate(R.layout.catalog_list_item_medio_pago, parent, false)
                v.findViewById<android.widget.TextView>(R.id.catalogCodigo).text = items[pos].codigo.toString()
                v.findViewById<android.widget.TextView>(R.id.catalogTipo).text = items[pos].tipo
                val computaView = v.findViewById<android.widget.TextView>(R.id.catalogComputa)
                if (items[pos].computa) {
                    computaView.text = "Sí"
                    computaView.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                } else {
                    computaView.text = "No"
                    computaView.setTextColor(android.graphics.Color.parseColor("#C62828"))
                }
                return v
            }
        }
        listViewMediosPago.setOnItemClickListener { _, _, position, _ ->
            showEditMedioPagoDialog(CatalogoMediosPago.mediosPago[position])
        }
    }

    private fun setupFabs() {
        fabAddVigilante.setOnClickListener { showAddVigilanteDialog() }
        fabAddVehiculo.setOnClickListener { showAddVehiculoDialog() }
        fabAddMedioPago.setOnClickListener { showAddMedioPagoDialog() }
    }

    // ── Vigilantes ──────────────────────────────────────────────────────────

    private fun showEditVigilanteDialog(vigilante: Vigilante) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_vigilante, null)
        dialogView.findViewById<TextView>(R.id.editCodigo).text = vigilante.codigo.toString()
        val nombreInput = dialogView.findViewById<EditText>(R.id.editNombre)
        nombreInput.setText(vigilante.nombre)

        AlertDialog.Builder(this)
            .setTitle("Editar Vigilante")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = nombreInput.text.toString().trim()
                if (nuevoNombre.isNotEmpty()) {
                    val idx = CatalogoVigilantes.vigilantes.indexOfFirst { it.codigo == vigilante.codigo }
                    if (idx >= 0) {
                        CatalogoVigilantes.vigilantes[idx] = vigilante.copy(nombre = nuevoNombre)
                        thread { catalogStorage.save() }
                        refreshVigilantesList()
                        Toast.makeText(this, "Vigilante actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddVigilanteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_vigilante, null)
        val nextCodigo = (CatalogoVigilantes.vigilantes.maxOfOrNull { it.codigo } ?: 0) + 1
        dialogView.findViewById<TextView>(R.id.editCodigo).text = nextCodigo.toString()
        val nombreInput = dialogView.findViewById<EditText>(R.id.editNombre)

        AlertDialog.Builder(this)
            .setTitle("Agregar Vigilante")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = nombreInput.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    CatalogoVigilantes.agregar(nombre)
                    thread { catalogStorage.save() }
                    refreshVigilantesList()
                    Toast.makeText(this, "Vigilante agregado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Vehiculos ────────────────────────────────────────────────────────────

    private fun showEditVehiculoDialog(vehiculo: TipoVehiculo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_vehiculo, null)
        dialogView.findViewById<TextView>(R.id.editCodigo).text = vehiculo.codigo.toString()
        val tipoInput = dialogView.findViewById<EditText>(R.id.editTipo)
        tipoInput.setText(vehiculo.tipo)

        AlertDialog.Builder(this)
            .setTitle("Editar Vehiculo")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoTipo = tipoInput.text.toString().trim()
                if (nuevoTipo.isNotEmpty()) {
                    val idx = CatalogoVehiculos.vehiculos.indexOfFirst { it.codigo == vehiculo.codigo }
                    if (idx >= 0) {
                        CatalogoVehiculos.vehiculos[idx] = vehiculo.copy(tipo = nuevoTipo)
                        thread { catalogStorage.save() }
                        refreshVehiculosList()
                        Toast.makeText(this, "Vehiculo actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddVehiculoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_vehiculo, null)
        val nextCodigo = (CatalogoVehiculos.vehiculos.maxOfOrNull { it.codigo } ?: 0) + 1
        dialogView.findViewById<TextView>(R.id.editCodigo).text = nextCodigo.toString()
        val tipoInput = dialogView.findViewById<EditText>(R.id.editTipo)

        AlertDialog.Builder(this)
            .setTitle("Agregar Vehiculo")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val tipo = tipoInput.text.toString().trim()
                if (tipo.isNotEmpty()) {
                    CatalogoVehiculos.agregar(tipo)
                    thread { catalogStorage.save() }
                    refreshVehiculosList()
                    Toast.makeText(this, "Vehiculo agregado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Medios de Pago ───────────────────────────────────────────────────────

    private fun showEditMedioPagoDialog(medioPago: MedioPago) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_medio_pago, null)
        dialogView.findViewById<TextView>(R.id.editCodigo).text = medioPago.codigo.toString()
        val tipoInput = dialogView.findViewById<EditText>(R.id.editTipo)
        val checkComputa = dialogView.findViewById<CheckBox>(R.id.checkComputa)
        tipoInput.setText(medioPago.tipo)
        checkComputa.isChecked = medioPago.computa

        AlertDialog.Builder(this)
            .setTitle("Editar Medio de Pago")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoTipo = tipoInput.text.toString().trim()
                if (nuevoTipo.isNotEmpty()) {
                    val idx = CatalogoMediosPago.mediosPago.indexOfFirst { it.codigo == medioPago.codigo }
                    if (idx >= 0) {
                        CatalogoMediosPago.mediosPago[idx] = medioPago.copy(
                            tipo = nuevoTipo,
                            computa = checkComputa.isChecked
                        )
                        thread { catalogStorage.save() }
                        refreshMediosPagoList()
                        Toast.makeText(this, "Medio de pago actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddMedioPagoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_medio_pago, null)
        val nextCodigo = (CatalogoMediosPago.mediosPago.maxOfOrNull { it.codigo } ?: 0) + 1
        dialogView.findViewById<TextView>(R.id.editCodigo).text = nextCodigo.toString()
        val tipoInput = dialogView.findViewById<EditText>(R.id.editTipo)
        val checkComputa = dialogView.findViewById<CheckBox>(R.id.checkComputa)

        AlertDialog.Builder(this)
            .setTitle("Agregar Medio de Pago")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val tipo = tipoInput.text.toString().trim()
                if (tipo.isNotEmpty()) {
                    CatalogoMediosPago.agregar(tipo, checkComputa.isChecked)
                    thread { catalogStorage.save() }
                    refreshMediosPagoList()
                    Toast.makeText(this, "Medio de pago agregado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
