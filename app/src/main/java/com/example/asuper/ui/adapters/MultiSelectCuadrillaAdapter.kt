package com.example.asuper.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import com.example.asuper.R

class MultiSelectCuadrillaAdapter(
    context: Context,
    private val opciones: Array<String>,
    private val seleccionados: MutableList<String>
) : ArrayAdapter<String>(context, R.layout.item_cuadrilla_checkbox, opciones) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_cuadrilla_checkbox, parent, false)

        val checkBox = view as CheckBox
        val opcion = opciones[position]
        
        checkBox.text = opcion
        checkBox.isChecked = seleccionados.contains(opcion)
        
        checkBox.setOnClickListener {
            if (checkBox.isChecked) {
                if (!seleccionados.contains(opcion)) {
                    seleccionados.add(opcion)
                }
            } else {
                seleccionados.remove(opcion)
            }
        }
        
        return view
    }
}