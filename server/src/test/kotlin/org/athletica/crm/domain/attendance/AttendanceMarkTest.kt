package org.athletica.crm.domain.attendance

import arrow.core.Either
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты смарт-конструктора отметки: «не пришёл без метки» непредставимо. */
class AttendanceMarkTest {
    private val label = AttendanceLabelId.new()

    /** Код ошибки из [result]; успех роняет тест. */
    private fun code(result: Either<DomainError, *>): String = assertIs<Either.Left<DomainError>>(result).value.code

    @Test
    fun `отсутствие без меток создать нельзя`() {
        val result = AttendanceMark.Absent.from(emptySet())

        assertEquals("ATTENDANCE_ABSENCE_REQUIRES_LABEL", code(result))
    }

    @Test
    fun `отсутствие с меткой создаётся`() {
        val mark = assertIs<Either.Right<AttendanceMark.Absent>>(AttendanceMark.Absent.from(setOf(label))).value

        assertEquals(AttendancePresence.ABSENT, mark.presence)
        assertEquals(setOf(label), mark.labelIds)
    }

    @Test
    fun `отметка из состояния не пришёл без меток отклоняется`() {
        val result = AttendanceMark.from(AttendancePresence.ABSENT, emptyList())

        assertEquals("ATTENDANCE_ABSENCE_REQUIRES_LABEL", code(result))
    }

    @Test
    fun `присутствие без меток допустимо`() {
        assertEquals(Either.Right(AttendanceMark.Present()), AttendanceMark.from(AttendancePresence.PRESENT, emptyList()))
    }

    @Test
    fun `метки у неотмеченного участника отклоняются`() {
        val result = AttendanceMark.from(null, listOf(label))

        assertEquals("ATTENDANCE_LABELS_WITHOUT_PRESENCE", code(result))
    }

    @Test
    fun `без присутствия и меток — не отмечен`() {
        assertEquals(Either.Right(AttendanceMark.Unmarked), AttendanceMark.from(null, emptyList()))
    }
}
