package org.athletica.crm.domain.employees

import org.athletica.crm.core.permissions.Permission
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class EmployeePermissionTest {
    private fun role(vararg permissions: Permission) =
        EmployeeRole(
            id = Uuid.random(),
            name = "role",
            permissions = permissions.toSet(),
        )

    // ─── нет прав ────────────────────────────────────────────────────────────

    @Test
    fun `возвращает false если нет ролей и нет overrides`() {
        val p = EmployeePermission(emptyList(), emptySet(), emptySet())
        assertFalse(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    // ─── права через роль ────────────────────────────────────────────────────

    @Test
    fun `возвращает true если право есть в роли`() {
        val p =
            EmployeePermission(
                roles = listOf(role(Permission.CAN_VIEW_CLIENT_BALANCE)),
                grantedPermissions = emptySet(),
                revokedPermissions = emptySet(),
            )
        assertTrue(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    @Test
    fun `возвращает false если право не входит ни в одну роль`() {
        val p =
            EmployeePermission(
                roles = listOf(role()),
                grantedPermissions = emptySet(),
                revokedPermissions = emptySet(),
            )
        assertFalse(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    @Test
    fun `возвращает true если право есть хотя бы в одной из нескольких ролей`() {
        val p =
            EmployeePermission(
                roles = listOf(role(), role(Permission.CAN_VIEW_CLIENT_BALANCE)),
                grantedPermissions = emptySet(),
                revokedPermissions = emptySet(),
            )
        assertTrue(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    // ─── grantedPermissions ──────────────────────────────────────────────────

    @Test
    fun `возвращает true если право явно выдано без роли`() {
        val p =
            EmployeePermission(
                roles = emptyList(),
                grantedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
                revokedPermissions = emptySet(),
            )
        assertTrue(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    // ─── revokedPermissions ──────────────────────────────────────────────────

    @Test
    fun `возвращает false если право явно отозвано даже при наличии в роли`() {
        val p =
            EmployeePermission(
                roles = listOf(role(Permission.CAN_VIEW_CLIENT_BALANCE)),
                grantedPermissions = emptySet(),
                revokedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
            )
        assertFalse(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    @Test
    fun `возвращает false если право явно отозвано даже при явной выдаче`() {
        val p =
            EmployeePermission(
                roles = emptyList(),
                grantedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
                revokedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
            )
        assertFalse(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    // ─── приоритет: revoked > granted > roles ─────────────────────────────────

    @Test
    fun `granted перекрывает отсутствие права в ролях`() {
        val p =
            EmployeePermission(
                roles = listOf(role()),
                grantedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
                revokedPermissions = emptySet(),
            )
        assertTrue(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }

    @Test
    fun `revoked перекрывает и роли и granted одновременно`() {
        val p =
            EmployeePermission(
                roles = listOf(role(Permission.CAN_VIEW_CLIENT_BALANCE)),
                grantedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
                revokedPermissions = setOf(Permission.CAN_VIEW_CLIENT_BALANCE),
            )
        assertFalse(p.hasPermission(Permission.CAN_VIEW_CLIENT_BALANCE))
    }
}
