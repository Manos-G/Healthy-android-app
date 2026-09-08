package com.healthy.app.analysis

/**
 * Nutrient targets (spec 16.4 and 16.5).
 *
 * Every one is off until the user asks for it, and none of them judges: no
 * red above a ceiling, no notification, no streak. A bar and a number, and the
 * seven-day mean beside today's value, because one day means little and a week
 * means something.
 */
object NutrientTargets {

    enum class Kind {
        /** Aim to reach it. */
        Floor,

        /** Aim to stay under it. */
        Ceiling,
    }

    data class Target(
        val name: String,
        val kind: Kind,
        val amount: Double,
        val unit: String,
        /** Where the figure came from, for the sources screen. */
        val basis: String,
    )

    data class Progress(
        val target: Target,
        val today: Double,
        val weekMean: Double,
        val percent: Int,
    ) {
        /**
         * True when today's figure is on the right side of the target. Used to
         * pick a neutral or a positive colour, never a warning one — spec 16.5
         * forbids colouring a bar red.
         */
        val met: Boolean
            get() = when (target.kind) {
                Kind.Floor -> today >= target.amount
                Kind.Ceiling -> today <= target.amount
            }
    }

    const val PROTEIN_G_PER_KG = 1.6
    const val FIBRE_G = 30.0
    const val SATURATED_FAT_PERCENT_OF_ENERGY = 10.0
    const val SUGAR_PERCENT_OF_ENERGY = 10.0
    const val SALT_G = 5.0

    /** Kilocalories in a gram, for the two ceilings set as a share of energy. */
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_CARB = 4.0

    /**
     * The defaults from spec 16.4, scaled to this user.
     *
     * Two of them depend on body weight and energy target, so they cannot be
     * constants: a protein floor of 1.6 g per kilogram means nothing without
     * the kilograms, and a sugar ceiling of 10 percent of energy means nothing
     * without the energy.
     */
    fun defaults(bodyWeightKg: Double?, energyTargetKcal: Int?): List<Target> = buildList {
        bodyWeightKg?.let { kg ->
            add(
                Target(
                    name = "Protein",
                    kind = Kind.Floor,
                    amount = kg * PROTEIN_G_PER_KG,
                    unit = "g",
                    basis = "$PROTEIN_G_PER_KG g for each kg of body weight",
                )
            )
        }
        add(Target("Fibre", Kind.Floor, FIBRE_G, "g", "a common daily recommendation"))
        energyTargetKcal?.let { kcal ->
            add(
                Target(
                    name = "Saturated fat",
                    kind = Kind.Ceiling,
                    amount = kcal * SATURATED_FAT_PERCENT_OF_ENERGY / 100.0 / KCAL_PER_G_FAT,
                    unit = "g",
                    basis = "${SATURATED_FAT_PERCENT_OF_ENERGY.toInt()} percent of your energy target",
                )
            )
            add(
                Target(
                    name = "Sugar",
                    kind = Kind.Ceiling,
                    amount = kcal * SUGAR_PERCENT_OF_ENERGY / 100.0 / KCAL_PER_G_CARB,
                    unit = "g",
                    basis = "${SUGAR_PERCENT_OF_ENERGY.toInt()} percent of your energy target",
                )
            )
        }
        add(Target("Salt", Kind.Ceiling, SALT_G, "g", "a common daily upper limit"))
    }

    /**
     * Today against each target, with the seven-day mean beside it (spec 16.5).
     *
     * The mean is taken over the days that were actually logged. Counting an
     * unlogged day as zero would make a floor look missed and a ceiling look
     * comfortably met, both of which are wrong.
     */
    fun progress(
        targets: List<Target>,
        today: Nutrition.Totals,
        recentDays: List<Nutrition.Totals>,
    ): List<Progress> = targets.map { target ->
        val todayValue = valueOf(target.name, today)
        val logged = recentDays.filter { it.kcal > 0 }
        val mean = if (logged.isEmpty()) 0.0 else logged.sumOf { valueOf(target.name, it) } / logged.size
        Progress(
            target = target,
            today = todayValue,
            weekMean = mean,
            percent = if (target.amount > 0) {
                Math.round(todayValue / target.amount * 100).toInt()
            } else {
                0
            },
        )
    }

    private fun valueOf(name: String, totals: Nutrition.Totals): Double = when (name) {
        "Protein" -> totals.protein
        "Fibre" -> totals.fibre
        "Saturated fat" -> totals.saturatedFat
        "Sugar" -> totals.sugar
        "Salt" -> totals.salt
        else -> 0.0
    }
}
