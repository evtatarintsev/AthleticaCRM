package org.athletica.crm.domain.enrollments

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EnrollmentId
import org.athletica.crm.core.entityids.GroupId
import kotlin.time.Instant

/**
 * Запись клиента в группу за один период участия.
 *
 * Один клиент может иметь несколько записей в одну группу — каждая описывает
 * отдельный непрерывный период: ушёл и вернулся → новая запись.
 *
 * [enrolledAt] — момент добавления в группу.
 * [leftAt] — момент выхода; `null` означает активное участие прямо сейчас.
 */
@Serializable
data class Enrollment(
    /** Уникальный идентификатор записи. */
    val id: EnrollmentId,
    /** Группа, в которую записан клиент. */
    val groupId: GroupId,
    /** Записанный клиент. */
    val clientId: ClientId,
    /** Дата и время начала участия. */
    val enrolledAt: Instant,
    /** Дата и время окончания участия; `null` — участие активно. */
    val leftAt: Instant?,
)
