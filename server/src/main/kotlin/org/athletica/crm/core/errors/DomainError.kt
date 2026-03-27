package org.athletica.crm.core.errors

interface DomainError {
    val code: String
    val message: String
}


data class  CommonDomainError(override val code: String, override val message: String) : DomainError
