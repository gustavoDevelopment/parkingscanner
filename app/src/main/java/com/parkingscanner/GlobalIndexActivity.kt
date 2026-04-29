package com.parkingscanner

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.parkingscanner.data.repository.GlobalTicketIndex

class GlobalIndexActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_index)

        val globalIndex = GlobalTicketIndex(this)
        globalIndex.rebuild()
        val index = globalIndex.load()

        // Count how many scanners each boleta appears in
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
        val statsText = "Total boletas indexadas: ${entries.size}" +
            if (duplicateCount > 0) "  |  Duplicadas: $duplicateCount" else ""
        findViewById<TextView>(R.id.statsTotalTickets).text = statsText

        val listView = findViewById<ListView>(R.id.ticketIndexList)
        listView.adapter = object : ArrayAdapter<Pair<Int, String>>(
            this, android.R.layout.simple_list_item_1, entries
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val (boleta, scanner) = entries[position]
                view.text = "$boleta  →  $scanner"
                val isDuplicate = (boletaScannerCount[boleta] ?: 1) > 1
                view.setTextColor(if (isDuplicate) Color.parseColor("#FF9800") else Color.parseColor("#212121"))
                return view
            }
        }
    }
}
