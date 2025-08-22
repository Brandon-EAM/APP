package com.example.asuper.data

/**
 * Data class que contiene todos los datos del formulario CFE
 */
data class FormularioData(
    // Datos Generales (Pantalla 1)
    val centroTrabajo: String = "Zona de Transmisión Monclova - Sabinas",
    val instalacion: String = "",
    val fechaSupervision: String = "",
    val actividad: String = "",
    val cuadrillas: List<String> = emptyList(),
    val responsableCuadrilla: String = "",
    val integrantesCuadrilla: String = "",
    val supervisor: String = "",
    val especialidad: String = "",
    
    // Secciones A-J (Pantallas 2-11)
    val seccionA: SeccionData = SeccionData(),
    val seccionB: SeccionData = SeccionData(),
    val seccionC: SeccionData = SeccionData(),
    val seccionD: SeccionData = SeccionData(),
    val seccionE: SeccionData = SeccionData(),
    val seccionF: SeccionData = SeccionData(),
    val seccionG: SeccionData = SeccionData(),
    val seccionH: SeccionData = SeccionData(),
    val seccionI: SeccionData = SeccionData(),
    val seccionJ: SeccionData = SeccionData()
)

/**
 * Data class para cada sección A-J del formulario
 */
data class SeccionData(
    val usarTexto: Boolean = false,
    val textoPrincipal: String = "",
    val textoAlternativo: String = "",
    val imagenesUris: List<String> = emptyList(),
    val nota: String = "" // Campo para almacenar la nota que reemplazará la fecha en el footer del PDF
)

/**
 * Enum para identificar las secciones
 */
enum class SeccionType {
    A, B, C, D, E, F, G, H, I, J
}

/**
 * Configuración de límites de imágenes por sección
 */
object SeccionConfig {
    fun getMaxImagenes(seccion: SeccionType): Int {
        return when (seccion) {
            SeccionType.A, SeccionType.B, SeccionType.I -> 1
            SeccionType.C, SeccionType.D, SeccionType.J -> 2
            SeccionType.F -> 3
            SeccionType.G -> 4
            SeccionType.E -> 5
            SeccionType.H -> 4
        }
    }
    
    fun getTitulo(seccion: SeccionType): String {
        return when (seccion) {
            SeccionType.A -> "A: Controles por verificar"
            SeccionType.B -> "B: Orden de Trabajo"
            SeccionType.C -> "C: Planeación en campo"
            SeccionType.D -> "D: Licencia de trabajo"
            SeccionType.E -> "E: Evidencia de uso de equipo"
            SeccionType.F -> "F: Delimitación de área"
            SeccionType.G -> "G: Uso de equipo de protección"
            SeccionType.H -> "H: Acciones que salvan vidas"
            SeccionType.I -> "I: Selfie del supervisor"
            SeccionType.J -> "J: Diagramas unifilares"
        }
    }
    
    fun getDescripcion(seccion: SeccionType): String {
        return when (seccion) {
            SeccionType.A -> "a) Planeación de Oficina (no aplica bajo emergencia)"
            SeccionType.B -> "B) Orden de Trabajo"
            SeccionType.C -> "c) Planeación en campo (ARS-RIM)"
            SeccionType.D -> "d) Solo si aplica, deberá contener imagen del registro y licencia del trabajo programado o licencia de emergencia."
            SeccionType.E -> "e) Imágenes en las que se observe que el uso de:\n" +
                    "i. Uso de sistemas anticaídas arnés y línea de vida (con amortiguador o retráctil entre otros medios probados e igualmente eficaces).\n" +
                    "ii. Uso de sistemas de posicionamiento (bandola, cordón de posicionamientos ajustable, entre otros medios probados y eficaces).\n" +
                    "iii. Uso de dispositivos de ascenso y descenso controlado como lo son: línea de vida vertical con absorbedor de energía, ganchos de gran apertura, entre otros medios probados y eficaces.\n" +
                    "iv. Para trabajos en canastillas, además, deberá: colocarse el freno de mano y otros dispositivos de frenado, se colocarán topes en las ruedas traseras en ambos sentidos."
            SeccionType.F -> "f) Imágenes en que se observe el uso de:\n" +
                    "Delimitación del área de trabajo, Uso de avisos preventivos, acordonamientos, barreras normalizadas, conos, líneas de restricción de movimiento, Respetar distancias de seguridad."
            SeccionType.G -> "g) Imágenes en que se observe el uso de equipo, herramienta y del equipo de protección personal."
            SeccionType.H -> "h) Si aplica ARS–RIM, adjuntar imágenes de evidencia implementando Acciones que Salvan Vida (AQSV):\n" +
                    "Apertura visible, bloqueos mecánicos, eléctricos, ausencia de potencial, instalación de puesta a tierra que correspondan."
            SeccionType.I -> "i) Autorretrato (selfie) del supervisor ejecutando la supervisión en el lugar del trabajo, actividad o maniobra."
            SeccionType.J -> "j) Si aplica de conformidad con ARS–RIM, adjuntar imágenes de diagramas unifilares eléctricos para ubicar la dirección eléctrica de trabajos, así como diagramas eléctricos y listado de cables del equipo o equipos a intervenir."
        }
    }
}