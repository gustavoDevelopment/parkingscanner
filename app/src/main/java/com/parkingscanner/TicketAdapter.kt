package com.parkingscanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import com.parkingscanner.domain.model.CatalogoMediosPago
import com.parkingscanner.domain.model.Ticket

private fun parseColombianAmount(raw: String): Double {
    val cleaned = raw.replace("$", "").replace("@", "").replace("S", "").trim()
    if (cleaned.isEmpty()) return 0.0
    return if (cleaned.contains(",")) {
        cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    } else {
        cleaned.replace(".", "").toDoubleOrNull() ?: 0.0
    }
}

private fun effectiveAmount(total: String, tiempo: String): Pair<Double, Boolean> {
    val fromTotal = parseColombianAmount(total)
    val hours = Regex("\\d+").find(tiempo)?.value?.toDoubleOrNull() ?: 0.0
    val fromTiempo = hours * 1000.0
    return when {
        fromTotal >= 1000.0 -> Pair(fromTotal, false)
        fromTiempo > 0.0 -> Pair(fromTiempo, true)
        fromTotal > 0.0 -> Pair(fromTotal, false)
        else -> Pair(0.0, false)
    }
}

class TicketAdapter(
    private val context: Context,
    private val tickets: List<Ticket>,
    private val onDeleteClick: (String) -> Unit,
    private val onItemClick: (Ticket) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = tickets.size
    override fun getItem(position: Int): Ticket = tickets[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.ticket_list_item, parent, false)

        val ticket = tickets[position]
        val medioPago = CatalogoMediosPago.obtenerPorCodigo(ticket.medioPagoCodigo)

        view.findViewById<TextView>(R.id.ticketBoleta).text =
            if (ticket.boleta.isNotEmpty()) "Boleta: ${ticket.boleta}" else "Sin boleta"

        val totalView = view.findViewById<TextView>(R.id.ticketTotal)
        val noSumaView = view.findViewById<TextView>(R.id.ticketNoSumaLabel)
        val isQr = medioPago?.tipo?.contains("qr", ignoreCase = true) == true
        val copFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
        val (amount, _) = effectiveAmount(ticket.total, ticket.tiempo)
        if (amount > 0.0) {
            totalView.text = "$${copFormat.format(amount)}"
            totalView.setTextColor(
                if (isQr) android.graphics.Color.parseColor("#2196F3")
                else android.graphics.Color.parseColor("#2E7D32")
            )
            totalView.visibility = View.VISIBLE
        } else {
            totalView.text = "Sin total"
            totalView.setTextColor(android.graphics.Color.parseColor("#FF5722"))
            totalView.visibility = View.VISIBLE
        }
        noSumaView.visibility = if (isQr) View.VISIBLE else View.GONE

        val placaVehiculoView = view.findViewById<TextView>(R.id.ticketPlacaVehiculo)
        val placaVehiculoParts = mutableListOf<String>()
        if (ticket.placa.isNotEmpty()) placaVehiculoParts.add("Placa: ${ticket.placa}")
        if (ticket.tipoVehiculo.isNotEmpty()) placaVehiculoParts.add(ticket.tipoVehiculo)
        if (placaVehiculoParts.isNotEmpty()) {
            placaVehiculoView.text = placaVehiculoParts.joinToString("  |  ")
            placaVehiculoView.visibility = View.VISIBLE
        } else {
            placaVehiculoView.visibility = View.GONE
        }

        val medioPagoView = view.findViewById<TextView>(R.id.ticketMedioPago)
        if (ticket.medioPagoCodigo != 0 && medioPago != null) {
            medioPagoView.text = medioPago.tipo
            medioPagoView.visibility = View.VISIBLE
        } else {
            medioPagoView.visibility = View.GONE
        }

        val ingresoView = view.findViewById<TextView>(R.id.ticketVigilanteIngreso)
        if (ticket.vigilanteIngreso.isNotEmpty()) {
            ingresoView.text = "Entrada: ${ticket.vigilanteIngreso}"
            ingresoView.visibility = View.VISIBLE
        } else {
            ingresoView.visibility = View.GONE
        }

        val salidaView = view.findViewById<TextView>(R.id.ticketVigilanteSalida)
        if (ticket.vigilanteSalida.isNotEmpty()) {
            salidaView.text = "Salida: ${ticket.vigilanteSalida}"
            salidaView.visibility = View.VISIBLE
        } else {
            salidaView.visibility = View.GONE
        }

        view.setOnClickListener { onItemClick(ticket) }
        view.findViewById<ImageButton>(R.id.deleteTicketButton).setOnClickListener {
            onDeleteClick(ticket.id)
        }

        return view
    }
}
