package com.example.asuper

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        window.setStatusBarColor(colorVerdePrincipal)

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