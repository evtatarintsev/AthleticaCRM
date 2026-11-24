package org.athletica.crm.demo

import arrow.core.raise.context.Raise
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.auth.Auth
import org.athletica.crm.domain.auth.SignUp
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import kotlin.random.Random

/**
 * Регистрация демо-режима: после регистрации [delegate] наполняет новую организацию профилем
 * [DemoProfile.SMALL] через [demoData] от имени владельца (его загружает [employees]) на сегодняшнюю
 * дату [calendar]. Наполнение идёт в транзакции регистрации: если оно не удалось, не сохраняется
 * ни организация, ни пользователь, ни письмо подтверждения. Остальные операции — как у [delegate].
 */
class DemoAuth(
    private val delegate: Auth,
    private val demoData: DemoData,
    private val employees: Employees,
    private val calendar: OrgCalendar = DbOrgCalendar(),
) : Auth by delegate {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun signup(request: SignUp, lang: Lang, clientIp: String?): AuthenticatedUser {
        val user = delegate.signup(request, lang, clientIp)
        context(ownerContext(user.orgId, employees, lang)) {
            demoData.fill(DemoProfile.SMALL, Random.Default, calendar.today())
        }
        return user
    }
}
