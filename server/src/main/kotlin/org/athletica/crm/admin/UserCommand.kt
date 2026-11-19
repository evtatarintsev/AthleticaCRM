package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.mordant.terminal.prompt

/** Группа административных операций над пользователями; пользователь не принадлежит одной организации. */
class UserCommand : SuspendingNoOpCliktCommand(name = "user") {
    override fun help(context: Context): String = "Операции над пользователями"
}

/**
 * Задаёт пользователю новый пароль без проверки старого.
 * Пароль запрашивается скрыто и дважды; аргумента или опции для него нет, чтобы он
 * не попадал в историю shell и в список процессов.
 */
class ResetPasswordCommand : SuspendingCliktCommand(name = "reset-password") {
    private val di by requireObject<AdminDi>()

    private val login by argument("login", help = "Логин пользователя (точное совпадение)")

    override fun help(context: Context): String = "Сбросить пароль пользователя"

    override suspend fun run() {
        if (!di.userPasswords.exists(login)) {
            throw userNotFound()
        }
        val password = readPassword("Новый пароль")
        if (password.isEmpty()) {
            throw CliktError("Пароль не может быть пустым")
        }
        if (readPassword("Повторите пароль") != password) {
            throw CliktError("Пароли не совпадают")
        }
        if (!di.userPasswords.reset(login, password)) {
            throw userNotFound()
        }
        echo("Пароль пользователя $login изменён")
    }

    /** Запрашивает пароль скрытым вводом с подсказкой [text]; конец ввода — ошибка. */
    private fun readPassword(text: String): String =
        terminal.prompt(text, hideInput = true)
            ?: throw CliktError("Ввод пароля прерван")

    /** Ошибка «пользователь не найден» для текущего логина. */
    private fun userNotFound() = CliktError("Пользователь с логином \"$login\" не найден")
}
