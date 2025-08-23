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
import java.io.ByteArrayInputStream
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
        private const val FOOTER_SPACE = 40f
        private const val CFE_GREEN = 0xFF00A651.toInt()
        private const val CFE_BLUE = 0xFF0066CC.toInt()
        private const val DARK_GRAY = 0xFF333333.toInt()
    }

    private val pdfMaxSize = 15 * 1024 * 1024 // 15 MB

    private fun countTotalImages(data: FormularioData): Int {
        return listOf(
            data.seccionA.imagenesUris.size,
            data.seccionB.imagenesUris.size,
            data.seccionC.imagenesUris.size,
            data.seccionD.imagenesUris.size,
            data.seccionE.imagenesUris.size,
            data.seccionF.imagenesUris.size,
            data.seccionG.imagenesUris.size,
            data.seccionH.imagenesUris.size,
            data.seccionI.imagenesUris.size,
            data.seccionJ.imagenesUris.size,
        ).sum()
    }

    private fun calculateTotalPages(data: FormularioData): Int {
        var pages = 1 // Datos Generales
        SeccionType.values().forEach { type ->
            val seccionData = when (type) {
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
            val n = seccionData.imagenesUris.size
            pages += when {
                n == 0 -> 1
                n <= 4 -> 1
                else -> 1 + ((n - 4 + 3) / 4) // ceil((n-4)/4)
            }
        }
        return pages
    }

    private fun getSectionQuality(baseQuality: Int, imagesInSection: Int): Int {
        return when {
            imagesInSection <= 2 -> 100
            imagesInSection <= 4 -> (baseQuality - 5).coerceAtLeast(90)
            else -> (baseQuality - 10 - (imagesInSection - 4) * 3).coerceAtLeast(80)
        }
    }

    fun generatePdf(
        data: FormularioData,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): File? {
        return try {
            val totalImages = countTotalImages(data)
            var baseQuality = when {
                totalImages <= 5 -> 100
                totalImages <= 10 -> 95
                totalImages <= 15 -> 90
                totalImages <= 20 -> 85
                else -> 80
            }
            var resultFile: File? = null
            val totalPages = calculateTotalPages(data)

            while (baseQuality >= 70 && resultFile == null) {
                if (isCancelled()) return null
                val document = PdfDocument()
                var currentPage = 0

                createDatosGeneralesPage(document, data)
                currentPage++
                onProgress(currentPage, totalPages)
                if (isCancelled()) {
                    document.close()
                    return null
                }

                SeccionType.values().forEach { seccionType ->
                    if (isCancelled()) return null
                    createSeccionPage(document, data, seccionType, baseQuality) {
                        currentPage++
                        onProgress(currentPage, totalPages)
                    }
                }

                val baos = ByteArrayOutputStream()
                document.writeTo(baos)
                document.close()

                if (baos.size() > pdfMaxSize) {
                    baseQuality -= 5
                    continue
                }

                val bytes = baos.toByteArray()
                val filename = buildFileName(data)
                val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

                outputStream.write(bytes)
                outputStream.close()

                resultFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CFE/${filename}")
                } else {
                    createLegacyDownloadsFile(filename)
                }
            }

            resultFile
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

    private fun createSeccionPage(
        document: PdfDocument,
        data: FormularioData,
        seccionType: SeccionType,
        baseQuality: Int,
        onPageFinished: () -> Unit
    ) {
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
            createSingleSeccionPage(document, data, seccionType, seccionData, emptyList(), getSectionQuality(baseQuality, 0), onPageFinished)
            return
        }
        
        // Determinar si es una sola imagen horizontal para la primera página
        var isLandscapePage = false
        if (!seccionData.usarTexto && seccionData.imagenesUris.size == 1) {
            try {
                val uri = Uri.parse(seccionData.imagenesUris[0])
                val bitmap = loadAndProcessImage(uri)
                if (bitmap != null && bitmap.width > bitmap.height) {
                    isLandscapePage = true
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } catch (_: Exception) { }
        }
        
        // Distribuir imágenes según las reglas
        val imagenesUris = seccionData.imagenesUris

        when {
            // Hasta 4 imágenes se colocan en la primera página
            imagenesUris.size <= 4 -> {
                createSingleSeccionPage(
                    document,
                    data,
                    seccionType,
                    seccionData,
                    imagenesUris,
                    getSectionQuality(baseQuality, imagenesUris.size),
                    onPageFinished
                )
            }

            // Más de 4 imágenes: 4 en la primera página y el resto en páginas adicionales (máx 4 por página)
            else -> {
                createSingleSeccionPage(
                    document,
                    data,
                    seccionType,
                    seccionData,
                    imagenesUris.take(4),
                    getSectionQuality(baseQuality, imagenesUris.size),
                    onPageFinished
                )

                val remainingImages = imagenesUris.drop(4)
                remainingImages.chunked(4).forEach { chunk ->
                    createAdditionalImagesPage(
                        document,
                        data,
                        seccionType,
                        chunk,
                        getSectionQuality(baseQuality, imagenesUris.size),
                        onPageFinished
                    )
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
        imagesToShow: List<String>,
        quality: Int,
        onPageFinished: () -> Unit
    ) {
        // Determinar si es una sola imagen horizontal
        var isLandscapePage = false
        if (!seccionData.usarTexto && imagesToShow.size == 1) {
            try {
                val uri = Uri.parse(imagesToShow[0])
                val bitmap = loadAndProcessImage(uri)
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
            yPosition = drawImages(canvas, imagesToShow, yPosition, isLandscapePage, quality)
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
        onPageFinished()
    }
    
    /**
     * Crea una página adicional solo con imágenes (sin texto descriptivo)
     */
    private fun createAdditionalImagesPage(
        document: PdfDocument, 
        data: FormularioData, 
        seccionType: SeccionType, 
        imagesToShow: List<String>,
        quality: Int,
        onPageFinished: () -> Unit
    ) {
        if (imagesToShow.isEmpty()) return
        
        // Determinar si es una sola imagen horizontal
        var isLandscapePage = false
        if (imagesToShow.size == 1) {
            try {
                val uri = Uri.parse(imagesToShow[0])
                val bitmap = loadAndProcessImage(uri)
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
        yPosition = drawImages(canvas, imagesToShow, yPosition, isLandscapePage, quality)

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
        onPageFinished()
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

    private fun drawImages(
        canvas: Canvas,
        imageUris: List<String>,
        startY: Float,
        isLandscapePage: Boolean = false,
        imageQuality: Int = 95
    ): Float {
        if (imageUris.isEmpty()) return startY

        var currentY = startY

        val bitmaps = imageUris.mapNotNull { uriString ->
            try {
                val uri = Uri.parse(uriString)
                loadAndProcessImage(uri)
            } catch (_: Exception) { null }
        }

        if (bitmaps.isEmpty()) return startY

        val pageWidth = if (isLandscapePage) PAGE_HEIGHT else PAGE_WIDTH
        val pageHeight = if (isLandscapePage) PAGE_WIDTH else PAGE_HEIGHT
        val contentWidth = pageWidth - (2 * MARGIN_HORIZONTAL)
        val availableHeight = pageHeight - MARGIN_BOTTOM - FOOTER_SPACE - currentY
        val spacing = 10f

        // Usar una sola columna cuando haya una o dos imágenes para maximizar la legibilidad
        val columns = if (bitmaps.size <= 2) 1 else 2
        val rows = Math.ceil(bitmaps.size / columns.toDouble()).toInt()

        // Altura de fila sin límite artificial para evitar estiramientos
        val rowHeight = (availableHeight - (rows - 1) * spacing) / rows

        var index = 0
        val paint = Paint().apply { isFilterBitmap = true }
        for (row in 0 until rows) {
            val colsInRow = minOf(columns, bitmaps.size - index)
            val colWidth = (contentWidth - (colsInRow - 1) * spacing) / colsInRow
            var x = MARGIN_HORIZONTAL
            for (col in 0 until colsInRow) {
                var bitmap = bitmaps[index]

                if (imageQuality < 100) {
                    val compressed = compressToJpegAndDecode(bitmap, imageQuality)
                    if (compressed != bitmap) {
                        bitmap.recycle()
                        bitmap = compressed
                    }
                }

                val scale = minOf(colWidth / bitmap.width, rowHeight / bitmap.height, 1f)
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val drawX = x + (colWidth - drawWidth) / 2
                val drawY = currentY + (rowHeight - drawHeight) / 2

                val dest = RectF(drawX, drawY, drawX + drawWidth, drawY + drawHeight)
                canvas.drawBitmap(bitmap, null, dest, paint)
                bitmap.recycle()

                x += colWidth + spacing
                index++
            }
            currentY += rowHeight
            if (row < rows - 1) currentY += spacing
        }

        return currentY
    }

    private fun loadAndProcessImage(uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap == null) return null

            val orientation = ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            bitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
            bitmap
        } catch (_: Exception) { null }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compressToJpegAndDecode(bitmap: Bitmap, quality: Int): Bitmap {
        if (quality >= 100) return bitmap

        // Dibujar sobre un fondo blanco para evitar bordes grises al comprimir imágenes con transparencia
        val withBg = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(withBg).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }

        val baos = ByteArrayOutputStream()
        withBg.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bytes = baos.toByteArray()

        if (withBg != bitmap) withBg.recycle()
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