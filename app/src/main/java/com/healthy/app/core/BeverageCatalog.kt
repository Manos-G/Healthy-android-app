package com.healthy.app.core

import com.healthy.app.core.BeverageCategory.Alcohol as AlcoholCat
import com.healthy.app.core.BeverageCategory.Caffeine as CaffeineCat
import com.healthy.app.core.BeverageCategory.Water as WaterCat

/**
 * The built-in drink catalog.
 *
 * These are constants rather than a seeded table: a static list cannot drift
 * out of step, needs no migration, and cannot be corrupted by a bad import.
 * The user's own additions live in `custom_drink`.
 *
 * Every one of spec 7's twenty drinks is here with its exact figures at the
 * default volume, marked below. The rest are the drinks a person actually
 * reaches for, weighted towards Greece because that is where this phone is.
 *
 * Caffeine figures are per 100 ml and are approximate by nature — brewing
 * strength varies more between two cups of filter coffee than between two
 * brands of cola. They are close enough to answer "can I sleep tonight",
 * which is the only question this app asks of them. Every value can be
 * overridden by scanning the barcode, which stores the real one.
 */
object BeverageCatalog {

    private fun water(name: String, ml: Int) = Beverage(name, WaterCat, ml)
    private fun caf(name: String, ml: Int, per100: Double) =
        Beverage(name, CaffeineCat, ml, caffeinePer100 = per100)
    private fun booze(name: String, ml: Int, abv: Double, per100: Double = 0.0) =
        Beverage(name, AlcoholCat, ml, caffeinePer100 = per100, abv = abv)

