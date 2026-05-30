package org.athletica.crm

import android.content.Intent
import android.net.Uri

actual fun openUrl(url: String) {
    val ctx = AndroidContextHolder.applicationContext ?: return
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    runCatching { ctx.startActivity(intent) }
}
