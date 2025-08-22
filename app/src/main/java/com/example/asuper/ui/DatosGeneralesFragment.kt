package com.example.asuper.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.asuper.R
import com.example.asuper.databinding.FragmentDatosGeneralesBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DatosGeneralesFragment : Fragment() {

    private var _binding: FragmentDatosGeneralesBinding? = null
    private val binding get() = _binding!!
    private val vm: FormularioViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatosGeneralesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAnterior.isEnabled = false
        
        // Configurar el campo de Centro de trabajo como fijo
        configurarCampoCentroTrabajo()
        
        // Configurar el campo de fecha con DatePicker
        configurarCampoFecha()
        
        // Configurar el campo de cuadrilla como dropdown multiselección
        configurarCampoCuadrilla()
        
        // Configurar el campo de especialidad como dropdown
        configurarCampoEspecialidad()
        
        // Configurar validación y comportamiento de Enter para todos los campos
        configurarCampos()
        
        // Cargar datos guardados si existen
        cargarDatosGuardados()
        
        // Habilitar el botón Siguiente inicialmente
        binding.btnSiguiente.isEnabled = true
        
        binding.btnSiguiente.setOnClickListener {
            if (validarCampos()) {
                // Obtener las cuadrillas seleccionadas
                val cuadrillasSeleccionadas = obtenerCuadrillasSeleccionadas()
                
                vm.setDatosGenerales(
                    binding.inputCentroTrabajo.text?.toString().orEmpty(),
                    binding.inputInstalacion.text?.toString().orEmpty(),
                    binding.inputFecha.text?.toString().orEmpty(),
                    binding.inputActividad.text?.toString().orEmpty(),
                    cuadrillasSeleccionadas,
                    binding.inputResponsable.text?.toString().orEmpty(),
                    binding.inputIntegrantes.text?.toString().orEmpty(),
                    binding.inputSupervisor.text?.toString().orEmpty(),
                    binding.inputEspecialidad.text?.toString().orEmpty()
                )
                findNavController().navigate(R.id.action_datos_to_seccionA)
            }
        }
    }

    private fun configurarCampoCentroTrabajo() {
        // Establecer el valor predeterminado
        binding.inputCentroTrabajo.setText("Zona de Transmisión Monclova - Sabinas")
        // Deshabilitar la edición
        binding.inputCentroTrabajo.isEnabled = false
    }
    
    private fun configurarCampoFecha() {
        // Establecer la fecha actual por defecto
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fechaActual = formatoFecha.format(Date())
        binding.inputFecha.setText(fechaActual)
        
        // Deshabilitar teclado para el campo de fecha
        binding.inputFecha.inputType = InputType.TYPE_NULL
        
        // Configurar el DatePicker al hacer clic en el campo
        binding.inputFecha.setOnClickListener {
            mostrarSelectorFecha()
        }
    }
    
    private fun mostrarSelectorFecha() {
        val calendario = Calendar.getInstance()
        val anio = calendario.get(Calendar.YEAR)
        val mes = calendario.get(Calendar.MONTH)
        val dia = calendario.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            val fechaSeleccionada = Calendar.getInstance()
            fechaSeleccionada.set(year, month, dayOfMonth)
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.inputFecha.setText(formatoFecha.format(fechaSeleccionada.time))
            // No validamos automáticamente al seleccionar fecha
        }, anio, mes, dia).show()
    }
    
    private fun configurarCampoCuadrilla() {
        val opciones = arrayOf(
            "PD0H0_COM01",
            "PD0H0_COM02",
            "PD0H0_CTR01",
            "PD0H0_CTR02",
            "PD0H0_LTS01",
            "PD0H0_LTS02",
            "PD0H0_PRO01",
            "PD0H0_PRO02",
            "PD0H0_SES01",
            "PD0H0_SES02"
        )
        
        // Lista para almacenar las cuadrillas seleccionadas
        val cuadrillasSeleccionadas = mutableListOf<String>()
        
        // Cargar selecciones previas si existen
        val datosGuardados = vm.state.value
        if (datosGuardados.cuadrillas.isNotEmpty()) {
            cuadrillasSeleccionadas.addAll(datosGuardados.cuadrillas)
        }
        
        // Crear adaptador para selección múltiple
        val adapter = MultiSelectCuadrillaAdapter(requireContext(), opciones, cuadrillasSeleccionadas)
        
        // Configurar el campo para mostrar las selecciones
        binding.inputCuadrilla.setAdapter(adapter)
        actualizarTextoCuadrillas(cuadrillasSeleccionadas)
        
        // Mostrar el diálogo de selección al hacer clic
        binding.inputCuadrilla.setOnClickListener {
            mostrarDialogoSeleccionCuadrillas(opciones, cuadrillasSeleccionadas)
        }
    }
    
    private fun actualizarTextoCuadrillas(cuadrillasSeleccionadas: List<String>) {
        if (cuadrillasSeleccionadas.isEmpty()) {
            binding.inputCuadrilla.setText("", false)
        } else {
            binding.inputCuadrilla.setText(cuadrillasSeleccionadas.joinToString(", "), false)
        }
    }
    
    private fun mostrarDialogoSeleccionCuadrillas(opciones: Array<String>, cuadrillasSeleccionadas: MutableList<String>) {
        val seleccionesIniciales = BooleanArray(opciones.size) { i -> cuadrillasSeleccionadas.contains(opciones[i]) }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar Cuadrillas")
            .setMultiChoiceItems(opciones, seleccionesIniciales) { _, index, isChecked ->
                if (isChecked) {
                    cuadrillasSeleccionadas.add(opciones[index])
                } else {
                    cuadrillasSeleccionadas.remove(opciones[index])
                }
            }
            .setPositiveButton("Aceptar") { _, _ ->
                actualizarTextoCuadrillas(cuadrillasSeleccionadas)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun configurarCampoEspecialidad() {
        val opciones = arrayOf(
            "Control",
            "Comunicaciones",
            "Protección y Medición",
            "Subestaciones y Líneas",
            "Control de Gestión",
            "Superintendencia"
        )
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, opciones)
        (binding.inputEspecialidad as? AutoCompleteTextView)?.setAdapter(adapter)
        
        // Cargar selección previa si existe
        val datosGuardados = vm.state.value
        if (datosGuardados.especialidad.isNotBlank()) {
            binding.inputEspecialidad.setText(datosGuardados.especialidad)
        }
    }
    
    private fun configurarCampos() {
        // Lista de todos los campos de texto y sus layouts
        val campos = listOf<Pair<TextView, TextInputLayout>>(
            binding.inputCentroTrabajo to binding.root.findViewById<TextInputLayout>(R.id.inputCentroTrabajoLayout),
            binding.inputInstalacion to binding.root.findViewById<TextInputLayout>(R.id.inputInstalacionLayout),
            binding.inputFecha to binding.root.findViewById<TextInputLayout>(R.id.inputFechaLayout),
            binding.inputActividad to binding.root.findViewById<TextInputLayout>(R.id.inputActividadLayout),
            binding.inputCuadrilla to binding.root.findViewById<TextInputLayout>(R.id.inputCuadrillaLayout),
            binding.inputResponsable to binding.root.findViewById<TextInputLayout>(R.id.inputResponsableLayout),
            binding.inputIntegrantes to binding.root.findViewById<TextInputLayout>(R.id.inputIntegrantesLayout),
            binding.inputSupervisor to binding.root.findViewById<TextInputLayout>(R.id.inputSupervisorLayout),
            binding.inputEspecialidad to binding.root.findViewById<TextInputLayout>(R.id.inputEspecialidadLayout)
        )
        
        // Limpiar errores inicialmente
        campos.forEach { (_, layout) ->
            layout.error = null
        }
        
        // Configurar cada campo
        campos.forEachIndexed { index, (campo, layout) ->
            // Evitar saltos de línea si no es AutoCompleteTextView
            if (campo !is AutoCompleteTextView) {
                campo.inputType = campo.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            
            // Validar solo cuando el usuario interactúe con el campo
            var usuarioInteractuo = false
            
            campo.addTextChangedListener {
                if (usuarioInteractuo) {
                    validarCampo(campo, layout)
                }
            }
            
            campo.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    usuarioInteractuo = true
                    validarCampo(campo, layout)
                }
            }
            
            // Configurar acción de Enter para pasar al siguiente campo
            campo.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || 
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    // Si no es el último campo, pasar al siguiente
                    if (index < campos.size - 1) {
                        campos[index + 1].first.requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
    
    private fun validarCampo(campo: TextView, layout: TextInputLayout): Boolean {
        val texto = campo.text?.toString().orEmpty()
        val esValido = texto.isNotBlank()
        
        if (!esValido) {
            layout.error = getString(R.string.campos_obligatorios)
        } else {
            layout.error = null
        }
        
        return esValido
    }
    
    private fun validarCuadrillas(layout: TextInputLayout): Boolean {
        val cuadrillasSeleccionadas = obtenerCuadrillasSeleccionadas()
        val esValido = cuadrillasSeleccionadas.isNotEmpty()
        
        if (!esValido) {
            layout.error = getString(R.string.campos_obligatorios)
        } else {
            layout.error = null
        }
        
        return esValido
    }
    
    private fun obtenerCuadrillasSeleccionadas(): List<String> {
        val texto = binding.inputCuadrilla.text?.toString().orEmpty()
        if (texto.isBlank()) return emptyList()
        
        return texto.split(", ").filter { it.isNotBlank() }
    }
    
    private fun validarCampos(): Boolean {
        // Mostrar errores en todos los campos
        val centroTrabajoValido = validarCampo(binding.inputCentroTrabajo, binding.root.findViewById(R.id.inputCentroTrabajoLayout))
        val instalacionValido = validarCampo(binding.inputInstalacion, binding.root.findViewById(R.id.inputInstalacionLayout))
        val fechaValido = validarCampo(binding.inputFecha, binding.root.findViewById(R.id.inputFechaLayout))
        val actividadValido = validarCampo(binding.inputActividad, binding.root.findViewById(R.id.inputActividadLayout))
        val cuadrillaValido = validarCuadrillas(binding.root.findViewById(R.id.inputCuadrillaLayout))
        val responsableValido = validarCampo(binding.inputResponsable, binding.root.findViewById(R.id.inputResponsableLayout))
        val integrantesValido = validarCampo(binding.inputIntegrantes, binding.root.findViewById(R.id.inputIntegrantesLayout))
        val supervisorValido = validarCampo(binding.inputSupervisor, binding.root.findViewById(R.id.inputSupervisorLayout))
        val especialidadValido = validarCampo(binding.inputEspecialidad, binding.root.findViewById(R.id.inputEspecialidadLayout))
        
        val todosValidos = centroTrabajoValido && instalacionValido && fechaValido && actividadValido && 
                          cuadrillaValido && responsableValido && integrantesValido && supervisorValido && especialidadValido
        
        return todosValidos
    }
    
    private fun cargarDatosGuardados() {
        // Obtener datos del ViewModel si existen
        val datos = vm.state.value
        if (datos.centroTrabajo.isNotBlank()) binding.inputCentroTrabajo.setText(datos.centroTrabajo)
        if (datos.instalacion.isNotBlank()) binding.inputInstalacion.setText(datos.instalacion)
        if (datos.fechaSupervision.isNotBlank()) binding.inputFecha.setText(datos.fechaSupervision)
        if (datos.actividad.isNotBlank()) binding.inputActividad.setText(datos.actividad)
        // Las cuadrillas se cargan en el método configurarCampoCuadrilla()
        if (datos.responsableCuadrilla.isNotBlank()) binding.inputResponsable.setText(datos.responsableCuadrilla)
        if (datos.integrantesCuadrilla.isNotBlank()) binding.inputIntegrantes.setText(datos.integrantesCuadrilla)
        if (datos.supervisor.isNotBlank()) binding.inputSupervisor.setText(datos.supervisor)
        // La especialidad se carga en el método configurarCampoEspecialidad()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}