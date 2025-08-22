package com.example.asuper

import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.example.asuper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar la barra de estado para que se integre con la barra verde
        // Configurar el color de la barra de estado para todas las versiones
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        
        // Obtener el color verde principal
        val colorVerdePrincipal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(R.color.cfe_verde_principal, theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(R.color.cfe_verde_principal)
        }
        
        // Aplicar el color a la barra de estado
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Para Android 12 (API 31) y superior
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            // Solo aplicar el color a la barra de estado, no a toda la ventana
            window.statusBarColor = colorVerdePrincipal
        } else {
            // Para Android 10 y versiones anteriores
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            @Suppress("DEPRECATION")
            window.statusBarColor = colorVerdePrincipal
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el logo para que ocupe el 40% del ancho de la pantalla
        val logoImageView = findViewById<ImageView>(R.id.iv_logo_cfe)
        val screenWidth = resources.displayMetrics.widthPixels
        val logoWidth = (screenWidth * 0.4).toInt() // 40% del ancho de la pantalla
        val layoutParams = logoImageView.layoutParams
        layoutParams.width = logoWidth
        logoImageView.layoutParams = layoutParams

        // Configurar el NavController
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
    }
}