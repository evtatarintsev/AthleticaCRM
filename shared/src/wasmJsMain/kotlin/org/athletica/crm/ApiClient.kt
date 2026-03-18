package org.athletica.crm

import io.ktor.client.*
import io.ktor.client.engine.js.*
import org.athletica.crm.api.client.createHttpClient

actual fun createHttpClient(): HttpClient = HttpClient(Js)
