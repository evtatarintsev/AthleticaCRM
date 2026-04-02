package org.athletica.crm

import kotlinx.datetime.TimeZone

actual fun platformAvailableTimezones(): List<String> =
    TimeZone.availableZoneIds.sorted()

actual fun platformCurrentTimezone(): String =
    TimeZone.currentSystemDefault().id
