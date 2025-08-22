package com.example.asuper.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.asuper.R
import com.example.asuper.data.FormularioData
import com.example.asuper.data.SeccionConfig
import com.example.asuper.data.SeccionData
import com.example.asuper.data.SeccionType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfGenerator(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 612f
        private const val PAGE_HEIGHT = 792f
        private const val MARGIN_HORIZONTAL = 56.69f
        private const val MARGIN_TOP = 30f // Reducido para que el contenido comience más arriba
        private const val MARGIN_BOTTOM = 20f // Reducido para que el footer esté más abajo
        private const val CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN_HORIZONTAL)
        private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM
        private const val CFE_GREEN = 0xFF00A651.toInt()
        private const val CFE_BLUE = 0xFF0066CC.toInt()
        private const val DARK_GRAY = 0xFF333333.toInt()
    }

    fun generatePdf(data: FormularioData): File? {
        return try {
            val document = PdfDocument()

            createDatosGeneralesPage(document, data)
            SeccionType.values().forEach { seccionType ->
                createSeccionPage(document, data, seccionType)
            }

            val filename = buildFileName(data)
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage: guardar en Downloads/CFE usando MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CFE")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                val legacyFile = createLegacyDownloadsFile(filename)
                FileOutputStream(legacyFile)
            } ?: return null

            document.writeTo(outputStream)
            outputStream.close()
            document.close()

            // Devolver File aproximado para mostrar ruta en Toast en Q+: construir ruta relativa
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CFE/${filename}")
            } else {
                createLegacyDownloadsFile(filename)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createDatosGeneralesPage(document: PdfDocument, data: FormularioData) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        var yPosition = MARGIN_TOP
        yPosition = drawHeader(canvas, "REPORTE DE SUPERVISIÓN EFECTIVA", yPosition)
        yPosition += 15f // Reducido de 20f a 15f para disminuir el espacio

        yPosition = drawDataSection(canvas, "Datos Generales", yPosition)
        yPosition += 10f

        var rowY = yPosition
        rowY = drawDataRow(canvas, "Centro de Trabajo:", data.centroTrabajo, rowY)
        rowY = drawDataRow(canvas, "Instalación:", data.instalacion, rowY)
        rowY = drawDataRow(canvas, "Fecha de Supervisión:", data.fechaSupervision, rowY)
        rowY = drawDataRow(canvas, "Actividad:", data.actividad, rowY)
        rowY = drawDataRow(canvas, "Cuadrilla:", data.cuadrillas.joinToString(", "), rowY)
        rowY = drawDataRow(canvas, "Responsable de Cuadrilla:", data.responsableCuadrilla, rowY)
        rowY = drawDataRow(canvas, "Integrantes de Cuadrilla:", data.integrantesCuadrilla, rowY)
        rowY = drawDataRow(canvas, "Supervisor:", data.supervisor, rowY)
        rowY = drawDataRow(canvas, "Especialidad:", data.especialidad, rowY)

        drawFooter(canvas, data.fechaSupervision)
        document.finishPage(page)
    }

    private fun createSeccionPage(document: PdfDocument, data: FormularioData, seccionType: SeccionType) {
        // Obtener los datos de la sección
        val seccionData = when (seccionType) {
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
        
        // Si no hay imágenes, crear una página normal
        if (seccionData.imagenesUris.isEmpty()) {
            createSingleSeccionPage(document, data, seccionType, seccionData, emptyList())
            return
        }
        
        // Determinar si es una sola imagen horizontal para la primera página
        var isLandscapePage = false
        if (!seccionData.usarTexto && seccionData.imagenesUris.size == 1) {
            try {
                val uri = Uri.parse(seccionData.imagenesUris[0])
                val bitmap = loadAndProcessImage(uri, true, false)
                if (bitmap != null && bitmap.width > bitmap.height) {
                    isLandscapePage = true
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } catch (_: Exception) { }
        }
        
        // Distribuir imágenes según las reglas
        val imagenesUris = seccionData.imagenesUris
        
        when {
            // Si hay 1 o 2 imágenes, todas van en la primera página
            imagenesUris.size <= 2 -> {
                createSingleSeccionPage(document, data, seccionType, seccionData, imagenesUris)
            }
            
            // Si hay 3 imágenes: 1 en primera página, 2 en segunda
            imagenesUris.size == 3 -> {
                // Primera página con 1 imagen
                createSingleSeccionPage(document, data, seccionType, seccionData, imagenesUris.take(1))
                // Segunda página solo con imágenes (sin texto descriptivo)
                createAdditionalImagesPage(document, data, seccionType, imagenesUris.drop(1))
            }
            
            // Si hay 4 imágenes: 2 en primera página, 2 en segunda
            imagenesUris.size == 4 -> {
                // Primera página con 2 imágenes
                createSingleSeccionPage(document, data, seccionType, seccionData, imagenesUris.take(2))
                // Segunda página solo con imágenes (sin texto descriptivo)
                createAdditionalImagesPage(document, data, seccionType, imagenesUris.drop(2))
            }
            
            // Si hay 5 o más imágenes: 2 en primera página, resto en páginas adicionales (máx 3 por página)
            else -> {
                // Primera página con 2 imágenes
                createSingleSeccionPage(document, data, seccionType, seccionData, imagenesUris.take(2))
                
                // Distribuir el resto en páginas adicionales (máx 3 por página)
                val remainingImages = imagenesUris.drop(2)
                remainingImages.chunked(3).forEach { chunk ->
                    createAdditionalImagesPage(document, data, seccionType, chunk)
                }
            }
        }
    }
    
    /**
     * Crea una página de sección con encabezado, título, descripción y las imágenes especificadas
     */
    private fun createSingleSeccionPage(
        document: PdfDocument, 
        data: FormularioData, 
        seccionType: SeccionType, 
        seccionData: SeccionData,
        imagesToShow: List<String>
    ) {
        // Determinar si es una sola imagen horizontal
        var isLandscapePage = false
        if (!seccionData.usarTexto && imagesToShow.size == 1) {
            try {
                val uri = Uri.parse(imagesToShow[0])
                val bitmap = loadAndProcessImage(uri, true, false)
                if (bitmap != null && bitmap.width > bitmap.height) {
                    isLandscapePage = true
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } catch (_: Exception) { }
        }
        
        // Crear la página con la orientación adecuada
        val pageWidth = if (isLandscapePage) PAGE_HEIGHT.toInt() else PAGE_WIDTH.toInt()
        val pageHeight = if (isLandscapePage) PAGE_WIDTH.toInt() else PAGE_HEIGHT.toInt()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, seccionType.ordinal + 2).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        var yPosition = MARGIN_TOP
        yPosition = drawHeader(canvas, "REPORTE DE SUPERVISIÓN EFECTIVA", yPosition)
        yPosition += 15f

        val seccionTitle = SeccionConfig.getTitulo(seccionType)
        yPosition = drawDataSection(canvas, seccionTitle, yPosition)
        yPosition += 12f
        
        // Añadir el texto descriptivo de la sección antes del contenido
        val descripcionSeccion = SeccionConfig.getDescripcion(seccionType)
        yPosition = drawTextContent(canvas, descripcionSeccion, yPosition)
        yPosition += 10f

        if (seccionData.usarTexto && seccionData.textoPrincipal.isNotEmpty()) {
            yPosition = drawTextContent(canvas, seccionData.textoPrincipal, yPosition)
        } else if (!seccionData.usarTexto && imagesToShow.isNotEmpty()) {
            yPosition = drawImages(canvas, imagesToShow, yPosition, isLandscapePage)
            if (seccionData.textoAlternativo.isNotEmpty()) {
                yPosition += 5f
                yPosition = drawTextContent(canvas, "Descripción: ${seccionData.textoAlternativo}", yPosition)
            }
        } else {
            yPosition = drawTextContent(canvas, "Sin contenido capturado", yPosition)
        }

        // Obtener la nota de la sección si existe
        val nota = seccionData.nota
        drawFooter(canvas, data.fechaSupervision, isLandscapePage, nota)
        
        document.finishPage(page)
    }
    
    /**
     * Crea una página adicional solo con imágenes (sin texto descriptivo)
     */
    private fun createAdditionalImagesPage(
        document: PdfDocument, 
        data: FormularioData, 
        seccionType: SeccionType, 
        imagesToShow: List<String>
    ) {
        if (imagesToShow.isEmpty()) return
        
        // Determinar si es una sola imagen horizontal
        var isLandscapePage = false
        if (imagesToShow.size == 1) {
            try {
                val uri = Uri.parse(imagesToShow[0])
                val bitmap = loadAndProcessImage(uri, true, false)
                if (bitmap != null && bitmap.width > bitmap.height) {
                    isLandscapePage = true
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } catch (_: Exception) { }
        }
        
        // Crear la página con la orientación adecuada
        val pageWidth = if (isLandscapePage) PAGE_HEIGHT.toInt() else PAGE_WIDTH.toInt()
        val pageHeight = if (isLandscapePage) PAGE_WIDTH.toInt() else PAGE_HEIGHT.toInt()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, seccionType.ordinal + 2).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        var yPosition = MARGIN_TOP
        yPosition = drawHeader(canvas, "REPORTE DE SUPERVISIÓN EFECTIVA", yPosition)
        yPosition += 15f

        val seccionTitle = SeccionConfig.getTitulo(seccionType) + " (Continuación)"
        yPosition = drawDataSection(canvas, seccionTitle, yPosition)
        yPosition += 12f
        
        // Dibujar solo las imágenes
        yPosition = drawImages(canvas, imagesToShow, yPosition, isLandscapePage)

        // Obtener la nota de la sección si existe
        val seccionData = when (seccionType) {
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
        val nota = seccionData.nota
        drawFooter(canvas, data.fechaSupervision, isLandscapePage, nota)
        
        document.finishPage(page)
    }

    private fun drawHeader(canvas: Canvas, title: String, startY: Float): Float {
        val paint = Paint().apply { 
            color = CFE_GREEN
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        // Determinar el ancho de página actual (para manejar correctamente páginas horizontales)
        val currentPageWidth = if (canvas.width > canvas.height) canvas.width.toFloat() else PAGE_WIDTH
        
        // Logo: posicionado a la derecha con proporciones correctas y tamaño reducido al 40%
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logocfe)
        if (logo != null) {
            // Calcular proporciones originales del logo
            val logoRatio = logo.width.toFloat() / logo.height.toFloat()
            // Reducir al 40% del tamaño original (60f * 0.4 = 24f)
            val logoHeight = 24f
            val logoWidth = logoHeight * logoRatio
            
            // Posicionar a la derecha según el ancho de página actual
            val logoX = currentPageWidth - MARGIN_HORIZONTAL - logoWidth
            val dstRect = RectF(logoX, startY, logoX + logoWidth, startY + logoHeight)
            
            // Escalar manteniendo proporciones
            val scaled = Bitmap.createScaledBitmap(logo, dstRect.width().toInt(), dstRect.height().toInt(), true)
            canvas.drawBitmap(scaled, dstRect.left, dstRect.top, null)
            if (!logo.isRecycled) logo.recycle()
            if (!scaled.isRecycled) scaled.recycle()
        } else {
            val logoPaint = Paint().apply { color = CFE_GREEN; style = Paint.Style.FILL }
            // Reducir al 40% del tamaño original (80f * 0.4 = 32f, 60f * 0.4 = 24f)
            val logoWidth = 32f
            val logoHeight = 24f
            val logoX = currentPageWidth - MARGIN_HORIZONTAL - logoWidth
            canvas.drawRect(logoX, startY, logoX + logoWidth, startY + logoHeight, logoPaint)
        }
        
        // Ajustar posición vertical del título para alinearlo con el logo reducido
        canvas.drawText(title, MARGIN_HORIZONTAL, startY + 18f, paint)
        val linePaint = Paint().apply { color = CFE_BLUE; strokeWidth = 2f }
        // Ajustar posición de la línea para que esté más cerca del logo y título
        canvas.drawLine(MARGIN_HORIZONTAL, startY + 35f, currentPageWidth - MARGIN_HORIZONTAL, startY + 35f, linePaint)
        return startY + 45f
    }

    private fun drawDataSection(canvas: Canvas, title: String, startY: Float): Float {
        val paint = Paint().apply { color = CFE_BLUE; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val bgPaint = Paint().apply { color = 0xFFE6F3FF.toInt(); style = Paint.Style.FILL }
        
        // Determinar el ancho de página actual (para manejar correctamente páginas horizontales)
        val currentPageWidth = if (canvas.width > canvas.height) canvas.width.toFloat() else PAGE_WIDTH
        
        canvas.drawRect(MARGIN_HORIZONTAL, startY, currentPageWidth - MARGIN_HORIZONTAL, startY + 25f, bgPaint)
        val borderPaint = Paint().apply { color = CFE_BLUE; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(MARGIN_HORIZONTAL, startY, currentPageWidth - MARGIN_HORIZONTAL, startY + 25f, borderPaint)
        canvas.drawText(title, MARGIN_HORIZONTAL + 10f, startY + 17f, paint)
        return startY + 35f
    }

    private fun drawDataRow(canvas: Canvas, label: String, value: String, startY: Float): Float {
        val labelPaint = Paint().apply { color = DARK_GRAY; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val valuePaint = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        
        // Aplicar ajuste automático de texto para todos los campos
        return drawMultilineDataRow(canvas, label, value, startY, labelPaint, valuePaint)
    }
    
    private fun drawMultilineDataRow(canvas: Canvas, label: String, value: String, startY: Float, 
                                    labelPaint: Paint, valuePaint: Paint): Float {
        // Calcular el ancho disponible para el valor
        val valueStartX = MARGIN_HORIZONTAL + 150f
        val availableWidth = PAGE_WIDTH - MARGIN_HORIZONTAL - valueStartX - 10f // Margen adicional para evitar que el texto toque el borde
        
        // Dividir el texto en líneas según el ancho disponible
        val lines = splitTextIntoLines(value, availableWidth, valuePaint)
        
        // Altura de cada línea de texto
        val lineHeight = 18f
        
        // Altura mínima de la fila (para mantener consistencia con filas de una sola línea)
        val minRowHeight = 20f
        
        // Altura total de la fila basada en el número de líneas (con un mínimo)
        val totalRowHeight = maxOf(minRowHeight, lineHeight * lines.size)
        
        // Dibujar el fondo alternado
        if ((startY.toInt() / 20) % 2 == 0) {
            val bgPaint = Paint().apply { color = 0xFFF8F8F8.toInt(); style = Paint.Style.FILL }
            canvas.drawRect(MARGIN_HORIZONTAL, startY, PAGE_WIDTH - MARGIN_HORIZONTAL, startY + totalRowHeight, bgPaint)
        }
        
        // Dibujar bordes de la celda
        val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas.drawRect(MARGIN_HORIZONTAL, startY, PAGE_WIDTH - MARGIN_HORIZONTAL, startY + totalRowHeight, borderPaint)
        canvas.drawLine(MARGIN_HORIZONTAL + 140f, startY, MARGIN_HORIZONTAL + 140f, startY + totalRowHeight, borderPaint)
        
        // Dibujar la etiqueta (solo en la primera línea)
        canvas.drawText(label, MARGIN_HORIZONTAL + 10f, startY + 13f, labelPaint)
        
        // Dibujar cada línea del valor
        lines.forEachIndexed { index, line ->
            val lineY = startY + 13f + (index * lineHeight)
            canvas.drawText(line, valueStartX, lineY, valuePaint)
        }
        
        // Devolver la nueva posición Y después de esta fila
        return startY + totalRowHeight
    }
    
    private fun splitTextIntoLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        
        // Si el texto está vacío o cabe en una línea, devolverlo directamente
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) {
            return listOf(text)
        }
        
        // Dividir el texto en palabras
        val words = text.split(" ")
        var currentLine = StringBuilder()
        
        for (word in words) {
            val wordWidth = paint.measureText(word)
            val spaceWidth = paint.measureText(" ")
            
            // Si la línea está vacía, agregar la palabra sin importar su ancho
            if (currentLine.isEmpty()) {
                // Si la palabra es más ancha que el espacio disponible, dividirla
                if (wordWidth > maxWidth) {
                    var remainingWord = word
                    while (remainingWord.isNotEmpty()) {
                        var i = 1
                        while (i <= remainingWord.length) {
                            val part = remainingWord.substring(0, i)
                            if (paint.measureText(part) > maxWidth) {
                                break
                            }
                            i++
                        }
                        // Agregar la parte que cabe
                        val part = remainingWord.substring(0, maxOf(1, i - 1))
                        lines.add(part)
                        // Continuar con el resto de la palabra
                        remainingWord = remainingWord.substring(part.length)
                    }
                } else {
                    currentLine.append(word)
                }
            } 
            // Si la palabra cabe en la línea actual con el espacio
            else if (paint.measureText(currentLine.toString() + " " + word) <= maxWidth) {
                currentLine.append(" ").append(word)
            } 
            // Si no cabe, comenzar una nueva línea
            else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
                
                // Si la palabra es más ancha que el espacio disponible, dividirla
                if (wordWidth > maxWidth) {
                    var remainingWord = word
                    while (remainingWord.isNotEmpty()) {
                        var i = 1
                        while (i <= remainingWord.length) {
                            val part = remainingWord.substring(0, i)
                            if (paint.measureText(part) > maxWidth) {
                                break
                            }
                            i++
                        }
                        // Agregar la parte que cabe
                        val part = remainingWord.substring(0, maxOf(1, i - 1))
                        lines.add(part)
                        // Continuar con el resto de la palabra
                        remainingWord = remainingWord.substring(part.length)
                    }
                } else {
                    currentLine.append(word)
                }
            }
        }
        
        // Agregar la última línea si no está vacía
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return lines
    }

    private fun drawTextContent(canvas: Canvas, text: String, startY: Float): Float {
        val paint = Paint().apply { 
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        
        // Determinar el ancho de página actual (para manejar correctamente páginas horizontales)
        val currentPageWidth = if (canvas.width > canvas.height) canvas.width.toFloat() else PAGE_WIDTH
        val currentContentWidth = currentPageWidth - (2 * MARGIN_HORIZONTAL)
        
        // Ancho disponible para el texto (con margen adicional de 10 a cada lado)
        val availableWidth = currentContentWidth - 20f
        
        // Dividir el texto en párrafos por saltos de línea explícitos
        val paragraphs = text.split("\n")
        var currentY = startY
        
        // Procesar cada párrafo
        paragraphs.forEach { paragraph ->
            if (paragraph.isEmpty()) {
                // Párrafo vacío, solo agregar espacio
                currentY += 16f
                return@forEach
            }
            
            // Dividir el párrafo en palabras
            val words = paragraph.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()
            
            // Construir líneas que se ajusten al ancho disponible
            words.forEach { word ->
                val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                val testWidth = paint.measureText(testLine)
                
                if (testWidth <= availableWidth) {
                    // La palabra cabe en la línea actual
                    currentLine = StringBuilder(testLine)
                } else {
                    // La palabra no cabe, agregar la línea actual y comenzar una nueva
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    } else {
                        // La palabra es demasiado larga para una línea, dividirla
                        var remainingWord = word
                        while (remainingWord.isNotEmpty()) {
                            var i = 1
                            while (i <= remainingWord.length) {
                                val part = remainingWord.substring(0, i)
                                if (paint.measureText(part) > availableWidth) {
                                    break
                                }
                                i++
                            }
                            // Agregar la parte que cabe
                            val part = remainingWord.substring(0, maxOf(1, i - 1))
                            lines.add(part)
                            // Continuar con el resto de la palabra
                            remainingWord = remainingWord.substring(part.length)
                        }
                    }
                }
            }
            
            // Agregar la última línea si no está vacía
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
            
            // Dibujar las líneas del párrafo
            lines.forEach { line ->
                if (currentY < PAGE_HEIGHT - MARGIN_BOTTOM - 50f) {
                    canvas.drawText(line, MARGIN_HORIZONTAL + 10f, currentY, paint)
                    currentY += 16f  // Interlineado de 1.3 aproximadamente (12pt * 1.3 ≈ 16pt)
                }
            }
            
            // Agregar espacio adicional después de cada párrafo
            currentY += 4f
        }
        
        return currentY + 10f
    }

    private fun drawImages(canvas: Canvas, imageUris: List<String>, startY: Float, isLandscapePage: Boolean = false): Float {
        if (imageUris.isEmpty()) return startY
        
        var currentY = startY
        val processedImages = mutableListOf<Bitmap>()
        
        // Determinar si es una sola imagen para aplicar el escalado especial
        val isSingleImage = imageUris.size == 1
        
        // Paso 1: Cargar y procesar todas las imágenes
        imageUris.forEach { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val bitmap = loadAndProcessImage(uri) ?: return@forEach
                // Aplicar escalado especial (30% más grande) si es una sola imagen
                val scaledBitmap = scaleImageToContent(bitmap, isSingleImage, isLandscapePage)
                val finalBitmap = compressToJpegAndDecode(scaledBitmap, 95) // Usamos calidad 95 para mejor nitidez
                processedImages.add(finalBitmap)
                
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
            } catch (_: Exception) { }
        }
        
        // Si no hay imágenes procesadas, salir
        if (processedImages.isEmpty()) return startY
        
        // Obtener el ancho de página según la orientación
        val pageWidth = if (isLandscapePage) PAGE_HEIGHT else PAGE_WIDTH
        
        // Paso 2: Determinar la disposición según la cantidad de imágenes
        when (processedImages.size) {
            1 -> {
                // Una sola imagen: centrada
                val bitmap = processedImages[0]
                
                // Calcular la posición para centrar la imagen en la página
                val x = (pageWidth - bitmap.width) / 2
                
                // En modo paisaje, aseguramos que la imagen esté correctamente posicionada
                // después de la rotación del canvas
                if (isLandscapePage) {
                    // Ajustamos la posición vertical para dejar espacio para el header y título
                    val adjustedY = currentY + 5f // Reducido de 10f a 5f
                    canvas.drawBitmap(bitmap, x, adjustedY, null)
                    currentY += bitmap.height + 10f // Reducido de 20f a 10f
                } else {
                    // En modo retrato, mantenemos el comportamiento original
                    canvas.drawBitmap(bitmap, x, currentY, null)
                    currentY += bitmap.height + 10f // Espaciado vertical uniforme de 10f
                }
            }
            2 -> {
                // Dos imágenes: una al lado de la otra, respetando proporciones
                val spacing = 10f // Espaciado uniforme de 10f entre imágenes
                val maxHeight = 250f // Reducimos la altura máxima para evitar saturación
                
                // Calcular escalas para mantener proporciones
                val scales = processedImages.map { 
                    minOf(1f, maxHeight / it.height) 
                }
                
                // Calcular ancho total después del escalado
                val totalWidth = processedImages.mapIndexed { index, bitmap ->
                    (bitmap.width * scales[index]).toInt()
                }.sum() + spacing
                
                var x = (pageWidth - totalWidth) / 2
                
                // Dibujar imágenes escaladas
                processedImages.forEachIndexed { index, bitmap ->
                    val scale = scales[index]
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()
                    
                    // Crear bitmap escalado manteniendo proporciones
                    val scaledBitmap = if (scale < 1f) {
                        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                    } else {
                        bitmap
                    }
                    
                    canvas.drawBitmap(scaledBitmap, x, currentY, null)
                    x += scaledWidth + spacing
                    
                    if (scaledBitmap != bitmap) scaledBitmap.recycle()
                }
                
                currentY += maxHeight + 10f // Espaciado vertical uniforme de 10f
            }
            3 -> {
                // Tres imágenes: 2 arriba, 1 abajo centrada, respetando proporciones
                val spacing = 10f // Espaciado uniforme de 10f entre imágenes
                val maxRowHeight = 250f // Reducimos la altura máxima para evitar saturación
                
                // Primera fila (2 imágenes)
                val row1 = processedImages.take(2)
                val scales1 = row1.map { minOf(1f, maxRowHeight / it.height) }
                
                val row1Width = row1.mapIndexed { index, bitmap ->
                    (bitmap.width * scales1[index]).toInt()
                }.sum() + spacing
                
                var x = (pageWidth - row1Width) / 2
                
                // Dibujar primera fila
                row1.forEachIndexed { index, bitmap ->
                    val scale = scales1[index]
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()
                    
                    val scaledBitmap = if (scale < 1f) {
                        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                    } else {
                        bitmap
                    }
                    
                    canvas.drawBitmap(scaledBitmap, x, currentY, null)
                    x += scaledWidth + spacing
                    
                    if (scaledBitmap != bitmap) scaledBitmap.recycle()
                }
                
                currentY += maxRowHeight + 10f // Espaciado vertical uniforme de 10f
                
                // Segunda fila (1 imagen centrada)
                val bitmap = processedImages[2]
                val scale2 = minOf(1f, maxRowHeight / bitmap.height)
                val scaledWidth = (bitmap.width * scale2).toInt()
                val scaledHeight = (bitmap.height * scale2).toInt()
                
                val scaledBitmap = if (scale2 < 1f) {
                    Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                } else {
                    bitmap
                }
                
                x = (pageWidth - scaledWidth) / 2
                canvas.drawBitmap(scaledBitmap, x, currentY, null)
                currentY += scaledHeight + 10f // Espaciado vertical uniforme de 10f
                
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
            }
            4 -> {
                // Cuatro imágenes: 2x2, respetando proporciones
                val spacing = 10f // Espaciado uniforme de 10f entre imágenes
                val maxRowHeight = 250f // Reducimos la altura máxima para evitar saturación
                val rows = processedImages.chunked(2)
                
                rows.forEachIndexed { rowIndex, rowBitmaps ->
                    val rowScales = rowBitmaps.map { minOf(1f, maxRowHeight / it.height) }
                    
                    val rowWidth = rowBitmaps.mapIndexed { index, bitmap ->
                        (bitmap.width * rowScales[index]).toInt()
                    }.sum() + spacing
                    
                    var x = (pageWidth - rowWidth) / 2
                    
                    rowBitmaps.forEachIndexed { index, bitmap ->
                        val scale = rowScales[index]
                        val scaledWidth = (bitmap.width * scale).toInt()
                        val scaledHeight = (bitmap.height * scale).toInt()
                        
                        val scaledBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                        } else {
                            bitmap
                        }
                        
                        canvas.drawBitmap(scaledBitmap, x, currentY, null)
                        x += scaledWidth + spacing
                        
                        if (scaledBitmap != bitmap) scaledBitmap.recycle()
                    }
                    
                    currentY += maxRowHeight + 10f // Espaciado vertical uniforme de 10f
                }
            }
            else -> {
                // Más de 4 imágenes (no debería ocurrir según los límites)
                // Mostrar en filas de 2, respetando proporciones
                val spacing = 10f // Espaciado uniforme de 10f entre imágenes
                val maxRowHeight = 250f // Reducimos la altura máxima para evitar saturación
                val rows = processedImages.chunked(2)
                
                rows.forEach { rowBitmaps ->
                    val rowScales = rowBitmaps.map { minOf(1f, maxRowHeight / it.height) }
                    
                    val rowWidth = rowBitmaps.mapIndexed { index, bitmap ->
                        (bitmap.width * rowScales[index]).toInt()
                    }.sum() + (rowBitmaps.size - 1) * spacing
                    
                    var x = (pageWidth - rowWidth) / 2
                    
                    rowBitmaps.forEachIndexed { index, bitmap ->
                        val scale = rowScales[index]
                        val scaledWidth = (bitmap.width * scale).toInt()
                        val scaledHeight = (bitmap.height * scale).toInt()
                        
                        val scaledBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                        } else {
                            bitmap
                        }
                        
                        canvas.drawBitmap(scaledBitmap, x, currentY, null)
                        x += scaledWidth + spacing
                        
                        if (scaledBitmap != bitmap) scaledBitmap.recycle()
                    }
                    
                    currentY += maxRowHeight + 10f // Espaciado vertical uniforme de 10f
                }
            }
        }
        
        // Liberar memoria de las imágenes procesadas
        processedImages.forEach { if (!it.isRecycled) it.recycle() }
        
        return currentY + 10f
    }

    private fun loadAndProcessImage(uri: Uri, singleImage: Boolean = false, isLandscapePage: Boolean = false): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val original = BitmapFactory.decodeStream(inputStream)
                applyExifRotation(original, uri)
            }
        } catch (_: Exception) { null }
    }

    private fun applyExifRotation(bitmap: Bitmap?, uri: Uri): Bitmap? {
        if (bitmap == null) return null
        return try {
            context.contentResolver.openInputStream(uri).use { exifStream ->
                val exif = ExifInterface(exifStream!!)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            }
        } catch (_: Exception) { bitmap }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleImageToContent(bitmap: Bitmap, singleImage: Boolean = false, isLandscapePage: Boolean = false): Bitmap {
        // Ajustar el ancho máximo según la orientación de la página
        val maxWidth = if (isLandscapePage) {
            // En modo paisaje, usamos el 85% del espacio disponible para dejar margen
            (PAGE_HEIGHT - 2 * MARGIN_HORIZONTAL - 40f).toInt() // Usar altura de página como ancho en modo paisaje
        } else {
            (CONTENT_WIDTH - 20f).toInt()
        }
        
        // Si es una sola imagen, aumentamos el tamaño máximo en un 30%
        val maxHeight = if (singleImage) {
            if (isLandscapePage) {
                // En modo paisaje con una sola imagen, usamos el 80% del espacio disponible
                (PAGE_WIDTH - 2 * MARGIN_HORIZONTAL - 60f).toInt() * 0.8f // 80% del ancho disponible en modo paisaje
            } else {
                390 // 300 * 1.3 = 390 (30% más grande que el estándar)
            }
        } else {
            250 // Altura estándar para múltiples imágenes
        }
        
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        
        // Si es una sola imagen, aplicamos un factor de escala adicional
        val finalScale = if (singleImage) {
            if (isLandscapePage) {
                // En modo paisaje, usamos un factor de escala del 95% para mantener proporciones
                scale * 0.95f
            } else {
                // En modo retrato, mantenemos el factor de escala del 130%
                scale * 1.3f
            }
        } else {
            scale
        }
        
        val newWidth = (bitmap.width * finalScale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * finalScale).toInt().coerceAtLeast(1)
        
        return if (newWidth == bitmap.width && newHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
    }

    private fun compressToJpegAndDecode(bitmap: Bitmap, quality: Int): Bitmap {
        val baos = ByteArrayOutputStream()
        // Aumentamos la calidad de compresión a 95 para todas las imágenes
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun drawFooter(canvas: Canvas, fecha: String, isLandscapePage: Boolean = false, nota: String = "") {
        val paint = Paint().apply { color = Color.GRAY; textSize = 10f; isAntiAlias = true }
        val footerText = if (nota.isNotBlank()) {
            // No añadimos "Nota:" aquí porque lo manejaremos por separado
            nota
        } else {
            "Reporte de Supervisión Efectiva – CFE – $fecha"
        }
        
        // Determinar el ancho de página actual (para manejar correctamente páginas horizontales)
        val currentPageWidth = if (canvas.width > canvas.height) canvas.width.toFloat() else PAGE_WIDTH
        val currentPageHeight = if (canvas.width > canvas.height) canvas.height.toFloat() else PAGE_HEIGHT
        val currentContentWidth = currentPageWidth - (2 * MARGIN_HORIZONTAL)
        
        // Si es una nota, implementar salto de línea automático
        if (nota.isNotBlank()) {
            // Crear paint para "Nota:" en rojo y negritas
            val notaPrefixPaint = Paint().apply { 
                color = Color.RED
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true 
            }
            
            // Paint para el texto de la nota (negro)
            val notaTextPaint = Paint().apply { 
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true 
            }
            
            // Prefijo "Nota:" que se añadirá al inicio
            val notaPrefix = "Nota: "
            val notaPrefixWidth = notaPrefixPaint.measureText(notaPrefix)
            
            // Ancho disponible para el texto (con margen adicional de 10 a cada lado)
            val availableWidth = currentContentWidth - 20f
            
            // Dividir el texto en palabras
            val words = nota.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()
            
            // Construir líneas que se ajusten al ancho disponible
            words.forEach { word ->
                val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                val lineWidth = paint.measureText(testLine)
                
                if (lineWidth <= availableWidth) {
                    currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                } else {
                    // La línea actual ya está completa, guardarla y comenzar una nueva
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    } else {
                        // La palabra es más larga que el ancho disponible, dividirla
                        currentLine.append(word)
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder()
                    }
                }
            }
            
            // Agregar la última línea si no está vacía
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
            
            // Calcular la altura total del footer basado en el número de líneas
            val lineHeight = 14f // Altura de línea para el texto del footer
            val totalFooterHeight = lines.size * lineHeight
            
            // Ajustar la posición vertical del footer según el número de líneas
            // Subir el footer cuando hay múltiples líneas para que no quede pegado al borde
            val footerBaseY = if (isLandscapePage) {
                currentPageHeight - MARGIN_BOTTOM - totalFooterHeight + 5f
            } else {
                PAGE_HEIGHT - MARGIN_BOTTOM - totalFooterHeight + 5f
            }
            
            // Centrar horizontalmente en páginas horizontales o alinear a la izquierda en verticales
            val footerX = if (isLandscapePage) {
                // En modo paisaje, centrar cada línea
                (currentPageWidth - paint.measureText(lines.firstOrNull() ?: "")) / 2
            } else {
                MARGIN_HORIZONTAL + 10f // Margen adicional para alinear con el contenido
            }
            
            // Dibujar cada línea del footer
            lines.forEachIndexed { index, line ->
                val lineY = footerBaseY + (index * lineHeight)
                val lineX = if (isLandscapePage) {
                    // Recalcular la posición X para cada línea en modo paisaje para centrarla
                    (currentPageWidth - (if (index == 0) notaPrefixWidth + notaTextPaint.measureText(line) else notaTextPaint.measureText(line))) / 2
                } else {
                    footerX
                }
                
                // Si es la primera línea, dibujar "Nota:" en rojo y negritas
                if (index == 0) {
                    canvas.drawText(notaPrefix, lineX, lineY, notaPrefixPaint)
                    canvas.drawText(line, lineX + notaPrefixWidth, lineY, notaTextPaint)
                } else {
                    canvas.drawText(line, lineX, lineY, notaTextPaint)
                }
            }
        } else {
            // Para el footer estándar (sin nota), mantener el comportamiento original
            val footerY = if (isLandscapePage) {
                currentPageHeight - MARGIN_BOTTOM + 5f
            } else {
                PAGE_HEIGHT - MARGIN_BOTTOM + 5f
            }
            
            // Centrar el texto del footer horizontalmente en páginas horizontales
            val footerX = if (isLandscapePage) {
                val textWidth = paint.measureText(footerText)
                (currentPageWidth - textWidth) / 2
            } else {
                MARGIN_HORIZONTAL + 10f
            }
            
            canvas.drawText(footerText, footerX, footerY, paint)
        }
    }

    private fun buildFileName(data: FormularioData): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        
        // Sanitizar el nombre del supervisor: eliminar acentos, espacios dobles y caracteres no permitidos
        val supervisorOriginal = data.supervisor.ifEmpty { "Sin_Supervisor" }
        val supervisorSanitizado = supervisorOriginal
            .replace(Regex("\\s+"), "_") // Reemplazar espacios con guiones bajos
            .replace(Regex("[áàäâã]"), "a")
            .replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i")
            .replace(Regex("[óòöôõ]"), "o")
            .replace(Regex("[úùüû]"), "u")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[^a-zA-Z0-9_-]"), "") // Eliminar caracteres no alfanuméricos excepto guiones
        
        // Asegurar unicidad si se generan varios archivos en el mismo minuto
        val uniqueSuffix = System.currentTimeMillis() % 100 // Últimos 2 dígitos de los milisegundos
        
        return "Reporte_CFE_${timestamp}_Supervisor_${supervisorSanitizado}_${uniqueSuffix}.pdf"
    }

    private fun createLegacyDownloadsFile(filename: String): File {
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CFE")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        return File(downloadsDir, filename)
    }
}