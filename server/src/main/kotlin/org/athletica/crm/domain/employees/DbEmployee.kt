package org.athletica.crm.domain.employees

import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
import kotlin.time.Instant

class DbEmployee(
    override val id: EmployeeId,
    override val userId: UserId?,
    override val name: String,
    override val avatarId: UploadId?,
    override val isOwner: Boolean,
    override val isActive: Boolean,
    override val joinedAt: Instant,
    override val roles: List<EmployeeRole>,
    override val phoneNo: String?,
    override val email: String?,
) : Employee