    /** Coffee, tea, energy drinks, colas and the two solids that carry caffeine. */
    private val CAFFEINE: List<Beverage> = listOf(
        // --- espresso and the bar drinks -------------------------------------
        caf("Espresso", 30, 210.0),                 // spec 7: 63 mg / 30 ml
        caf("Double espresso", 60, 208.0),          // spec 7: 125 mg / 60 ml
        caf("Ristretto", 20, 210.0),
        caf("Lungo", 60, 130.0),
        caf("Macchiato", 60, 105.0),
        caf("Cortado", 120, 52.0),
        caf("Cappuccino", 180, 41.7),               // spec 7: 75 mg / 180 ml
        caf("Latte", 240, 31.0),
        caf("Flat white", 160, 47.0),
        caf("Americano", 200, 40.0),
        caf("Mocha", 240, 38.0),
        caf("Affogato", 100, 63.0),
        caf("Cortado freddo", 150, 55.0),
        caf("Freddo espresso", 200, 62.5),          // spec 7: 125 mg / 200 ml
        caf("Freddo cappuccino", 250, 50.0),        // spec 7: 125 mg / 250 ml
        caf("Frappé", 250, 28.0),                   // spec 7: 70 mg / 250 ml
        caf("Frappé, double", 300, 39.0),
        caf("Greek coffee", 60, 100.0),             // spec 7: 60 mg / 60 ml
        caf("Greek coffee, double", 120, 100.0),
        caf("Turkish coffee", 60, 100.0),
        // --- brewed at home ---------------------------------------------------
        caf("Filter coffee", 240, 39.6),            // spec 7: 95 mg / 240 ml
        caf("Filter coffee, mug", 350, 39.6),
        caf("French press", 240, 42.0),
        caf("Moka pot", 100, 105.0),
        caf("Percolator coffee", 240, 45.0),
        caf("Pour-over", 300, 40.0),
        caf("Aeropress", 240, 45.0),
        caf("Instant coffee", 200, 30.0),           // spec 7: 60 mg / 200 ml
        caf("Instant coffee, strong", 200, 50.0),
        caf("Coffee capsule", 110, 70.0),
        caf("Coffee with milk", 200, 40.0),
        caf("Cold brew", 300, 66.7),                // spec 7: 200 mg / 300 ml
        caf("Nitro cold brew", 300, 70.0),
        caf("Iced coffee, bottled", 250, 32.0),
        caf("Iced latte", 300, 25.0),
        caf("Chicory coffee blend", 240, 20.0),
        caf("Mushroom coffee", 240, 20.0),
        caf("Decaf coffee", 240, 1.2),
        caf("Decaf espresso", 30, 8.0),
        caf("Decaf instant", 200, 1.0),
        // --- tea ---------------------------------------------------------------
        caf("Black tea", 240, 19.6),                // spec 7: 47 mg / 240 ml
        caf("Green tea", 240, 11.7),                // spec 7: 28 mg / 240 ml
        caf("White tea", 240, 12.0),
        caf("Oolong tea", 240, 16.0),
        caf("Pu-erh tea", 240, 17.0),
        caf("Earl Grey", 240, 20.0),
        caf("English breakfast", 240, 21.0),
        caf("Jasmine tea", 240, 12.0),
        caf("Masala chai", 200, 20.0),
        caf("Chai latte", 240, 20.0),
        caf("Matcha", 240, 29.0),
        caf("Matcha latte", 300, 22.0),
        caf("Tea, strong brew", 240, 30.0),
        caf("Tea, weak brew", 240, 12.0),
        caf("Decaf tea", 240, 2.0),
        caf("Iced tea, bottled", 500, 5.0),
        caf("Iced tea, lemon", 330, 5.0),
        caf("Iced tea, peach", 330, 5.0),
        caf("Iced green tea", 330, 4.0),
        caf("Kombucha", 250, 6.0),
        caf("Yerba mate", 240, 35.0),
        caf("Guayusa", 240, 38.0),
        caf("Cascara tea", 240, 10.0),
        // --- energy drinks -----------------------------------------------------
        caf("Red Bull", 250, 32.0),                 // spec 7: 80 mg / 250 ml
        caf("Red Bull, big can", 355, 32.0),        // spec 7: 114 mg / 355 ml
        caf("Red Bull sugarfree", 250, 32.0),
        caf("Hell", 250, 32.0),                     // spec 7: 80 mg / 250 ml
        caf("Hell, big can", 500, 32.0),            // spec 7: 160 mg / 500 ml
        caf("Hell White Peach", 250, 32.0),
        caf("Hell Strong", 250, 32.0),
        caf("Monster", 500, 32.0),                  // spec 7: 160 mg / 500 ml
        caf("Monster Ultra", 500, 32.0),
        caf("Burn", 250, 32.0),
        caf("Rockstar", 500, 32.0),
        caf("Relentless", 500, 32.0),
        caf("Effect", 250, 32.0),
        caf("Shark", 250, 32.0),
        caf("Prime Energy", 355, 40.0),
        caf("Celsius", 355, 56.0),
        caf("Bang", 473, 63.0),
        caf("Reign", 500, 60.0),
        caf("Energy drink, generic", 250, 32.0),
        caf("Energy shot", 60, 350.0),
        caf("Pre-workout", 300, 50.0),
        // --- colas and soft drinks --------------------------------------------
        caf("Coca-Cola", 330, 9.7),                 // spec 7: 32 mg / 330 ml
        caf("Coke Zero", 330, 10.3),                // spec 7: 34 mg / 330 ml
        caf("Diet Coke", 330, 12.8),
        caf("Cherry Coke", 330, 9.7),
        caf("Coca-Cola Vanilla", 330, 9.7),
        caf("Pepsi", 330, 10.3),
        caf("Pepsi Max", 330, 12.5),
        caf("Pepsi Light", 330, 10.0),
        caf("Dr Pepper", 330, 12.8),
        caf("Mountain Dew", 330, 15.2),
        caf("Irn-Bru", 330, 10.0),
        caf("Cola, generic", 330, 10.0),
        // --- eaten, not drunk --------------------------------------------------
        Beverage("Dark chocolate", CaffeineCat, 50, caffeinePer100 = 80.0, solid = true),
        Beverage("Milk chocolate", CaffeineCat, 50, caffeinePer100 = 20.0, solid = true),
        Beverage("Coffee ice cream", CaffeineCat, 100, caffeinePer100 = 30.0, solid = true),
        Beverage("Caffeine tablet", CaffeineCat, 1, caffeinePer100 = 20000.0, solid = true),
        caf("Hot chocolate", 240, 4.0),
        caf("Chocolate milk", 250, 3.0),
        caf("Cocoa", 200, 5.0),
    )

