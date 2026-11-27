package org.athletica.mobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.athletica.mobile.AndroidApp
import org.athletica.mobile.ui.AppLinks

/** Единственная Activity приложения: весь интерфейс — общие экраны `:mobile`. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AndroidApp(BuildConfig.API_URL, AppLinks(BuildConfig.TERMS_URL, BuildConfig.PRIVACY_URL)) }
    }
}
