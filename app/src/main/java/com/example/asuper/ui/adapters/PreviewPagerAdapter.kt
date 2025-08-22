package com.example.asuper.ui.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.asuper.R
import com.example.asuper.data.FormularioData
import com.example.asuper.data.SeccionConfig
import com.example.asuper.data.SeccionType
import com.example.asuper.utils.AppMetadata

class PreviewPagerAdapter(
    private var data: FormularioData,
    private val onEdit: (type: SeccionType?) -> Unit
) : RecyclerView.Adapter<PreviewPagerAdapter.VH>() {
    
    // Método privado para verificación interna - no visible en UI
    private fun getMetadataSignature(): String? {
        return try {
            // Referencia oculta a la firma del creador
            AppMetadata.getVerificationData()
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preview_page, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = 1 + SeccionType.values().size

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (position == 0) {
            holder.bindDatosGenerales(data)
            holder.btnEdit.setOnClickListener { onEdit(null) }
        } else {
            val type = SeccionType.values()[position - 1]
            holder.bindSeccion(type, data)
            holder.btnEdit.setOnClickListener { onEdit(type) }
        }
    }

    fun submitData(newData: FormularioData) {
        this.data = newData
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvSectionTitle)
        val alt: TextView = itemView.findViewById(R.id.tvAltText)
        val container: LinearLayout = itemView.findViewById(R.id.containerThumbs)
        val btnEdit: View = itemView.findViewById(R.id.btnEditar)

        fun bindDatosGenerales(data: FormularioData) {
            title.text = "Datos Generales"
            alt.text = buildString {
                append("Centro: ").append(data.centroTrabajo).append('\n')
                append("Instalación: ").append(data.instalacion).append('\n')
                append("Fecha: ").append(data.fechaSupervision).append('\n')
                append("Actividad: ").append(data.actividad).append('\n')
                append("Cuadrilla: ").append(data.cuadrillas.joinToString(", ")).append('\n')
                append("Responsable: ").append(data.responsableCuadrilla).append('\n')
                append("Integrantes: ").append(data.integrantesCuadrilla).append('\n')
                append("Supervisor: ").append(data.supervisor).append('\n')
                append("Especialidad: ").append(data.especialidad)
            }
            container.removeAllViews()
        }

        fun bindSeccion(type: SeccionType, data: FormularioData) {
            title.text = SeccionConfig.getTitulo(type)
            val seccion = when (type) {
                SeccionType.A -> data.seccionA
                SeccionType.B -> data.seccionB
                SeccionType.C -> data.seccionC
                SeccionType.D -> data.seccionD
                SeccionType.E -> data.seccionE
                SeccionType.F -> data.seccionF
                SeccionType.G -> data.seccionG
                SeccionType.H -> data.seccionH
                SeccionType.I -> data.seccionI
                SeccionType.J -> data.seccionJ
            }

            // Mostrar primero el texto descriptivo de la sección
            val descripcionSeccion = SeccionConfig.getDescripcion(type)
            
            // Luego mostrar el contenido del usuario
            val contenidoUsuario = when {
                seccion.usarTexto && seccion.textoPrincipal.isNotBlank() -> seccion.textoPrincipal
                !seccion.usarTexto && seccion.textoAlternativo.isNotBlank() -> "Descripción: ${seccion.textoAlternativo}"
                else -> ""
            }
            
            // Combinar ambos textos con un separador si hay contenido del usuario
            alt.text = if (contenidoUsuario.isNotBlank()) {
                "$descripcionSeccion\n\n$contenidoUsuario"
            } else {
                descripcionSeccion
            }
            
            // Configurar el espaciado de línea para mejor legibilidad
            alt.setLineSpacing(0f, 1.3f)

            container.removeAllViews()
            if (!seccion.usarTexto && seccion.imagenesUris.isNotEmpty()) {
                // Crear un contenedor con scroll horizontal para las imágenes
                val scrollContainer = HorizontalScrollView(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    tag = "scroll_container"
                    // Habilitar desplazamiento suave para mejor experiencia táctil
                    isHorizontalScrollBarEnabled = true
                    isSmoothScrollingEnabled = true
                }
                
                // Contenedor interno para las imágenes en fila horizontal
                val imagesContainer = LinearLayout(container.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    tag = "images_container"
                }
                
                scrollContainer.addView(imagesContainer)
                container.addView(scrollContainer)
                
                // Calcular el ancho para las imágenes (reducido en un 30%)
                val displayMetrics = container.context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val containerPadding = 32 // 16dp de padding a cada lado
                // Reducir el tamaño de la imagen en un 30% respecto al tamaño actual
                val imageWidth = ((screenWidth - containerPadding) * 0.7 * 0.7).toInt() // Reducción adicional para mostrar múltiples imágenes
                
                // Agregar cada imagen al contenedor horizontal
                seccion.imagenesUris.forEach { uriStr ->
                    val iv = ImageView(container.context)
                    // Usar ancho y altura proporcionales para el scroll horizontal
                    iv.layoutParams = LinearLayout.LayoutParams(
                        imageWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        // Añadir margen horizontal entre imágenes
                        setMargins(0, 0, 16, 0)
                    }
                    // Ajustar la imagen para mantener su proporción
                    iv.adjustViewBounds = true
                    iv.scaleType = ImageView.ScaleType.FIT_CENTER
                    
                    // Añadir un fondo con borde suave para mejorar la visualización
                    iv.setBackgroundResource(R.drawable.image_preview_background)
                    iv.setPadding(8, 8, 8, 8)
                    
                    // Cargar imagen con Glide optimizado para calidad
                    Glide.with(iv)
                        .load(uriStr)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        // No forzar un tamaño específico para mantener la proporción original
                        .into(iv)
                    
                    imagesContainer.addView(iv)
                }
            }
        }
    }
}