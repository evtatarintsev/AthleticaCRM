package org.athletica.crm.ui.list

import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.api.schemas.settings.SortStateSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Тесты для [SortState]. */
class SortStateTest {
    private val columnA = ColumnId("a")
    private val columnB = ColumnId("b")

    @Test
    fun `cycle null по колонке A даёт Asc(A)`() {
        val result = SortState.cycle(null, columnA)
        assertEquals(SortState(columnA, SortDirection.Asc), result)
    }

    @Test
    fun `cycle Asc(A) по колонке A даёт Desc(A)`() {
        val current = SortState(columnA, SortDirection.Asc)
        val result = SortState.cycle(current, columnA)
        assertEquals(SortState(columnA, SortDirection.Desc), result)
    }

    @Test
    fun `cycle Desc(A) по колонке A даёт null`() {
        val current = SortState(columnA, SortDirection.Desc)
        val result = SortState.cycle(current, columnA)
        assertNull(result)
    }

    @Test
    fun `cycle Asc(A) по колонке B даёт Asc(B)`() {
        val current = SortState(columnA, SortDirection.Asc)
        val result = SortState.cycle(current, columnB)
        assertEquals(SortState(columnB, SortDirection.Asc), result)
    }

    @Test
    fun `toDto и fromDto roundtrip`() {
        val original = SortState(columnA, SortDirection.Desc)
        val dto = original.toSchema()
        assertEquals(SortStateSchema("a", SortDirectionSchema.Desc), dto)
        val restored = SortState.fromSchema(dto)
        assertEquals(original, restored)
    }
}
