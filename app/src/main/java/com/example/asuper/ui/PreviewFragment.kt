package com.example.asuper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.example.asuper.databinding.FragmentPreviewBinding
import com.example.asuper.ui.adapters.PreviewPagerAdapter
import com.example.asuper.data.SeccionType
import androidx.navigation.fragment.findNavController
import com.example.asuper.R
import com.example.asuper.utils.PdfGenerator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PreviewFragment : Fragment() {
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val vm: FormularioViewModel by activityViewModels()

    private lateinit var adapter: PreviewPagerAdapter

    private val requestWritePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) generarPdf() else Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PreviewPagerAdapter(vm.state.value) { type ->
            // navegar a editar
            if (type == null) {
                findNavController().navigate(R.id.action_preview_to_datos)
            } else {
                val actionId = when (type) {
                    SeccionType.A -> R.id.action_preview_to_seccionA
                    SeccionType.B -> R.id.action_preview_to_seccionB
                    SeccionType.C -> R.id.action_preview_to_seccionC
                    SeccionType.D -> R.id.action_preview_to_seccionD
                    SeccionType.E -> R.id.action_preview_to_seccionE
                    SeccionType.F -> R.id.action_preview_to_seccionF
                    SeccionType.G -> R.id.action_preview_to_seccionG
                    SeccionType.H -> R.id.action_preview_to_seccionH
                    SeccionType.I -> R.id.action_preview_to_seccionI
                    SeccionType.J -> R.id.action_preview_to_seccionJ
                }
                findNavController().navigate(actionId)
            }
        }
        binding.viewPagerPreview.adapter = adapter

        // Para la versión 5.x del artefacto com.tbuonomo:dotsindicator, el paquete XML es com.tbuonomo.viewpagerdotsindicator
        // y el método correcto es attachTo(viewPager2)
        binding.dotsIndicator.attachTo(binding.viewPagerPreview)

        binding.viewPagerPreview.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Usar post para evitar requestLayout durante el layout
                binding.tvPageIndicator.post {
                    binding.tvPageIndicator.text = "${position + 1}/11"
                }
            }
        })

        // Collect StateFlow correctamente
        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collect { data ->
                adapter.submitData(data)
            }
        }

        // Configurar botón Generar PDF
        binding.btnGenerarPdf.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED) {
                    generarPdf()
                } else {
                    requestWritePerm.launch(perm)
                }
            } else {
                generarPdf()
            }
        }
        
        // Configurar botón Nuevo Reporte
        binding.btnNuevoReporte.setOnClickListener {
            mostrarDialogoConfirmacion()
        }
    }

    private fun generarPdf() {
        val data = vm.state.value
        val file = PdfGenerator(requireContext()).generatePdf(data)
        if (file != null) {
            Toast.makeText(requireContext(), "PDF guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Error al generar PDF", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Muestra un diálogo de confirmación para reiniciar el formulario
     */
    private fun mostrarDialogoConfirmacion() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Nuevo Reporte")
            .setMessage("¿Está seguro de que desea iniciar un nuevo reporte? Esto borrará todo el contenido actual.")
            .setPositiveButton("Aceptar") { _, _ ->
                // Reiniciar todos los datos del formulario
                vm.reiniciarFormulario()
                
                // Mostrar mensaje de confirmación
                Toast.makeText(requireContext(), "Se ha iniciado un nuevo reporte", Toast.LENGTH_SHORT).show()
                
                // Navegar a la primera pantalla (Datos Generales)
                findNavController().navigate(R.id.action_preview_to_datos)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}