package com.kline.inventorypos.core.common

import java.text.NumberFormat
import java.util.Locale

private val UgandanNumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-UG"))

fun formatUgx(amount: Long): String = "UGX ${UgandanNumberFormat.format(amount)}"

fun sanitizeAmountInput(raw: String, maxDigits: Int = 12): String {
    val digits = raw.filter(Char::isDigit).take(maxDigits)
    if (digits.isEmpty()) return ""
    return digits.trimStart('0').ifEmpty { "0" }
}

/**
 * Formats a whole-currency value for display.
 * A leading zero is kept only while zero is the complete value, so typing after it
 * never turns an intended 5 into 05.
 */
fun formatAmountInput(raw: String, maxDigits: Int = 12): String {
    return sanitizeAmountInput(raw, maxDigits)
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
}

fun parseAmountInput(value: String): Long? =
    value.filter(Char::isDigit).takeIf(String::isNotEmpty)?.toLongOrNull()
