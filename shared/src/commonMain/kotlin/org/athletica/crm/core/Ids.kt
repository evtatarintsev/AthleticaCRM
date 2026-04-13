package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class OrgId(val value: Uuid) {
    companion object {
        fun new() = OrgId(Uuid.generateV7())
    }
}

fun Uuid.toOrgId(): OrgId = OrgId(this)

@Serializable
@JvmInline
value class ClientId(val value: Uuid) {
    companion object {
        fun new() = ClientId(Uuid.generateV7())
    }
}

fun Uuid.toClientId(): ClientId = ClientId(this)

@Serializable
@JvmInline
value class DisciplineId(val value: Uuid) {
    companion object {
        fun new() = DisciplineId(Uuid.generateV7())
    }
}

fun Uuid.toDisciplineId(): DisciplineId = DisciplineId(this)

@Serializable
@JvmInline
value class GroupId(val value: Uuid) {
    companion object {
        fun new() = GroupId(Uuid.generateV7())
    }
}

fun Uuid.toGroupId(): GroupId = GroupId(this)

@Serializable
@JvmInline
value class UploadId(val value: Uuid) {
    companion object {
        fun new() = UploadId(Uuid.generateV7())
    }
}

fun Uuid.toUploadId(): UploadId = UploadId(this)

@Serializable
@JvmInline
value class EmployeeId(val value: Uuid) {
    companion object {
        fun new() = EmployeeId(Uuid.generateV7())
    }
}

fun Uuid.toEmployeeId(): EmployeeId = EmployeeId(this)

@Serializable
@JvmInline
value class UserId(val value: Uuid) {
    companion object {
        fun new() = UserId(Uuid.generateV7())
    }
}

fun Uuid.toUserId(): UserId = UserId(this)
