package org.athletica.crm

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsCurrentTimezone(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsAvailableTimezonesJoined(): String = js("(function(){ try { return Intl.supportedValuesOf('timeZone').join(','); } catch(e){ return 'UTC'; } })()")

actual fun platformCurrentTimezone(): String = jsCurrentTimezone()

actual fun platformAvailableTimezones(): List<String> = jsAvailableTimezonesJoined().split(",").sorted()
