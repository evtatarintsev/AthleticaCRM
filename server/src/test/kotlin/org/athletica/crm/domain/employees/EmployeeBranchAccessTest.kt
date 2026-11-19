package org.athletica.crm.domain.employees

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.domain.EmployeeStub
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmployeeBranchAccessTest {
    @Test
    fun `all grants access to any branch`() {
        val employee = EmployeeStub(availableBranches = EmployeeBranchAccess.All)

        assertTrue(employee.hasAccessTo(BranchId.new()))
        assertTrue(employee.hasAccessTo(BranchId.new()))
    }

    @Test
    fun `selected grants access only to assigned branches`() {
        val branchId = BranchId.new()
        val employee = EmployeeStub(availableBranches = EmployeeBranchAccess.Selected(listOf(branchId)))

        assertTrue(employee.hasAccessTo(branchId))
        assertFalse(employee.hasAccessTo(BranchId.new()))
    }

    @Test
    fun `empty selection denies access`() {
        val employee = EmployeeStub(availableBranches = EmployeeBranchAccess.Selected(emptyList()))

        assertFalse(employee.hasAccessTo(BranchId.new()))
    }
}
