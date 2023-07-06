package io.github.smaugfm.monobudget.common.model.financial

import io.github.smaugfm.monobudget.common.util.formatW
import java.util.Currency
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

@JvmInline
value class Amount(val value: Long) {

    fun formatWithCurrency(currency: Currency, noDecimals: Boolean = false): String {
        return format(currency, noDecimals) + currency.currencyCode
    }

    fun format(currency: Currency, noDecimals: Boolean = false): String {
        val delimiter = (10.0.pow(currency.defaultFractionDigits)).toInt()
        return "${value / delimiter}${if (noDecimals) "" else ".${(abs(value % delimiter).formatW())}"}"
    }

    operator fun unaryMinus() = Amount(-value)

    fun equalsInverted(other: Amount): Boolean = this.value == -other.value

    /**
     * Monobank amount uses minimum currency units (e.g. cents for dollars)
     * and YNAB amount uses milliunits (1/1000th of a dollar)
     */
    fun toYnabAmount() = value * YNAB_MULTIPLIER

    fun toLunchmoneyAmount(currency: Currency) =
        value.toBigDecimal().setScale(2) / (10.toBigDecimal().pow(currency.defaultFractionDigits))

    companion object {

        fun fromYnabAmount(ynabAmount: Long) = Amount(ynabAmount / YNAB_MULTIPLIER)

        fun fromLunchmoneyAmount(lunchmoneyAmount: Double, currency: Currency) = Amount(
            (lunchmoneyAmount * (10.toBigDecimal()
                .pow(currency.defaultFractionDigits)).toLong()).roundToLong()
        )

        private const val YNAB_MULTIPLIER = 10
    }
}