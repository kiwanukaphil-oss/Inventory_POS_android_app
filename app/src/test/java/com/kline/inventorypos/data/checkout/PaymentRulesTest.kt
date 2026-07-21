package com.kline.inventorypos.data.checkout

import com.kline.inventorypos.core.model.PaymentLeg
import org.junit.Assert.assertThrows
import org.junit.Test

class PaymentRulesTest {
    @Test
    fun exactCashAndCashChangeAreValid() {
        PaymentRules.requireValid(listOf(PaymentLeg("cash", 100_000)), 100_000, false)
        PaymentRules.requireValid(listOf(PaymentLeg("cash", 120_000)), 100_000, false)
    }

    @Test
    fun validSplitUsesEachMethodOnce() {
        PaymentRules.requireValid(
            listOf(PaymentLeg("card", 60_000), PaymentLeg("cash", 45_000)),
            100_000,
            false,
        )
    }

    @Test
    fun nonCashCannotCreateChange() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentRules.requireValid(listOf(PaymentLeg("card", 100_001)), 100_000, false)
        }
    }

    @Test
    fun duplicateMethodsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentRules.requireValid(
                listOf(PaymentLeg("cash", 50_000), PaymentLeg("cash", 50_000)),
                100_000,
                false,
            )
        }
    }

    @Test
    fun customerBalanceMethodsRequireCustomer() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentRules.requireValid(listOf(PaymentLeg("credit", 100_000)), 100_000, false)
        }
        PaymentRules.requireValid(listOf(PaymentLeg("credit", 100_000)), 100_000, true)
    }
}
