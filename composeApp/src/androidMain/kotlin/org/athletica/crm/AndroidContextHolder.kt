package org.athletica.crm

import android.content.Context

/**
 * Синглтон для доступа к контексту приложения из платформенных top-level функций.
 * Инициализируется в [AthleticaApp.onCreate] и живёт на протяжении всего жизненного цикла процесса.
 */
object AndroidContextHolder {
    /** Контекст приложения. Устанавливается в [AthleticaApp.onCreate], всегда non-null после старта. */
    var applicationContext: Context? = null
}
