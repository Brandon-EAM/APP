package com.example.asuper.utils

/**
 * Clase interna que contiene metadatos de la aplicación.
 * Esta clase no se utiliza directamente en la interfaz de usuario.
 */
internal object AppMetadata {
    // Constantes internas que no se muestran en la interfaz
    private const val CREATOR_SIGNATURE = "BranEAM"
    private const val BUILD_VERSION = "1.0.0"
    
    // Método interno para verificación de integridad
    internal fun getVerificationData(): String {
        return "$BUILD_VERSION-$CREATOR_SIGNATURE"
    }
}