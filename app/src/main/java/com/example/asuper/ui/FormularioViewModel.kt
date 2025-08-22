package com.example.asuper.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.asuper.data.FormularioData
import com.example.asuper.data.SeccionData
import com.example.asuper.data.SeccionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FormularioViewModel : ViewModel() {
    private val _state = MutableStateFlow(FormularioData())
    val state: StateFlow<FormularioData> = _state
    
    /**
     * Reinicia todos los datos del formulario a sus valores por defecto
     */
    fun reiniciarFormulario() {
        _state.value = FormularioData()
    }

    fun setDatosGenerales(
        centroTrabajo: String,
        instalacion: String,
        fecha: String,
        actividad: String,
        cuadrillas: List<String>,
        responsable: String,
        integrantes: String,
        supervisor: String,
        especialidad: String
    ) {
        _state.update {
            it.copy(
                centroTrabajo = centroTrabajo,
                instalacion = instalacion,
                fechaSupervision = fecha,
                actividad = actividad,
                cuadrillas = cuadrillas,
                responsableCuadrilla = responsable,
                integrantesCuadrilla = integrantes,
                supervisor = supervisor,
                especialidad = especialidad
            )
        }
    }

    fun updateSeccion(
        seccion: SeccionType,
        usarTexto: Boolean? = null,
        textoPrincipal: String? = null,
        textoAlternativo: String? = null,
        addImagenes: List<Uri>? = null,
        removeAt: Int? = null,
        nota: String? = null
    ) {
        fun SeccionData.applyChanges(): SeccionData {
            var imgs = imagenesUris
            if (addImagenes != null && addImagenes.isNotEmpty()) {
                imgs = (imagenesUris + addImagenes.map { it.toString() }).distinct()
            }
            if (removeAt != null && removeAt in imagenesUris.indices) {
                imgs = imagenesUris.toMutableList().also { it.removeAt(removeAt) }
            }
            return this.copy(
                usarTexto = usarTexto ?: this.usarTexto,
                textoPrincipal = textoPrincipal ?: this.textoPrincipal,
                textoAlternativo = textoAlternativo ?: this.textoAlternativo,
                imagenesUris = imgs,
                nota = nota ?: this.nota
            )
        }
        _state.update { current ->
            when (seccion) {
                SeccionType.A -> current.copy(seccionA = current.seccionA.applyChanges())
                SeccionType.B -> current.copy(seccionB = current.seccionB.applyChanges())
                SeccionType.C -> current.copy(seccionC = current.seccionC.applyChanges())
                SeccionType.D -> current.copy(seccionD = current.seccionD.applyChanges())
                SeccionType.E -> current.copy(seccionE = current.seccionE.applyChanges())
                SeccionType.F -> current.copy(seccionF = current.seccionF.applyChanges())
                SeccionType.G -> current.copy(seccionG = current.seccionG.applyChanges())
                SeccionType.H -> current.copy(seccionH = current.seccionH.applyChanges())
                SeccionType.I -> current.copy(seccionI = current.seccionI.applyChanges())
                SeccionType.J -> current.copy(seccionJ = current.seccionJ.applyChanges())
            }
        }
    }
}