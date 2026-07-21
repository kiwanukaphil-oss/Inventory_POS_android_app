package com.kline.inventorypos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationModelsTest {
    @Test
    fun totalsAndReadinessUseCanonicalChannelValues() {
        val reconciliation = sample(
            listOf(
                ReconciliationChannel("1", "cash", 500_000, 499_000, 0, -1_000, "Count checked", "Pat", "now"),
                ReconciliationChannel("2", "visa", 250_000, 255_000, 5_000, 0, null, "Pat", "now"),
            ),
        )

        assertEquals(750_000, reconciliation.systemTotal)
        assertEquals(754_000, reconciliation.externalTotal)
        assertEquals(-1_000, reconciliation.variance)
        assertTrue(reconciliation.allChannelsVerified)
    }

    @Test
    fun oneUnverifiedChannelBlocksReadiness() {
        val reconciliation = sample(listOf(ReconciliationChannel("1", "cash", 500_000, null, 0, 0, null, null, null)))

        assertFalse(reconciliation.allChannelsVerified)
    }

    private fun sample(channels: List<ReconciliationChannel>) = Reconciliation(
        "r1", "2026-07-21", "counting", null, null, null, null, channels, emptyList(), emptyList(),
    )
}
