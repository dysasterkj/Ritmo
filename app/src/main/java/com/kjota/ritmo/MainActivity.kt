package com.kjota.ritmo

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.kjota.ritmo.ui.RitmoRoot
import com.kjota.ritmo.ui.RitmoTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleShare(intent)
        setContent { RitmoTheme { RitmoRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Desde Spotify, YouTube, etc. se puede "Compartir" una playlist directamente a Ritmo. */
    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { vm.setPendingImport(it) }
        }
    }
}
