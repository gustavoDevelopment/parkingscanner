package com.parkingscanner

import android.content.Context
import android.graphics.Color
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

private fun parseTiempoToAmount(tiempo: String): Double {
    if (tiempo.isBlank()) return 0.0
    val lower = tiempo.lowercase()
    val numbers = Regex("\\d+").findAll(tiempo).map { it.value.toDouble() }.toList()
    if (numbers.isEmpty()) return 0.0
    return when {
        lower.contains("minuto") && !lower.contains("hora") -> numbers[0] / 60.0 * 1000.0
        lower.contains("hora") && lower.contains("minuto") && numbers.size >= 2 -> (numbers[0] + numbers[1] / 60.0) * 1000.0
        else -> numbers[0] * 1000.0
    }
}

private fun effectiveAmount(total: String, tiempo: String): Double {
    val fromTotal = parseColombianAmount(total)
    val fromTiempo = parseTiempoToAmount(tiempo)
    return when {
        fromTotal > 0.0 -> fromTotal
        fromTiempo > 0.0 -> fromTiempo
        else -> 0.0
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
        val isQr = medioPago?.tipo?.contains("qr", ignoreCase = true) == true
        val isAnulado = medioPago?.computa == false

        view.findViewById<TextView>(R.id.ticketBoleta).text =
            if (ticket.boleta.isNotEmpty()) "Boleta: ${ticket.boleta}" else "Sin boleta"

        val totalView = view.findViewById<TextView>(R.id.ticketTotal)
        val noSumaView = view.findViewById<TextView>(R.id.ticketNoSumaLabel)
        val copFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
        val amount = effectiveAmount(ticket.total, ticket.tiempo)
        if (amount > 0.0) {
            totalView.text = "$${copFormat.format(amount)}"
            totalView.setTextColor(when {
                isAnulado -> Color.parseColor("#FF9800")
                isQr -> Color.parseColor("#0D47A1")
                else -> Color.parseColor("#1B5E20")
            })
            totalView.visibility = View.VISIBLE
        } else {
            totalView.text = "Sin total"
            totalView.setTextColor(Color.parseColor("#B3261E"))
            totalView.visibility = View.VISIBLE
        }
        noSumaView.visibility = if (isQr || isAnulado) View.VISIBLE else View.GONE
        if (isAnulado) noSumaView.text = "Anulado"
        else if (isQr) noSumaView.text = "No suma al efectivo"

        val placaVehiculoView = view.findViewById<TextView>(R.id.ticketPlacaVehiculo)
        val parts = mutableListOf<String>()
        if (ticket.placa.isNotEmpty()) parts.add("Placa: ${ticket.placa}")
        if (ticket.tipoVehiculo.isNotEmpty()) parts.add(ticket.tipoVehiculo)
        if (parts.isNotEmpty()) {
            placaVehiculoView.text = parts.joinToString("  ·  ")
            placaVehiculoView.visibility = View.VISIBLE
        } else {
            placaVehiculoView.visibility = View.GONE
        }

        val medioPagoView = view.findViewById<TextView>(R.id.ticketMedioPago)
        if (ticket.medioPagoCodigo != 0 && medioPago != null) {
            medioPagoView.text = medioPago.tipo.uppercase()
            medioPagoView.visibility = View.VISIBLE
            val (bgDrawable, textColor) = when {
                isAnulado -> Pair(R.drawable.chip_bg_anulado, context.getColor(R.color.chip_anulado_text))
                isQr -> Pair(R.drawable.chip_bg_qr, context.getColor(R.color.chip_qr_text))
                else -> Pair(R.drawable.chip_bg_efectivo, context.getColor(R.color.chip_efectivo_text))
            }
            medioPagoView.setBackgroundResource(bgDrawable)
            medioPagoView.setTextColor(textColor)
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
