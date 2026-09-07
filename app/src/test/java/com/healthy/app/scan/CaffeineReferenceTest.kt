package com.healthy.app.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Starting figures when Open Food Facts has no caffeine value (spec 11.4). */
class CaffeineReferenceTest {

    /** The two drinks actually to hand for testing. */
    @Test
    fun `Hell White Peach reads as an energy drink`() {
        val guess = assertNotNull(CaffeineReference.forName("Hell White Peach"))
            .let { CaffeineReference.forName("Hell White Peach")!! }
        assertEquals(32, guess.mgPer100Ml)
        // 32 mg per 100 ml on a 250 ml can is 80 mg, the figure on the tin.
        assertEquals(80, OpenFoodFacts.totalMg(guess.mgPer100Ml, 100, 250))
    }

    @Test
    fun `Pepsi Max reads as a cola`() {
        val guess = CaffeineReference.forName("Pepsi Max")!!
        assertEquals(11, guess.mgPer100Ml)
        assertEquals(36, OpenFoodFacts.totalMg(guess.mgPer100Ml, 100, 330))
    }

    @Test
    fun `the brand is matched as well as the name`() {
        assertEquals(32, CaffeineReference.forName("Monster Energy Ultra")!!.mgPer100Ml)
        assertEquals(11, CaffeineReference.forName("Coca-Cola Zero")!!.mgPer100Ml)
    }

    @Test
    fun `a coffee is denser than a cola`() {
        val coffee = CaffeineReference.forName("Filter coffee")!!.mgPer100Ml
        val cola = CaffeineReference.forName("Cola")!!.mgPer100Ml
        assert(coffee > cola)
    }

    /** A wrong guess is worse than none: it would be accepted unchecked. */
    @Test
    fun `something unrecognised gets no guess at all`() {
        assertNull(CaffeineReference.forName("Sparkling water"))
        assertNull(CaffeineReference.forName("Orange juice"))
        assertNull(CaffeineReference.forName(""))
        assertNull(CaffeineReference.forName(null))
    }

    @Test
    fun `every guess says what it was based on`() {
        listOf("Hell", "Pepsi", "Green tea", "Espresso").forEach { name ->
            val guess = CaffeineReference.forName(name)!!
            assert(guess.basis.isNotBlank()) { "$name has no basis" }
            assert(guess.basis.startsWith("a ") || guess.basis.startsWith("typical")) {
                "$name should read as a generality, not a fact: ${guess.basis}"
            }
        }
    }
}
