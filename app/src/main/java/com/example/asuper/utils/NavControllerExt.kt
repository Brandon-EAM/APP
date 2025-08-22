package com.example.asuper.utils

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extensión para NavController que proporciona navegación segura
 * evitando IllegalArgumentException cuando se navega rápidamente
 */
object NavControllerExt {
    private val isNavigating = AtomicBoolean(false)
    private const val TAG = "NavControllerExt"
    
    /**
     * Navega de forma segura usando un ID de acción
     * Evita navegaciones múltiples simultáneas y verifica que la acción exista
     * desde el destino actual
     */
    fun NavController.safeNavigate(actionId: Int) {
        // Si ya estamos navegando, ignoramos esta navegación
        if (isNavigating.get()) {
            Log.d(TAG, "Navegación ignorada: ya hay una navegación en curso")
            return
        }
        
        // Verificamos que la acción exista desde el destino actual
        val action = currentDestination?.getAction(actionId)
        if (action == null) {
            Log.d(TAG, "Navegación ignorada: acción $actionId no disponible desde el destino actual ${currentDestination?.id}")
            return
        }
        
        try {
            // Marcamos que estamos navegando
            isNavigating.set(true)
            // Realizamos la navegación
            navigate(actionId)
        } catch (e: IllegalArgumentException) {
            // Capturamos la excepción para evitar el crash
            Log.e(TAG, "Error al navegar: ${e.message}")
        }
    }
    
    /**
     * Navega de forma segura usando NavDirections
     * Evita navegaciones múltiples simultáneas y verifica que la acción exista
     * desde el destino actual
     */
    fun NavController.safeNavigate(directions: NavDirections) {
        safeNavigate(directions.actionId)
    }
    
    /**
     * Resetea el estado de navegación
     * Debe llamarse cuando se completa una navegación
     */
    fun resetNavigationState() {
        isNavigating.set(false)
    }
}