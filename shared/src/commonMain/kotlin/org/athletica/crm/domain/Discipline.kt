package org.athletica.crm.domain

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Дисциплина.
 * [id] — идентификатор дисциплины.
 * [name] — название дисциплины.
 */
@Serializable
data class Discipline(
    /** Идентификатор дисциплины. */
    val id: Uuid,
    /** Название дисциплины. */
    val name: String,
)
