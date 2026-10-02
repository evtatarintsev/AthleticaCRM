package org.athletica.crm.api.schemas.attendance

import kotlinx.serialization.Serializable
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId

/** Метка посещаемости из справочника организации. */
@Serializable
data class AttendanceLabelSchema(
    /** Идентификатор метки. */
    val id: AttendanceLabelId,
    /** Название метки. */
    val name: String,
    /** Состояние отметки, к которому метка применима. */
    val scope: AttendanceLabelScope,
    /** Порядок метки в справочнике. */
    val position: Int,
    /** Метка архивирована: не предлагается для новых отметок. */
    val isArchived: Boolean,
)

/** Запрос справочника меток. */
@Serializable
data class AttendanceLabelListRequest(
    /** Включать архивные метки. */
    val includeArchived: Boolean = false,
)

/** Справочник меток организации, упорядоченный по [AttendanceLabelSchema.position]. */
@Serializable
data class AttendanceLabelListResponse(
    /** Метки справочника. */
    val labels: List<AttendanceLabelSchema>,
)

/** Запрос на создание метки. */
@Serializable
data class CreateAttendanceLabelRequest(
    /** Идентификатор новой метки, выбранный клиентом. */
    val id: AttendanceLabelId,
    /** Название метки; уникально в пределах организации. */
    val name: String,
    /** Применимость метки. */
    val scope: AttendanceLabelScope,
)

/** Запрос на изменение названия и порядка метки. */
@Serializable
data class UpdateAttendanceLabelRequest(
    /** Изменяемая метка. */
    val id: AttendanceLabelId,
    /** Новое название. */
    val name: String,
    /** Новый порядок в справочнике. */
    val position: Int,
)

/** Запрос, адресованный одной метке: архивация или восстановление. */
@Serializable
data class AttendanceLabelRequest(
    /** Метка, над которой выполняется действие. */
    val id: AttendanceLabelId,
)
