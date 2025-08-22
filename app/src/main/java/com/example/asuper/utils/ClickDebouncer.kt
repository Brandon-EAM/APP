package com.example.asuper.utils

/**
 * Utilidad para prevenir múltiples clics rápidos en botones
 * Implementa un mecanismo de debounce simple basado en tiempo
 */
class ClickDebouncer {
    private var lastClickTime: Long = 0
    
    /**
     * Intenta registrar un clic y devuelve true si el clic debe procesarse
     * @param debounceTime Tiempo mínimo entre clics en milisegundos
     * @return true si el clic debe procesarse, false si debe ignorarse
     */
    fun tryClick(debounceTime: Long = 500): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Si no ha pasado suficiente tiempo desde el último clic, ignoramos este
        if (currentTime - lastClickTime < debounceTime) {
            return false
        }
        
        // Actualizamos el tiempo del último clic
        lastClickTime = currentTime
        return true
    }
    
    /**
     * Resetea el temporizador de debounce
     */
    fun reset() {
        lastClickTime = 0
    }
}