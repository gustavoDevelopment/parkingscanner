package com.parkingscanner

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.parkingscanner.data.repository.GlobalTicketIndex
import org.json.JSONObject

class GlobalIndexActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_index)

        val globalIndex = GlobalTicketIndex(this)
        globalIndex.rebuild()
        val index = globalIndex.load()

        // Contar cuántas fechas tiene cada boleta
        val boletaScannerCount = mutableMapOf<Int, Int>()
        val entries = mutableListOf<Pair<Int, String>>()
        val keys = index.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val num = key.toIntOrNull() ?: continue
            val arr = index.getJSONArray(key)
            boletaScannerCount[num] = arr.length()
            for (i in 0 until arr.length()) {
                entries.add(num to arr.getString(i))
            }
        }
        entries.sortBy { it.first }

        val duplicateCount = boletaScannerCount.count { it.value > 1 }
        val statsText = "${entries.size} boletas indexadas" +
            if (duplicateCount > 0) "  ·  $duplicateCount duplicadas" else ""
        findViewById<TextView>(R.id.statsTotalTickets).text = statsText

        findViewById<TextView>(R.id.btnGlobalConsecutivos).setOnClickListener {
            showGlobalConsecutivosDialog(index)
        }

        val listView = findViewById<ListView>(R.id.ticketIndexList)
        listView.adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(this@GlobalIndexActivity)
                    .inflate(R.layout.global_index_item, parent, false)

                val (boleta, scanner) = entries[position]
                val isDuplicate = (boletaScannerCount[boleta] ?: 1) > 1

                view.findViewById<TextView>(R.id.itemBoleta).apply {
                    text = "$boleta"
                    setTextColor(if (isDuplicate) Color.parseColor("#E65100") else Color.parseColor("#1A1C1E"))
                }
                view.findViewById<TextView>(R.id.itemScanner).text = scanner
                view.findViewById<TextView>(R.id.itemDuplicateBadge).apply {
                    visibility = if (isDuplicate) View.VISIBLE else View.GONE
                    setBackgroundResource(R.drawable.chip_bg_dup)
                }
                return view
            }
        }
    }

    private fun showGlobalConsecutivosDialog(index: JSONObject) {
        val todas = mutableListOf<Int>()
        val keys = index.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            key.toIntOrNull()?.let { todas.add(it) }
        }
        todas.sort()

        if (todas.size < 2) {
            AlertDialog.Builder(this)
                .setTitle("Consecutivos globales")
                .setMessage("No hay suficientes boletas para analizar.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Rango [min, max] por fecha de recogida
        val scannerRanges = mutableMapOf<String, Pair<Int, Int>>()
        val keys2 = index.keys()
        while (keys2.hasNext()) {
            val key = keys2.next()
            val num = key.toIntOrNull() ?: continue
            val arr = index.getJSONArray(key)
            for (i in 0 until arr.length()) {
                val scanner = arr.getString(i)
                val cur = scannerRanges[scanner]
                scannerRanges[scanner] = if (cur == null) Pair(num, num)
                    else Pair(minOf(cur.first, num), maxOf(cur.second, num))
            }
        }

        fun inferScanner(boleta: Int): String {
            val candidates = scannerRanges.filter { (_, r) -> boleta in r.first..r.second }
            return when {
                candidates.size == 1 -> candidates.keys.first()
                candidates.size > 1 -> candidates.keys.joinToString(" / ")
                else -> scannerRanges.minByOrNull { (_, r) ->
                    minOf(Math.abs(boleta - r.first), Math.abs(boleta - r.second))
                }?.key ?: "?"
            }
        }

        // Algoritmo cluster
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
        val idxCluster = todas.indexOf(clusterStart)
        if (idxCluster > 0 && clusterStart - todas[idxCluster - 1] <= 10) {
            clusterStart = todas[idxCluster - 1]
        }

        val end = todas.last()
        val todasSet = todas.toHashSet()
        val faltantes = (clusterStart..end).filter { it !in todasSet }

        // Agrupar consecutivos en rangos con fecha inferida
        data class FaltanteRow(val label: String, val scanner: String)
        val rows = mutableListOf<FaltanteRow>()
        if (faltantes.isNotEmpty()) {
            var rangeStart = faltantes[0]
            var prev = faltantes[0]
            for (i in 1..faltantes.size) {
                val isLast = i == faltantes.size
                val broke = isLast || faltantes[i] != prev + 1
                if (broke) {
                    val label = if (rangeStart == prev) "$rangeStart" else "$rangeStart – $prev"
                    rows.add(FaltanteRow(label, inferScanner(rangeStart)))
                    if (!isLast) { rangeStart = faltantes[i]; prev = faltantes[i] }
                } else {
                    prev = faltantes[i]
                }
            }
        }

        // Construir vista de tabla
        val density = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        // Subtítulo rango
        container.addView(TextView(this).apply {
            text = "Rango analizado: $clusterStart → $end"
            textSize = 12f
            setTextColor(Color.parseColor("#44474F"))
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (12 * density).toInt())
        })

        if (rows.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "✅ No hay consecutivos faltantes en el rango."
                textSize = 14f
                setTextColor(Color.parseColor("#1B5E20"))
                setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            })
        } else {
            // Cabecera de tabla
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#D0D8E4"))
                setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())
            }
            header.addView(TextView(this).apply {
                text = "BOLETA"
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#44474F"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(TextView(this).apply {
                text = "FECHA PROBABLE"
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#44474F"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            container.addView(header)

            // Filas
            rows.forEachIndexed { idx, row ->
                val rowView = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F5F7FA"))
                    setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                }
                rowView.addView(TextView(this).apply {
                    text = row.label
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#1A1C1E"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                rowView.addView(TextView(this).apply {
                    text = row.scanner
                    textSize = 13f
                    setTextColor(Color.parseColor("#1565C0"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                })
                container.addView(rowView)
            }
        }

        val scroll = ScrollView(this).also { it.addView(container) }

        AlertDialog.Builder(this)
            .setTitle("Faltantes globales: ${faltantes.size}")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }
}
