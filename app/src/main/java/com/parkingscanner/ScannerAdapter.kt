package com.parkingscanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import com.parkingscanner.domain.model.ScannerSummary
import java.text.NumberFormat
import java.util.Locale

class ScannerAdapter(
    private val context: Context,
    private val summaries: List<ScannerSummary>,
    private val onItemClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val onEditClick: (String) -> Unit = {}
) : BaseAdapter() {

    private val currencyFormat = NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    override fun getCount(): Int = summaries.size
    override fun getItem(position: Int): ScannerSummary = summaries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.scanner_list_item, parent, false)

        val summary = summaries[position]
        view.findViewById<TextView>(R.id.scannerName).text = summary.name
        view.findViewById<TextView>(R.id.ticketCount).text =
            "${summary.ticketCount} boletas  •  Total: $${currencyFormat.format(summary.computaTotal)}"

        val descView = view.findViewById<TextView>(R.id.scannerDescription)
        if (summary.description.isNotEmpty()) {
            descView.text = summary.description
            descView.visibility = View.VISIBLE
        } else {
            descView.visibility = View.GONE
        }

        view.findViewById<ImageButton>(R.id.btnEditScanner).setOnClickListener {
            onEditClick(summary.name)
        }
        view.findViewById<ImageButton>(R.id.btnDeleteScanner).setOnClickListener {
            onDeleteClick(summary.name)
        }

        view.setOnClickListener {
            onItemClick(summary.name)
        }

        return view
    }
}
