package org.athletica.crm.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.json.Json
import org.athletica.crm.core.systemContext
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.storage.Database
import org.athletica.infra.yookassa.YookassaConfig
import org.athletica.infra.yookassa.YookassaWebhook
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KtorSimpleLogger("org.athletica.crm.routes.CheckoutRoutes")
private val webhookJson = Json { ignoreUnknownKeys = true }

/**
 * Маршруты webhook-уведомлений от платёжных шлюзов.
 * ВАЖНО: регистрировать ВНЕ блока `authenticate("auth-jwt")` — вызываются ЮKassa напрямую, без JWT.
 */
fun Route.checkoutRoutes(
    db: Database,
    payments: Payments,
    orgBalances: OrgBalances,
    yookassaConfig: YookassaConfig,
) {
    post("/checkout/yookassa") {
        val payload = call.receiveText()
        val signature = call.request.headers["X-HMAC-SHA256"]

        // 1. Верификация подписи — защита от поддельных вызовов
        if (signature == null || !verifySignature(payload, signature, yookassaConfig.secretKey)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        // 2. Парсинг тела webhook
        val webhook =
            try {
                webhookJson.decodeFromString<YookassaWebhook>(payload)
            } catch (e: Exception) {
                logger.warn("Не удалось распарсить webhook ЮKassa: ${e.message}")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

        // 3. Обрабатываем только событие успешной оплаты; прочие события игнорируем
        if (webhook.event != "payment.succeeded") {
            call.respond(HttpStatusCode.OK)
            return@post
        }

        val externalId = webhook.paymentObject.id

        // 4. Атомарно: markAsPaid + replenish — в одной транзакции
        db.transaction {
            arrow.core.raise.either {
                context(this@transaction) {
                    // markAsPaid не требует RequestContext — поиск по глобально уникальному ключу
                    val payment = payments.markAsPaid("yookassa", externalId)

                    // SystemRequestContext строится из данных платежа — orgId и currency известны
                    val sysCtx = systemContext(payment.orgId, currency = payment.amount.currency)

                    context(sysCtx) {
                        orgBalances.current().replenish(
                            amount = payment.amount,
                            description = "Пополнение баланса, платёж #${payment.externalPaymentId}",
                        )
                    }
                }
            }.onLeft { error ->
                // PaymentAlreadyProcessed — идемпотентный случай, не критическая ошибка
                logger.warn("Webhook обработан ранее или платёж не найден: ${error.message}")
            }
        }

        // Всегда отвечаем 200 — иначе ЮKassa будет повторять доставку webhook
        call.respond(HttpStatusCode.OK)
    }
}

/** Верифицирует HMAC-SHA256 подпись webhook ЮKassa. */
private fun verifySignature(payload: String, signature: String, secretKey: String): Boolean {
    val expected = hmacSha256(payload, secretKey)
    return expected.equals(signature, ignoreCase = true)
}

/** Вычисляет HMAC-SHA256 хеш строки [data] с ключом [key]; возвращает hex-строку в нижнем регистре. */
private fun hmacSha256(data: String, key: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
