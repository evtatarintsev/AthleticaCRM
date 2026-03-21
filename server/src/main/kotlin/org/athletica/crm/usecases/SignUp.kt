package org.athletica.crm.usecases

import org.athletica.crm.api.schemas.SignUpRequest
import org.athletica.crm.core.auth.AuthenticatedUser
import kotlin.uuid.Uuid


data class User(override val id: Uuid, override val username: String): AuthenticatedUser
class SignUp {
    suspend fun signUp(request: SignUpRequest) : User {
        return User(id = Uuid.generateV7(), username = "admin")
    }
}