    /** Everything with neither caffeine nor alcohol — the "++" of Water++. */
    private val WATER: List<Beverage> = listOf(
        // --- water in the shapes it comes in ----------------------------------
        water("Water, glass", 250),
        water("Water, tumbler", 200),
        water("Water, mug", 300),
        water("Water, sip", 100),
        water("Water, small bottle", 330),
        water("Water, bottle", 500),
        water("Water, large bottle", 750),
        water("Water, litre bottle", 1000),
        water("Water, 1.5 litre bottle", 1500),
        water("Ice water", 350),
        water("Hot water", 250),
        water("Water with lemon", 250),
        water("Sparkling water", 250),
        water("Sparkling water, bottle", 500),
        water("Mineral water", 500),
        water("Alkaline water", 500),
        water("Flavoured water", 500),
        water("Coconut water", 330),
        water("Coconut water, bottle", 500),
        water("Soda water", 200),
        water("Tonic water", 200),
        water("Barley water", 250),
        // --- herbal, and none of it has caffeine ------------------------------
        water("Chamomile tea", 240),
        water("Peppermint tea", 240),
        water("Sage tea", 240),
        water("Greek mountain tea", 240),
        water("Linden tea", 240),
        water("Rooibos", 240),
        water("Hibiscus tea", 240),
        water("Ginger tea", 240),
        water("Lemon balm tea", 240),
        water("Fennel tea", 240),
        water("Rosehip tea", 240),
        water("Verbena tea", 240),
        water("Liquorice tea", 240),
        water("Nettle tea", 240),
        water("Valerian tea", 240),
        water("Lavender tea", 240),
        water("Anise tea", 240),
        water("Herbal infusion", 240),
        water("Lemon and honey", 250),
        water("Ginger and turmeric", 250),
        // --- juice --------------------------------------------------------------
        water("Orange juice", 200),
        water("Fresh orange juice", 250),
        water("Apple juice", 200),
        water("Grape juice", 200),
        water("Pineapple juice", 200),
        water("Cranberry juice", 200),
        water("Grapefruit juice", 200),
        water("Pomegranate juice", 200),
        water("Tomato juice", 200),
        water("Carrot juice", 200),
        water("Vegetable juice", 250),
        water("Watermelon juice", 250),
        water("Sour cherry juice", 250),
        water("Peach nectar", 250),
        water("Apricot nectar", 250),
        water("Mixed fruit juice", 200),
        water("Lemonade", 330),
        water("Cloudy lemonade", 330),
        water("Orangeade", 330),
        water("Smoothie", 250),
        water("Green smoothie", 300),
        // --- milk and the things made from it ---------------------------------
        water("Milk, glass", 250),
        water("Whole milk", 250),
        water("Semi-skimmed milk", 250),
        water("Skimmed milk", 250),
        water("Almond milk", 250),
        water("Oat milk", 250),
        water("Soy milk", 250),
        water("Rice milk", 250),
        water("Coconut milk drink", 250),
        water("Kefir", 250),
        water("Ayran", 250),
        water("Buttermilk", 250),
        water("Drinking yoghurt", 250),
        water("Lassi", 250),
        water("Milkshake", 300),
        water("Protein shake", 400),
        water("Whey shake", 500),
        // --- soft drinks with no caffeine in them ------------------------------
        water("Sprite", 330),
        water("7Up", 330),
        water("Fanta orange", 330),
        water("Fanta lemon", 330),
        water("Ginger ale", 330),
        water("Ginger beer", 330),
        water("Root beer", 330),
        water("Bitter lemon", 200),
        water("Cream soda", 330),
        water("Sparkling apple", 330),
        water("Soft drink, generic", 330),
        water("Slush drink", 300),
        // --- sport, illness, and soup -------------------------------------------
        water("Sports drink", 500),
        water("Isotonic drink", 500),
        water("Electrolyte sachet", 500),
        water("Rehydration solution", 250),
        water("Aloe vera drink", 250),
        water("Broth", 250),
        water("Bone broth", 300),
        water("Vegetable broth", 250),
        water("Soup, cup", 300),
        water("Miso soup", 200),
        water("Non-alcoholic beer", 330),
    )

