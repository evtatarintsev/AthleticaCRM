package org.athletica.crm.core.errors

/** Базовый тип доменной ошибки. [code] — машиночитаемый код, [message] — описание для клиента. */
interface DomainError {
    val code: String
    val message: String
}

/** Универсальная реализация [DomainError] для случаев, не требующих отдельного типа. */
data class CommonDomainError(
    override val code: String,
    override val message: String,
) : DomainError
