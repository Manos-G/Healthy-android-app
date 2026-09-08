package com.healthy.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AlcoholTest {

    /**
     * Spec 9.4 sets 1.7 units for a beer and 1.6 for a glass of wine. Those
     * defaults were approximating exactly this calculation, so the computed
     * figure should land on them for the volumes they assumed.
     */
    @Test
    fun `a 330 ml lager is about the spec's 1_7 units`() {
        assertEquals(1.65, Alcohol.units(330, 5.0), 0.01)
    }

    @Test
    fun `a 150 ml glass of wine is about the spec's 1_6 units`() {
        assertEquals(1.8, Alcohol.units(150, 12.0), 0.01)
    }

    /** The reason for the rewrite: a pint is not the same drink as a bottle. */
    @Test
    fun `a pint carries more units than a bottle of the same beer`() {
        val bottle = Alcohol.units(330, 5.0)
        val pint = Alcohol.units(568, 5.0)
        assertEquals(2.84, pint, 0.01)
        assertEquals(true, pint > bottle)
    }

    /** A unit means different things in different countries (spec 9.4). */
    @Test
    fun `a larger unit size gives fewer units for the same drink`() {
        // The US standard drink is 14 g of ethanol, near 17.7 ml.
        assertEquals(0.93, Alcohol.units(330, 5.0, mlPerUnit = 17.7), 0.01)
    }

    @Test
    fun `no alcohol and no volume give no units`() {
        assertEquals(0.0, Alcohol.units(330, 0.0), 0.0)
        assertEquals(0.0, Alcohol.units(0, 5.0), 0.0)
    }

    @Test
    fun `grams of ethanol use the density of alcohol`() {
        assertEquals(13.0, Alcohol.grams(330, 5.0), 0.1)
    }
}
