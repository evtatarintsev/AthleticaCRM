package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.domain.clientapp.auth.ClientAppAccounts
import org.athletica.crm.domain.clientapp.auth.ClientAppCodes
import org.athletica.crm.domain.clientapp.auth.ClientAppSessions
import org.athletica.crm.domain.clientapp.auth.CodeCheck
import org.athletica.crm.domain.clientapp.auth.CodeIssue
import org.athletica.crm.domain.clientapp.auth.IssuedClientAppSession
import org.athletica.crm.domain.clientapp.codes.VerificationCode
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Вход в кабинет клиента по одноразовому коду: запрос кода, вход, обновление токена и выход.
 * [termsVersion] — текущая версия условий; [sessionTtl] — сколько живёт неактивный сеанс.
 */
class ClientAppSignIn(
    private val db: Database,
    private val codes: ClientAppCodes,
    private val accounts: ClientAppAccounts,
    private val sessions: ClientAppSessions,
    private val termsVersion: String,
    private val sessionTtl: Duration,
) {
    /**
     * Выпускает код на номер [rawPhone] в момент [now] под лимитами частоты. Исход одинаков для любого
     * номера; доставку кода ([VerificationCodeDelivery]) вызывающий запускает после ответа.
     * [lang] — язык ошибки разбора номера.
     */
    context(raise: Raise<AppError>)
    suspend fun requestCode(rawPhone: String, lang: Lang, now: Instant): CodeIssue {
        val phone = phone(rawPhone, lang)
        return db.transaction { codes.issue(phone, now) }
    }

    /**
     * Входит по номеру [rawPhone] и коду [rawCode] в момент [now] и открывает сеанс с названием
     * [sessionName]. Аккаунт без принятой текущей версии условий входит, только если [acceptedTermsVersion] —
     * текущая версия; иначе верный код возвращает `CLIENT_APP_TERMS_REQUIRED` без погашения и траты попытки.
     */
    context(raise: Raise<AppError>)
    suspend fun login(
        rawPhone: String,
        rawCode: String,
        acceptedTermsVersion: String?,
        sessionName: String?,
        lang: Lang,
        now: Instant,
    ): SignedIn {
        val phone = phone(rawPhone, lang)
        val code = VerificationCode.from(rawCode)
        val outcome =
            db.transaction {
                val account = accounts.byPhone(phone)
                val termsAccepted = account?.termsVersion == termsVersion
                val canSignIn = termsAccepted || acceptedTermsVersion == termsVersion
                when (codes.check(phone, code, now, consumeAccepted = canSignIn)) {
                    CodeCheck.WRONG -> LoginOutcome.WrongCode
                    CodeCheck.EXPIRED -> LoginOutcome.ExpiredCode
                    CodeCheck.ACCEPTED -> {
                        if (!canSignIn) {
                            return@transaction LoginOutcome.TermsRequired
                        }
                        val accountId = account?.id ?: accounts.new(phone, termsVersion, now)
                        if (account != null && !termsAccepted) {
                            accounts.acceptTerms(accountId, termsVersion, now)
                        }
                        LoginOutcome.Success(SignedIn(accountId, sessions.new(accountId, sessionName, now, now - sessionTtl)))
                    }
                }
            }
        return when (outcome) {
            is LoginOutcome.Success -> outcome.signedIn
            LoginOutcome.TermsRequired -> raise(CommonDomainError("CLIENT_APP_TERMS_REQUIRED", Messages.ClientAppTermsRequired.localize(lang)))
            LoginOutcome.WrongCode -> raise(CommonDomainError("CLIENT_APP_CODE_WRONG", Messages.ClientAppCodeWrong.localize(lang)))
            LoginOutcome.ExpiredCode -> raise(CommonDomainError("CLIENT_APP_CODE_EXPIRED", Messages.ClientAppCodeExpired.localize(lang)))
        }
    }

    /**
     * Меняет токен обновления [presentedJti] сеанса [sessionId] аккаунта [accountId] в момент [now].
     * Завершённый сеанс или повторно предъявленный токен — [SessionRevoked] (HTTP 401); во втором случае сеанс завершается.
     */
    context(raise: Raise<AppError>)
    suspend fun refresh(
        accountId: ClientAppAccountId,
        sessionId: ClientAppSessionId,
        presentedJti: Uuid,
        now: Instant,
    ): IssuedClientAppSession = db.transaction { sessions.rotate(sessionId, accountId, presentedJti, now) } ?: raise(SessionRevoked)

    /** Завершает сеанс запроса [ctx]. */
    context(ctx: ClientAccountContext, raise: Raise<AppError>)
    suspend fun logout() {
        db.transaction { sessions.delete(ctx.sessionId, ctx.accountId) }
    }

    /** Номер из [raw]; `INVALID_PHONE` на языке [lang], если в строке нет цифр. */
    context(raise: Raise<AppError>)
    private fun phone(raw: String, lang: Lang): PhoneDigits = PhoneDigits.from(raw) ?: raise(CommonDomainError("INVALID_PHONE", Messages.ClientAppInvalidPhone.localize(lang)))
}

/** Исход входа, зафиксированный транзакцией: потраченная попытка сохраняется и при ошибке. */
private sealed interface LoginOutcome {
    /** Нужно принять текущую версию условий. */
    data object TermsRequired : LoginOutcome

    /** Код неверный. */
    data object WrongCode : LoginOutcome

    /** Код больше не действует. */
    data object ExpiredCode : LoginOutcome

    /** Вход выполнен: [signedIn]. */
    data class Success(val signedIn: SignedIn) : LoginOutcome
}

/** Успешный вход: аккаунт [accountId] и его новый сеанс [session]. */
data class SignedIn(
    val accountId: ClientAppAccountId,
    val session: IssuedClientAppSession,
)