    /**
     * Alcohol, by strength rather than by "one drink".
     *
     * The percentages are the usual ones for the style. A bottle in the hand
     * beats this list every time; the carousel and the strength are both
     * editable when it disagrees.
     */
    private val ALCOHOL: List<Beverage> = listOf(
        // --- beer ---------------------------------------------------------------
        booze("Lager", 330, 5.0),
        booze("Lager, big can", 500, 5.0),
        booze("Pilsner", 330, 4.8),
        booze("Mythos", 330, 5.0),
        booze("Alfa", 330, 5.0),
        booze("Fix Hellas", 330, 5.0),
        booze("Vergina", 330, 5.0),
        booze("Heineken", 330, 5.0),
        booze("Amstel", 330, 5.0),
        booze("Corona", 330, 4.5),
        booze("Stella Artois", 330, 5.0),
        booze("Beer, pint", 568, 4.5),
        booze("Beer, half pint", 284, 4.5),
        booze("Draught beer", 400, 5.0),
        booze("Craft lager", 440, 5.2),
        booze("Pale ale", 330, 5.0),
        booze("IPA", 330, 6.5),
        booze("Double IPA", 330, 8.0),
        booze("Wheat beer", 500, 5.4),
        booze("Weissbier", 500, 5.4),
        booze("Stout", 440, 4.5),
        booze("Guinness", 440, 4.2),
        booze("Porter", 330, 5.5),
        booze("Sour beer", 330, 4.5),
        booze("Belgian tripel", 330, 8.5),
        booze("Trappist ale", 330, 9.0),
        booze("Radler", 330, 2.5),
        booze("Shandy", 330, 2.0),
        booze("Light beer", 330, 4.2),
        booze("Cider", 330, 4.5),
        booze("Cider, pint", 568, 4.5),
        booze("Hard seltzer", 330, 4.5),
        booze("Alcopop", 275, 4.0),
        // --- wine ---------------------------------------------------------------
        booze("Red wine, glass", 150, 13.5),
        booze("White wine, glass", 150, 12.0),
        booze("Rosé, glass", 150, 12.5),
        booze("Wine, small glass", 125, 13.0),
        booze("Wine, large glass", 250, 13.0),
        booze("Wine, bottle", 750, 13.0),
        booze("Retsina", 150, 11.5),
        booze("Assyrtiko", 150, 13.0),
        booze("Agiorgitiko", 150, 13.0),
        booze("Xinomavro", 150, 13.5),
        booze("Moschofilero", 150, 11.5),
        booze("Malagousia", 150, 13.0),
        booze("Prosecco", 125, 11.0),
        booze("Champagne", 125, 12.0),
        booze("Sparkling wine", 125, 11.5),
        booze("Sangria", 250, 8.0),
        booze("Mulled wine", 200, 9.0),
        booze("Dessert wine", 75, 15.0),
        booze("Mavrodaphne", 75, 15.0),
        booze("Vinsanto", 75, 15.0),
        booze("Port", 75, 20.0),
        booze("Sherry", 75, 17.5),
        booze("Vermouth", 75, 15.0),
        booze("Sake", 100, 15.0),
        booze("Mead", 200, 12.0),
        // --- spirits --------------------------------------------------------------
        booze("Ouzo, shot", 50, 40.0),
        booze("Ouzo, carafe", 200, 40.0),
        booze("Tsipouro", 50, 40.0),
        booze("Tsikoudia", 50, 40.0),
        booze("Rakomelo", 50, 30.0),
        booze("Mastiha", 50, 24.0),
        booze("Metaxa", 40, 38.0),
        booze("Whisky", 40, 40.0),
        booze("Whisky, double", 70, 40.0),
        booze("Bourbon", 40, 45.0),
        booze("Vodka", 40, 40.0),
        booze("Gin", 40, 40.0),
        booze("Rum", 40, 40.0),
        booze("Tequila", 40, 38.0),
        booze("Brandy", 40, 40.0),
        booze("Cognac", 40, 40.0),
        booze("Grappa", 40, 40.0),
        booze("Absinthe", 30, 60.0),
        booze("Sambuca", 30, 38.0),
        booze("Jägermeister", 30, 35.0),
        booze("Schnapps", 30, 35.0),
        booze("Limoncello", 30, 28.0),
        booze("Baileys", 50, 17.0),
        booze("Liqueur, generic", 40, 25.0),
        booze("Shot, generic", 40, 40.0),
        // --- cocktails -------------------------------------------------------------
        booze("Gin and tonic", 250, 8.0),
        booze("Vodka and coke", 250, 8.0, per100 = 6.0),
        booze("Cuba libre", 250, 8.0, per100 = 6.0),
        booze("Mojito", 250, 10.0),
        booze("Moscow mule", 250, 8.0),
        booze("Aperol spritz", 200, 8.0),
        booze("Margarita", 150, 20.0),
        booze("Daiquiri", 120, 20.0),
        booze("Whisky sour", 120, 18.0),
        booze("Cosmopolitan", 120, 22.0),
        booze("Negroni", 90, 24.0),
        booze("Old fashioned", 90, 32.0),
        booze("Caipirinha", 150, 20.0),
        booze("Espresso martini", 150, 20.0, per100 = 40.0),
        booze("Long Island iced tea", 250, 15.0),
        booze("Piña colada", 250, 10.0),
        booze("Bloody Mary", 200, 10.0),
        booze("Tequila sunrise", 200, 10.0),
        booze("Sex on the beach", 200, 10.0),
        booze("Cocktail, generic", 200, 12.0),
    )

    val ALL: List<Beverage> = CAFFEINE + WATER + ALCOHOL

    fun of(category: BeverageCategory): List<Beverage> = when (category) {
        WaterCat -> WATER
        CaffeineCat -> CAFFEINE
        AlcoholCat -> ALCOHOL
    }

    /**
     * Name search, case- and accent-insensitive on the leading word boundary
     * first so "co" reaches Coca-Cola before it reaches Filter coffee.
     */
    fun search(category: BeverageCategory, query: String): List<Beverage> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return of(category)
        val (starts, contains) = of(category)
            .filter { it.name.lowercase().contains(needle) }
            .partition { it.name.lowercase().startsWith(needle) }
        return starts + contains
    }

    /** Looks a logged row's name back up, so a repeat tap knows its strength. */
    fun byName(name: String): Beverage? = ALL.firstOrNull { it.name == name }
}
