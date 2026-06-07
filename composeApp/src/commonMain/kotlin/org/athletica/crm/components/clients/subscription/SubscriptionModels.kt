package org.athletica.crm.components.clients.subscription

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import kotlin.math.absoluteValue
import kotlin.math.round

/**
 * Тарифный план — шаблон абонемента, задаваемый в настройках организации.
 */
data class TariffPlan(
    /** Идентификатор тарифа-шаблона. */
    val id: TariffPlanId,
    /** Отображаемое название плана. */
    val name: String,
    /** Количество занятий; `null` — безлимитный план. */
    val sessions: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость плана. */
    val price: Money,
)

/**
 * Скидка на абонемент.
 */
sealed interface Discount {
    /** Без скидки. */
    data object None : Discount

    /** Скидка в процентах от стоимости. */
    data class Percent(val value: Int) : Discount

    /** Скидка фиксированной суммой. */
    data class Fixed(val amount: Money) : Discount
}

/**
 * Параметры выдаваемого абонемента (шаг «Параметры»).
 *
 * Если [plan] равен `null`, абонемент считается индивидуальным (персональным) —
 * все поля заполняются вручную. При выборе плана поля префиллятся, но остаются
 * редактируемыми.
 */
data class SubscriptionForm(
    /** Выбранный тарифный план или `null` для индивидуального абонемента. */
    val plan: TariffPlan?,
    /** Группы, в которых действует абонемент. */
    val groupIds: Set<GroupId>,
    /** Признак безлимитного количества занятий. */
    val unlimited: Boolean,
    /** Количество занятий; `null`, если не задано или безлимит. */
    val sessions: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Дата начала действия (по умолчанию — сегодня). */
    val startDate: LocalDate,
    /** Стоимость абонемента до скидки. */
    val price: Money,
    /** Применяемая скидка. */
    val discount: Discount,
) {
    /** Дата окончания: [startDate] плюс срок действия. Только для чтения. */
    val endDate: LocalDate
        get() =
            when (durationUnit) {
                DurationUnit.DAYS -> startDate.plus(DatePeriod(days = durationValue))
                DurationUnit.MONTHS -> startDate.plus(DatePeriod(months = durationValue))
            }

    /** Сумма скидки в деньгах, не превышающая стоимость. */
    val discountAmount: Money
        get() =
            when (val d = discount) {
                Discount.None -> Money.zero(price.currency)
                is Discount.Percent -> Money(price.minorUnits * d.value.coerceIn(0, 100) / 100, price.currency)
                is Discount.Fixed -> if (d.amount > price) price else d.amount
            }

    /** Итоговая стоимость с учётом скидки (не ниже нуля). */
    val total: Money
        get() {
            val result = price - discountAmount
            return if (result.isNegative) Money.zero(price.currency) else result
        }

    /**
     * Форма пригодна для перехода к оплате.
     *
     * Группы — необязательное поле на этапе UI без бекенда (в новой/тестовой
     * организации их может не быть, иначе форму не пройти). Обязательны лишь
     * количество занятий (или безлимит) и положительный срок действия.
     */
    val isValid: Boolean
        get() = (unlimited || (sessions != null && sessions > 0)) && durationValue > 0
}

/**
 * Параметры оплаты деньгами/балансом (шаг «Оплата»).
 *
 * Признак «оплата не требуется» — это отдельный шаг мастера, а не поле здесь.
 * Сдача на руки вычисляется как `сдача − [changeToBalance]`, чтобы не хранить
 * связанные значения раздельно.
 */
data class PaymentForm(
    /** Сколько списать с баланса (может уводить баланс в минус — долг). */
    val fromBalance: Money,
    /** Сколько внесено деньгами (наличные/карта). */
    val cashGiven: Money,
    /** Сколько из сдачи зачислить на счёт клиента; остальное выдаётся на руки. */
    val changeToBalance: Money,
)

/**
 * Результат расчёта оплаты — производная величина от итоговой суммы, текущего
 * баланса и введённых администратором значений. Чистая функция, без побочек.
 */
data class PaymentSummary(
    /** Сколько покрыто (деньги + списание с баланса). */
    val covered: Money,
    /** Переплата (сдача). Ноль, если переплаты нет. */
    val change: Money,
    /** Часть сдачи, зачисляемая на счёт. */
    val changeToBalance: Money,
    /** Часть сдачи, выдаваемая на руки. */
    val changeToHand: Money,
    /** Остаток, ушедший в долг (списан с баланса сверх внесённых денег). */
    val debt: Money,
    /** Баланс клиента после операции. */
    val newBalance: Money,
)

/**
 * Считает [PaymentSummary] для итоговой суммы [total], текущего баланса
 * [currentBalance] и введённых значений [payment].
 */
fun paymentSummary(total: Money, currentBalance: Money, payment: PaymentForm): PaymentSummary {
    val currency = total.currency
    val covered = payment.cashGiven + payment.fromBalance

    return if (covered >= total) {
        val change = covered - total
        val toBalance = payment.changeToBalance.coerceIn(Money.zero(currency), change)
        PaymentSummary(
            covered = covered,
            change = change,
            changeToBalance = toBalance,
            changeToHand = change - toBalance,
            debt = Money.zero(currency),
            newBalance = currentBalance - payment.fromBalance + toBalance,
        )
    } else {
        val debt = total - covered
        PaymentSummary(
            covered = covered,
            change = Money.zero(currency),
            changeToBalance = Money.zero(currency),
            changeToHand = Money.zero(currency),
            debt = debt,
            newBalance = currentBalance - payment.fromBalance - debt,
        )
    }
}

/** Ограничивает сумму диапазоном [[min], [max]] (валюты должны совпадать). */
private fun Money.coerceIn(min: Money, max: Money): Money =
    when {
        this < min -> min
        this > max -> max
        else -> this
    }

/**
 * Разбирает введённый пользователем текст в [Money] указанной валюты.
 * Пустая строка трактуется как ноль; некорректный или отрицательный ввод — `null`.
 */
fun parseMoney(text: String, currency: Currency): Money? {
    val raw = text.replace(',', '.').trim()
    if (raw.isBlank()) {
        return Money.zero(currency)
    }
    val value = raw.toDoubleOrNull() ?: return null
    if (value < 0) {
        return null
    }
    val scale = pow10(currency.fractionDigits)
    return Money(round(value * scale).toLong(), currency)
}

/**
 * Текстовое представление суммы для редактирования: без символа валюты и
 * группировки разрядов; дробная часть опускается, если равна нулю.
 */
val Money.editText: String
    get() {
        val scale = pow10(currency.fractionDigits)
        val major = minorUnits / scale
        val minor = (minorUnits % scale).absoluteValue
        return if (currency.fractionDigits == 0 || minor == 0L) {
            major.toString()
        } else {
            "$major.${minor.toString().padStart(currency.fractionDigits, '0')}"
        }
    }

private fun pow10(digits: Int): Long {
    var result = 1L
    repeat(digits) { result *= 10 }
    return result
}
