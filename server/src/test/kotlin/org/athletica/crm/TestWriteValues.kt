package org.athletica.crm

import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.PositiveInt
import org.athletica.crm.core.errors.orThrow

internal fun testName(value: String): NonBlankName = orThrow { NonBlankName.from(value) }

internal fun testPositiveInt(value: Int): PositiveInt = orThrow { PositiveInt.from(value) }
