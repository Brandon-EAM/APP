package com.example.asuper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.NavDirections
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.asuper.R
import com.example.asuper.data.FormularioData
import com.example.asuper.data.SeccionConfig
import com.example.asuper.data.SeccionType
import com.example.asuper.databinding.FragmentSeccionBinding
import com.example.asuper.ui.adapters.SimpleImageAdapter
import com.example.asuper.utils.ClickDebouncer
import com.example.asuper.utils.NavControllerExt.safeNavigate
import com.example.asuper.utils.NavControllerExt.resetNavigationState
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class SeccionFragment : Fragment() {
    private var _binding: FragmentSeccionBinding? = null
    private val binding get() = _binding!!
    private val vm: FormularioViewModel by activityViewModels()
    private val args by navArgs<SeccionFragmentArgs>()
    
    // Debouncer para evitar múltiples clics rápidos
    private val debouncer = ClickDebouncer()

    private var tempCaptureUri: Uri? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            val current = vm.state.value.valueFor(seccion())
            val max = SeccionConfig.getMaxImagenes(seccion())
            val espacioDisponible = max - current.imagenesUris.size
            val aAgregar = uris.take(espacioDisponible)
            if (aAgregar.isNotEmpty()) {
                vm.updateSeccion(seccion(), addImagenes = aAgregar)
            }
            if (uris.size > espacioDisponible) {
                mostrarAlertaMaxImagenes(max)
            }
        }
    }

    private val captureImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCaptureUri != null) {
            val current = vm.state.value.valueFor(seccion())
            val max = SeccionConfig.getMaxImagenes(seccion())
            if (current.imagenesUris.size >= max) {
                mostrarAlertaMaxImagenes(max)
            } else {
                vm.updateSeccion(seccion(), addImagenes = listOf(tempCaptureUri!!))
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeccionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Título
        binding.tvTitulo.text = com.example.asuper.data.SeccionConfig.getTitulo(seccion())
        // Descripción
        binding.tvDescripcion.text = com.example.asuper.data.SeccionConfig.getDescripcion(seccion())
        
        // Inicializar switch y contenedores según estado actual
        val estadoInicial = vm.state.value.valueFor(seccion()).usarTexto
        binding.switchUsarTexto.isChecked = estadoInicial
        toggleEntrada(estadoInicial)
        
        // Mejorar la interactividad del switch con animación y feedback táctil
        binding.switchUsarTexto.apply {
            // Asegurar que el switch sea visible y clickeable
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            
            // Añadir efecto de ripple para feedback táctil
            isHapticFeedbackEnabled = true
            
            // Configurar listener para cambios
            setOnCheckedChangeListener { _, isChecked ->
                // Animar la transición entre modos
                val fadeOut = AlphaAnimation(1f, 0f).apply { duration = 150 }
                val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 150 }
                
                if (isChecked) {
                    // Cambiar a modo texto
                    binding.contenedorImagenes.startAnimation(fadeOut)
                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {}
                        override fun onAnimationRepeat(animation: Animation?) {}
                        override fun onAnimationEnd(animation: Animation?) {
                            toggleEntrada(true)
                            binding.contenedorTexto.startAnimation(fadeIn)
                        }
                    })
                } else {
                    // Cambiar a modo imagen
                    binding.contenedorTexto.startAnimation(fadeOut)
                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {}
                        override fun onAnimationRepeat(animation: Animation?) {}
                        override fun onAnimationEnd(animation: Animation?) {
                            toggleEntrada(false)
                            binding.contenedorImagenes.startAnimation(fadeIn)
                        }
                    })
                }
                
                // Actualizar el estado en el ViewModel
                vm.updateSeccion(seccion(), usarTexto = isChecked)
            }
        }
        
        // Registrar el listener de cambio de destino
        findNavController().addOnDestinationChangedListener(destinationChangedListener)
        
        binding.btnAnterior.setOnClickListener {
            // Aplicar debounce para evitar múltiples clics rápidos
            if (!debouncer.tryClick(500)) return@setOnClickListener
            
            // Deshabilitar botones durante la navegación
            binding.btnAnterior.isEnabled = false
            binding.btnSiguiente.isEnabled = false
            
            persistirTexto()
            navegarAnterior()
        }
        binding.btnSiguiente.setOnClickListener {
            // Aplicar debounce para evitar múltiples clics rápidos
            if (!debouncer.tryClick(500)) return@setOnClickListener
            
            // Deshabilitar botones durante la navegación
            binding.btnAnterior.isEnabled = false
            binding.btnSiguiente.isEnabled = false
            
            // Solo validamos cuando se presiona el botón Siguiente
            persistirTexto()
            navegarSiguiente()
        }

        // Botones de imagen y nota
        binding.btnAgregarImagen.setOnClickListener { mostrarOpcionesImagen() }
        // Configurar el botón de Nota
        binding.btnNota.setOnClickListener { toggleNotaVisibility() }
        // El botón btnLimpiarImagenes ha sido eliminado del layout
        
        // Configurar el campo de nota
        binding.inputNota.setText(vm.state.value.valueFor(seccion()).nota)
        binding.inputNota.addTextChangedListener {
            vm.updateSeccion(seccion(), nota = it.toString())
        }
        
        // Texto principal
        binding.inputTexto.setText(vm.state.value.valueFor(seccion()).textoPrincipal)

        // Configurar validación y comportamiento de Enter
        configurarCamposTexto()
        
        binding.inputTexto.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) vm.updateSeccion(seccion(), textoPrincipal = binding.inputTexto.text?.toString())
        }
        
        // Habilitar el botón Siguiente inicialmente
        binding.btnSiguiente.isEnabled = true

        // Recycler horizontal para imágenes
        binding.rvImagenes.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val adapter = SimpleImageAdapter(onRemove = { index -> vm.updateSeccion(seccion(), removeAt = index) })
        binding.rvImagenes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val sec = state.valueFor(seccion())
                    // Mantener switch y UI sincronizados con el estado
                    if (binding.switchUsarTexto.isChecked != sec.usarTexto) {
                        binding.switchUsarTexto.isChecked = sec.usarTexto
                    }
                    toggleEntrada(sec.usarTexto)
                    adapter.submitList(sec.imagenesUris)
                    
                    // Actualizar el campo de nota si ha cambiado
                    if (binding.inputNota.text.toString() != sec.nota) {
                        binding.inputNota.setText(sec.nota)
                    }
                    
                    // Mostrar el campo de nota si hay contenido
                    if (sec.nota.isNotBlank() && binding.inputNotaLayout.visibility == View.GONE) {
                        binding.inputNotaLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun FormularioData.valueFor(type: SeccionType) = when (type) {
        SeccionType.A -> this.seccionA
        SeccionType.B -> this.seccionB
        SeccionType.C -> this.seccionC
        SeccionType.D -> this.seccionD
        SeccionType.E -> this.seccionE
        SeccionType.F -> this.seccionF
        SeccionType.G -> this.seccionG
        SeccionType.H -> this.seccionH
        SeccionType.I -> this.seccionI
        SeccionType.J -> this.seccionJ
    }

    private fun toggleEntrada(usarTexto: Boolean) {
        binding.contenedorImagenes.visibility = if (usarTexto) View.GONE else View.VISIBLE
        binding.contenedorTexto.visibility = if (usarTexto) View.VISIBLE else View.GONE
        // No realizamos ninguna validación al cambiar entre modos
    }
    
    private fun toggleNotaVisibility() {
        // Alternar la visibilidad del campo de nota
        if (binding.inputNotaLayout.visibility == View.VISIBLE) {
            binding.inputNotaLayout.visibility = View.GONE
        } else {
            binding.inputNotaLayout.visibility = View.VISIBLE
            binding.inputNota.requestFocus()
        }
    }
    
    private fun configurarCamposTexto() {
        // Evitar saltos de línea en los campos de texto
        binding.inputTexto.inputType = binding.inputTexto.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        
        // Limpiar errores inicialmente
        binding.root.findViewById<TextInputLayout>(R.id.inputTextoLayout).error = null
        
        // Configurar acción de Enter para finalizar edición
        binding.inputTexto.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                binding.inputTexto.clearFocus()
                true
            } else {
                false
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
    
    private fun validarCampos(): Boolean {
        // No validamos ninguna sección, todas las secciones siempre son válidas
        binding.btnSiguiente.isEnabled = true
        return true
    }

    private fun persistirTexto() {
        vm.updateSeccion(
            seccion(),
            textoPrincipal = binding.inputTexto.text?.toString(),
            nota = binding.inputNota.text?.toString()
        )
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Seleccionar de galería", "Tomar foto")
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar imagen")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> solicitarYSeleccionar()
                    1 -> solicitarYCapturar()
                }
            }
            .show()
    }

    private fun mostrarAlertaMaxImagenes(max: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Límite de imágenes")
            .setMessage("Has alcanzado el máximo de $max imágenes para esta sección.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun solicitarYSeleccionar() {
        if (tienePermisoLectura()) {
            pickImages.launch("image/*")
        } else {
            solicitarPermisoLectura()
        }
    }

    private fun solicitarYCapturar() {
        if (tienePerisosCamara()) {
            val current = vm.state.value.valueFor(seccion())
            val max = SeccionConfig.getMaxImagenes(seccion())
            if (current.imagenesUris.size >= max) {
                mostrarAlertaMaxImagenes(max)
                return
            }
            val tempDir = File(requireContext().cacheDir, "temp_images")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File.createTempFile("capture_", ".jpg", tempDir)
            tempCaptureUri = FileProvider.getUriForFile(
                requireContext(),
                "com.example.asuper.fileprovider",
                tempFile
            )
            captureImage.launch(tempCaptureUri)
        } else {
            solicitarPermisosCamara()
        }
    }

    // Permisos
    private fun tienePerisosCamara(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun tienePermisoLectura(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickImages.launch("image/*")
    }

    private val requestCameraPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) solicitarYCapturar()
    }

    private fun solicitarPermisoLectura() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPerm.launch(perm)
    }

    private fun solicitarPermisosCamara() {
        requestCameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun seccion(): SeccionType = runCatching { SeccionType.valueOf(args.seccionType) }.getOrElse { SeccionType.A }

    private fun navegarAnterior() {
        val navController = findNavController()
        when (seccion()) {
            SeccionType.A -> navController.safeNavigate(R.id.action_seccionA_to_datos)
            SeccionType.B -> navController.safeNavigate(R.id.action_seccionB_to_seccionA)
            SeccionType.C -> navController.safeNavigate(R.id.action_seccionC_to_seccionB)
            SeccionType.D -> navController.safeNavigate(R.id.action_seccionD_to_seccionC)
            SeccionType.E -> navController.safeNavigate(R.id.action_seccionE_to_seccionD)
            SeccionType.F -> navController.safeNavigate(R.id.action_seccionF_to_seccionE)
            SeccionType.G -> navController.safeNavigate(R.id.action_seccionG_to_seccionF)
            SeccionType.H -> navController.safeNavigate(R.id.action_seccionH_to_seccionG)
            SeccionType.I -> navController.safeNavigate(R.id.action_seccionI_to_seccionH)
            SeccionType.J -> navController.safeNavigate(R.id.action_seccionJ_to_seccionI)
        }
    }

    private fun navegarSiguiente() {
        // Todas las secciones navegan sin validación
        val navController = findNavController()
        
        when (seccion()) {
            SeccionType.A -> navController.safeNavigate(R.id.action_seccionA_to_seccionB)
            SeccionType.B -> navController.safeNavigate(R.id.action_seccionB_to_seccionC)
            SeccionType.C -> navController.safeNavigate(R.id.action_seccionC_to_seccionD)
            SeccionType.D -> navController.safeNavigate(R.id.action_seccionD_to_seccionE)
            SeccionType.E -> navController.safeNavigate(R.id.action_seccionE_to_seccionF)
            SeccionType.F -> navController.safeNavigate(R.id.action_seccionF_to_seccionG)
            SeccionType.G -> navController.safeNavigate(R.id.action_seccionG_to_seccionH)
            SeccionType.H -> navController.safeNavigate(R.id.action_seccionH_to_seccionI)
            SeccionType.I -> navController.safeNavigate(R.id.action_seccionI_to_seccionJ)
            SeccionType.J -> navController.safeNavigate(R.id.action_seccionJ_to_preview)
        }
    }

    // Listener de cambio de destino
    private val destinationChangedListener = androidx.navigation.NavController.OnDestinationChangedListener { _, _, _ ->
        // Resetear el estado de navegación
        resetNavigationState()
        // Rehabilitar los botones
        binding.btnAnterior.isEnabled = true
        binding.btnSiguiente.isEnabled = true
    }
    
    override fun onDestroyView() {
        // Limpiar el listener de cambio de destino para evitar fugas de memoria
        findNavController().removeOnDestinationChangedListener(destinationChangedListener)
        super.onDestroyView()
        _binding = null
    }
}