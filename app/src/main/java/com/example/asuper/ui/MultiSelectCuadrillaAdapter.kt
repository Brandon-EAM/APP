package com.example.asuper.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import com.example.asuper.R

/**
 * Adaptador personalizado para manejar la selección múltiple de cuadrillas
 * con casillas de verificación.
 */
class MultiSelectCuadrillaAdapter(
    context: Context,
    private val opciones: Array<String>,
    private val seleccionadas: MutableList<String>
) : ArrayAdapter<String>(context, R.layout.item_cuadrilla_checkbox, opciones) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_cuadrilla_checkbox, parent, false)

        val opcion = opciones[position]
        val checkBox = view.findViewById<CheckBox>(R.id.checkbox)
        val textView = view.findViewById<TextView>(R.id.text)

        textView.text = opcion
        checkBox.isChecked = seleccionadas.contains(opcion)

        // Manejar clics en el checkbox
        checkBox.setOnClickListener {
            if (checkBox.isChecked) {
                if (!seleccionadas.contains(opcion)) {
                    seleccionadas.add(opcion)
                }
            } else {
                seleccionadas.remove(opcion)
            }
        }

        // Manejar clics en toda la vista
        view.setOnClickListener {
            checkBox.isChecked = !checkBox.isChecked
            if (checkBox.isChecked) {
                if (!seleccionadas.contains(opcion)) {
                    seleccionadas.add(opcion)
                }
            } else {
                seleccionadas.remove(opcion)
            }
        }

        return view
    }
}