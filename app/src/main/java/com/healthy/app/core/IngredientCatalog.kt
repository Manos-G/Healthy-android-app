package com.healthy.app.core

import com.healthy.app.data.entity.Product

/**
 * Common cooking ingredients with their values for 100 g.
 *
 * Raised as an issue: building a recipe meant typing an ingredient's name into
 * a free-text box, which recorded a weight and no nutrients at all, so the
 * finished dish came out at zero calories. A name is not a food. This list
 * gives the everyday ingredients — eggs, flour, bananas, cocoa — real values,
 * so that picking one attaches nutrition rather than a label.
 *
 * Figures are per 100 g raw, drawn from standard food-composition tables. They
 * are typical values for the ingredient, not for any particular brand: a
 * scanned package always beats them, and scanning is offered alongside.
 *
 * Each becomes a [Product] with a stable `builtin-` barcode, so it flows
 * through the same machinery as a scanned item — resolved by barcode, counted
 * by [com.healthy.app.analysis.Nutrition], exported and restored like any
 * other. Nothing about recipes needed to learn a second kind of ingredient.
 */
data class CatalogIngredient(
    val name: String,
    val group: IngredientGroup,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugar: Double,
    val fat: Double,
    val saturatedFat: Double,
    val fibre: Double,
    val salt: Double,
) {
    /** A stable code, so the same ingredient is one row however often it is used. */
    val barcode: String get() = BUILTIN_PREFIX + slug(name)

    fun toProduct(): Product = Product(
        barcode = barcode,
        kind = Product.KIND_FOOD,
        name = name,
        kcal100 = kcal,
        protein100 = protein,
        carbs100 = carbs,
        sugar100 = sugar,
        fat100 = fat,
        saturatedFat100 = saturatedFat,
        fibre100 = fibre,
        salt100 = salt,
        source = Product.USER,
    )

    companion object {
        const val BUILTIN_PREFIX = "builtin-"

        fun slug(name: String): String =
            name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("").trim('-').replace(Regex("-+"), "-")
    }
}

enum class IngredientGroup(val label: String) {
    Dairy("Dairy and eggs"),
    Grains("Flour, grains and bread"),
    Baking("Baking and sweet"),
    Fats("Oils and fats"),
    Meat("Meat and fish"),
    Legumes("Pulses, nuts and seeds"),
    Vegetables("Vegetables"),
    Fruit("Fruit"),
    Pantry("Pantry and seasoning"),
}

object IngredientCatalog {

    private fun i(
        name: String,
        group: IngredientGroup,
        kcal: Double,
        protein: Double,
        carbs: Double,
        sugar: Double,
        fat: Double,
        satFat: Double,
        fibre: Double,
        salt: Double,
    ) = CatalogIngredient(name, group, kcal, protein, carbs, sugar, fat, satFat, fibre, salt)

    val ALL: List<CatalogIngredient> = listOf(
        // --- dairy and eggs -------------------------------------------------
        i("Egg, whole", IngredientGroup.Dairy, 143.0, 12.6, 0.7, 0.4, 9.5, 3.1, 0.0, 0.36),
        i("Egg white", IngredientGroup.Dairy, 52.0, 10.9, 0.7, 0.7, 0.2, 0.0, 0.0, 0.40),
        i("Egg yolk", IngredientGroup.Dairy, 322.0, 15.9, 3.6, 0.6, 26.5, 9.6, 0.0, 0.13),
        i("Milk, whole", IngredientGroup.Dairy, 61.0, 3.2, 4.8, 5.1, 3.3, 1.9, 0.0, 0.10),
        i("Milk, semi-skimmed", IngredientGroup.Dairy, 50.0, 3.4, 4.9, 4.9, 1.8, 1.1, 0.0, 0.10),
        i("Milk, skimmed", IngredientGroup.Dairy, 34.0, 3.4, 5.0, 5.0, 0.1, 0.1, 0.0, 0.10),
        i("Evaporated milk", IngredientGroup.Dairy, 135.0, 6.8, 10.0, 10.0, 7.6, 4.6, 0.0, 0.27),
        i("Condensed milk", IngredientGroup.Dairy, 321.0, 7.9, 54.4, 54.4, 8.7, 5.5, 0.0, 0.31),
        i("Greek yoghurt, 2%", IngredientGroup.Dairy, 73.0, 10.0, 3.9, 3.9, 1.9, 1.2, 0.0, 0.10),
        i("Greek yoghurt, full fat", IngredientGroup.Dairy, 97.0, 9.0, 4.0, 4.0, 5.0, 3.2, 0.0, 0.10),
        i("Yoghurt, plain", IngredientGroup.Dairy, 61.0, 3.5, 4.7, 4.7, 3.3, 2.1, 0.0, 0.10),
        i("Feta", IngredientGroup.Dairy, 264.0, 14.2, 4.1, 4.1, 21.3, 14.9, 0.0, 3.00),
        i("Graviera", IngredientGroup.Dairy, 400.0, 27.0, 1.0, 1.0, 32.0, 20.0, 0.0, 1.70),
        i("Kefalotyri", IngredientGroup.Dairy, 390.0, 26.0, 1.5, 1.0, 31.0, 19.0, 0.0, 2.40),
        i("Cheddar", IngredientGroup.Dairy, 403.0, 25.0, 1.3, 0.5, 33.0, 21.0, 0.0, 1.80),
        i("Mozzarella", IngredientGroup.Dairy, 280.0, 28.0, 3.1, 1.2, 17.0, 10.0, 0.0, 1.40),
        i("Parmesan", IngredientGroup.Dairy, 392.0, 36.0, 3.2, 0.8, 25.0, 16.0, 0.0, 1.60),
        i("Cream cheese", IngredientGroup.Dairy, 350.0, 6.0, 5.5, 3.8, 34.0, 20.0, 0.0, 0.80),
        i("Cottage cheese", IngredientGroup.Dairy, 98.0, 11.0, 3.4, 2.7, 4.3, 1.7, 0.0, 1.00),
        i("Butter", IngredientGroup.Dairy, 717.0, 0.9, 0.1, 0.1, 81.0, 51.0, 0.0, 1.60),
        i("Cream, single", IngredientGroup.Dairy, 195.0, 2.9, 4.0, 4.0, 19.0, 12.0, 0.0, 0.10),
        i("Cream, double", IngredientGroup.Dairy, 445.0, 2.1, 2.8, 2.8, 47.0, 29.0, 0.0, 0.10),

        // --- flour, grains and bread -----------------------------------------
        i("Flour, plain", IngredientGroup.Grains, 364.0, 10.3, 76.3, 0.3, 1.0, 0.2, 2.7, 0.01),
        i("Flour, self-raising", IngredientGroup.Grains, 354.0, 9.9, 74.2, 0.3, 1.0, 0.2, 2.7, 1.60),
        i("Flour, wholemeal", IngredientGroup.Grains, 340.0, 13.2, 72.0, 0.4, 2.5, 0.4, 10.7, 0.01),
        i("Flour, strong bread", IngredientGroup.Grains, 361.0, 12.0, 72.5, 0.3, 1.7, 0.3, 2.4, 0.01),
        i("Semolina", IngredientGroup.Grains, 360.0, 12.7, 72.8, 0.0, 1.1, 0.2, 3.9, 0.01),
        i("Cornflour", IngredientGroup.Grains, 381.0, 0.3, 91.0, 0.0, 0.1, 0.0, 0.9, 0.02),
        i("Rice, white raw", IngredientGroup.Grains, 365.0, 7.1, 80.0, 0.1, 0.7, 0.2, 1.3, 0.01),
        i("Rice, brown raw", IngredientGroup.Grains, 370.0, 7.9, 77.0, 0.9, 2.9, 0.6, 3.5, 0.01),
        i("Pasta, dry", IngredientGroup.Grains, 371.0, 13.0, 75.0, 2.7, 1.5, 0.3, 3.2, 0.02),
        i("Oats", IngredientGroup.Grains, 389.0, 16.9, 66.0, 0.0, 6.9, 1.2, 10.6, 0.01),
        i("Bulgur", IngredientGroup.Grains, 342.0, 12.3, 76.0, 0.4, 1.3, 0.2, 18.3, 0.02),
        i("Couscous, dry", IngredientGroup.Grains, 376.0, 12.8, 77.0, 0.2, 0.6, 0.1, 5.0, 0.02),
        i("Quinoa, raw", IngredientGroup.Grains, 368.0, 14.1, 64.0, 0.0, 6.1, 0.7, 7.0, 0.01),
        i("Bread, white", IngredientGroup.Grains, 265.0, 9.0, 49.0, 5.0, 3.2, 0.7, 2.7, 1.20),
        i("Bread, wholemeal", IngredientGroup.Grains, 247.0, 13.0, 41.0, 4.3, 3.4, 0.7, 7.0, 1.10),
        i("Breadcrumbs", IngredientGroup.Grains, 395.0, 13.4, 72.0, 6.0, 5.3, 1.2, 4.5, 1.40),
        i("Phyllo pastry", IngredientGroup.Grains, 290.0, 7.4, 52.0, 0.3, 5.5, 1.0, 1.8, 1.00),
        i("Puff pastry", IngredientGroup.Grains, 380.0, 5.2, 36.0, 1.0, 24.0, 12.0, 1.3, 0.80),
        i("Cornmeal", IngredientGroup.Grains, 370.0, 8.1, 79.0, 0.6, 3.6, 0.5, 7.3, 0.01),

        // --- baking and sweet --------------------------------------------------
        i("Sugar, white", IngredientGroup.Baking, 400.0, 0.0, 100.0, 100.0, 0.0, 0.0, 0.0, 0.00),
        i("Sugar, brown", IngredientGroup.Baking, 380.0, 0.0, 98.0, 97.0, 0.0, 0.0, 0.0, 0.03),
        i("Icing sugar", IngredientGroup.Baking, 389.0, 0.0, 100.0, 98.0, 0.0, 0.0, 0.0, 0.01),
        i("Honey", IngredientGroup.Baking, 304.0, 0.3, 82.0, 82.0, 0.0, 0.0, 0.2, 0.01),
        i("Maple syrup", IngredientGroup.Baking, 260.0, 0.0, 67.0, 60.0, 0.1, 0.0, 0.0, 0.03),
        i("Cocoa powder", IngredientGroup.Baking, 228.0, 19.6, 58.0, 1.8, 13.7, 8.1, 33.0, 0.05),
        i("Dark chocolate, 70%", IngredientGroup.Baking, 546.0, 7.8, 46.0, 24.0, 31.0, 18.0, 11.0, 0.02),
        i("Milk chocolate", IngredientGroup.Baking, 535.0, 7.6, 59.0, 52.0, 30.0, 18.0, 3.4, 0.08),
        i("Chocolate spread", IngredientGroup.Baking, 539.0, 6.3, 57.0, 56.0, 31.0, 10.0, 3.4, 0.10),
        i("Baking powder", IngredientGroup.Baking, 53.0, 0.0, 28.0, 0.0, 0.0, 0.0, 0.2, 26.00),
        i("Bicarbonate of soda", IngredientGroup.Baking, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 68.00),
        i("Yeast, dried", IngredientGroup.Baking, 325.0, 40.0, 41.0, 0.0, 7.6, 1.0, 27.0, 0.13),
        i("Vanilla extract", IngredientGroup.Baking, 288.0, 0.1, 12.7, 12.7, 0.1, 0.0, 0.0, 0.02),
        i("Desiccated coconut", IngredientGroup.Baking, 660.0, 6.9, 24.0, 7.4, 65.0, 57.0, 16.0, 0.09),

        // --- oils and fats -----------------------------------------------------
        i("Olive oil", IngredientGroup.Fats, 884.0, 0.0, 0.0, 0.0, 100.0, 14.0, 0.0, 0.00),
        i("Sunflower oil", IngredientGroup.Fats, 884.0, 0.0, 0.0, 0.0, 100.0, 11.0, 0.0, 0.00),
        i("Coconut oil", IngredientGroup.Fats, 892.0, 0.0, 0.0, 0.0, 99.0, 87.0, 0.0, 0.00),
        i("Margarine", IngredientGroup.Fats, 717.0, 0.2, 0.7, 0.0, 80.0, 16.0, 0.0, 1.50),
        i("Tahini", IngredientGroup.Fats, 595.0, 17.0, 21.0, 0.5, 54.0, 7.6, 9.3, 0.09),
        i("Mayonnaise", IngredientGroup.Fats, 680.0, 1.0, 0.6, 0.6, 75.0, 11.0, 0.0, 1.20),

        // --- meat and fish -------------------------------------------------------
        i("Chicken breast, raw", IngredientGroup.Meat, 120.0, 22.5, 0.0, 0.0, 2.6, 0.7, 0.0, 0.16),
        i("Chicken thigh, raw", IngredientGroup.Meat, 177.0, 18.0, 0.0, 0.0, 11.0, 3.1, 0.0, 0.20),
        i("Beef mince, 20% fat", IngredientGroup.Meat, 254.0, 17.0, 0.0, 0.0, 20.0, 7.7, 0.0, 0.20),
        i("Beef mince, 5% fat", IngredientGroup.Meat, 137.0, 21.0, 0.0, 0.0, 5.0, 2.2, 0.0, 0.20),
        i("Beef steak, raw", IngredientGroup.Meat, 190.0, 21.0, 0.0, 0.0, 11.0, 4.5, 0.0, 0.15),
        i("Pork chop, raw", IngredientGroup.Meat, 218.0, 21.0, 0.0, 0.0, 14.0, 5.0, 0.0, 0.15),
        i("Pork mince", IngredientGroup.Meat, 263.0, 17.0, 0.0, 0.0, 21.0, 7.9, 0.0, 0.18),
        i("Lamb, raw", IngredientGroup.Meat, 282.0, 17.0, 0.0, 0.0, 23.0, 10.0, 0.0, 0.18),
        i("Bacon", IngredientGroup.Meat, 393.0, 12.0, 1.4, 0.0, 37.0, 12.0, 0.0, 3.50),
        i("Sausage", IngredientGroup.Meat, 300.0, 12.0, 4.0, 1.0, 25.0, 9.0, 0.0, 2.00),
        i("Ham", IngredientGroup.Meat, 145.0, 18.0, 1.5, 1.0, 7.0, 2.4, 0.0, 3.00),
        i("Salmon, raw", IngredientGroup.Meat, 208.0, 20.0, 0.0, 0.0, 13.0, 3.1, 0.0, 0.14),
        i("Cod, raw", IngredientGroup.Meat, 82.0, 18.0, 0.0, 0.0, 0.7, 0.1, 0.0, 0.20),
        i("Tuna, canned in water", IngredientGroup.Meat, 116.0, 26.0, 0.0, 0.0, 1.0, 0.3, 0.0, 0.80),
        i("Prawns, raw", IngredientGroup.Meat, 99.0, 24.0, 0.2, 0.0, 0.3, 0.1, 0.0, 0.60),
        i("Anchovies", IngredientGroup.Meat, 210.0, 29.0, 0.0, 0.0, 10.0, 2.2, 0.0, 3.50),
        i("Sardines, canned", IngredientGroup.Meat, 208.0, 25.0, 0.0, 0.0, 11.0, 1.5, 0.0, 1.00),
        i("Octopus, raw", IngredientGroup.Meat, 82.0, 15.0, 2.2, 0.0, 1.0, 0.2, 0.0, 0.60),

        // --- pulses, nuts and seeds -----------------------------------------------
        i("Lentils, dry", IngredientGroup.Legumes, 352.0, 25.0, 63.0, 2.0, 1.1, 0.2, 11.0, 0.02),
        i("Chickpeas, dry", IngredientGroup.Legumes, 378.0, 20.0, 63.0, 11.0, 6.0, 0.6, 12.0, 0.06),
        i("Chickpeas, canned", IngredientGroup.Legumes, 139.0, 7.1, 22.0, 4.0, 2.6, 0.3, 6.4, 0.70),
        i("White beans, dry", IngredientGroup.Legumes, 333.0, 23.0, 60.0, 2.1, 0.9, 0.2, 15.0, 0.02),
        i("Black-eyed beans, dry", IngredientGroup.Legumes, 336.0, 24.0, 60.0, 6.9, 1.3, 0.3, 11.0, 0.04),
        i("Split peas, dry", IngredientGroup.Legumes, 341.0, 25.0, 60.0, 8.0, 1.2, 0.2, 26.0, 0.04),
        i("Tofu", IngredientGroup.Legumes, 76.0, 8.0, 1.9, 0.6, 4.8, 0.7, 0.3, 0.02),
        i("Peanut butter", IngredientGroup.Legumes, 588.0, 25.0, 20.0, 9.0, 50.0, 10.0, 6.0, 0.90),
        i("Almonds", IngredientGroup.Legumes, 579.0, 21.0, 22.0, 4.4, 50.0, 3.8, 12.5, 0.00),
        i("Walnuts", IngredientGroup.Legumes, 654.0, 15.0, 14.0, 2.6, 65.0, 6.1, 6.7, 0.01),
        i("Cashews", IngredientGroup.Legumes, 553.0, 18.0, 30.0, 5.9, 44.0, 7.8, 3.3, 0.03),
        i("Peanuts", IngredientGroup.Legumes, 567.0, 26.0, 16.0, 4.0, 49.0, 6.3, 8.5, 0.04),
        i("Pistachios", IngredientGroup.Legumes, 560.0, 20.0, 28.0, 7.7, 45.0, 5.9, 10.0, 0.01),
        i("Sunflower seeds", IngredientGroup.Legumes, 584.0, 21.0, 20.0, 2.6, 51.0, 4.5, 8.6, 0.02),
        i("Sesame seeds", IngredientGroup.Legumes, 573.0, 18.0, 23.0, 0.3, 50.0, 7.0, 12.0, 0.03),

        // --- vegetables --------------------------------------------------------------
        i("Onion", IngredientGroup.Vegetables, 40.0, 1.1, 9.3, 4.2, 0.1, 0.0, 1.7, 0.01),
        i("Spring onion", IngredientGroup.Vegetables, 32.0, 1.8, 7.3, 2.3, 0.2, 0.0, 2.6, 0.04),
        i("Garlic", IngredientGroup.Vegetables, 149.0, 6.4, 33.0, 1.0, 0.5, 0.1, 2.1, 0.04),
        i("Leek", IngredientGroup.Vegetables, 61.0, 1.5, 14.0, 3.9, 0.3, 0.0, 1.8, 0.05),
        i("Tomato", IngredientGroup.Vegetables, 18.0, 0.9, 3.9, 2.6, 0.2, 0.0, 1.2, 0.01),
        i("Tomatoes, tinned", IngredientGroup.Vegetables, 32.0, 1.6, 5.2, 3.6, 0.3, 0.0, 1.3, 0.03),
        i("Tomato paste", IngredientGroup.Vegetables, 82.0, 4.3, 19.0, 12.0, 0.5, 0.1, 4.1, 0.10),
        i("Potato", IngredientGroup.Vegetables, 77.0, 2.0, 17.0, 0.8, 0.1, 0.0, 2.2, 0.02),
        i("Sweet potato", IngredientGroup.Vegetables, 86.0, 1.6, 20.0, 4.2, 0.1, 0.0, 3.0, 0.14),
        i("Carrot", IngredientGroup.Vegetables, 41.0, 0.9, 9.6, 4.7, 0.2, 0.0, 2.8, 0.17),
        i("Courgette", IngredientGroup.Vegetables, 17.0, 1.2, 3.1, 2.5, 0.3, 0.1, 1.0, 0.02),
        i("Aubergine", IngredientGroup.Vegetables, 25.0, 1.0, 5.9, 3.5, 0.2, 0.0, 3.0, 0.01),
        i("Pepper, red", IngredientGroup.Vegetables, 31.0, 1.0, 6.0, 4.2, 0.3, 0.1, 2.1, 0.01),
        i("Pepper, green", IngredientGroup.Vegetables, 20.0, 0.9, 4.6, 2.4, 0.2, 0.1, 1.7, 0.01),
        i("Spinach", IngredientGroup.Vegetables, 23.0, 2.9, 3.6, 0.4, 0.4, 0.1, 2.2, 0.20),
        i("Broccoli", IngredientGroup.Vegetables, 34.0, 2.8, 6.6, 1.7, 0.4, 0.1, 2.6, 0.08),
        i("Cauliflower", IngredientGroup.Vegetables, 25.0, 1.9, 5.0, 1.9, 0.3, 0.1, 2.0, 0.08),
        i("Mushrooms", IngredientGroup.Vegetables, 22.0, 3.1, 3.3, 2.0, 0.3, 0.1, 1.0, 0.01),
        i("Cucumber", IngredientGroup.Vegetables, 15.0, 0.7, 3.6, 1.7, 0.1, 0.0, 0.5, 0.01),
        i("Lettuce", IngredientGroup.Vegetables, 15.0, 1.4, 2.9, 0.8, 0.2, 0.0, 1.3, 0.07),
        i("Cabbage", IngredientGroup.Vegetables, 25.0, 1.3, 5.8, 3.2, 0.1, 0.0, 2.5, 0.04),
        i("Green beans", IngredientGroup.Vegetables, 31.0, 1.8, 7.0, 3.3, 0.2, 0.0, 3.4, 0.02),
        i("Peas", IngredientGroup.Vegetables, 81.0, 5.4, 14.0, 5.7, 0.4, 0.1, 5.7, 0.01),
        i("Okra", IngredientGroup.Vegetables, 33.0, 1.9, 7.0, 1.5, 0.2, 0.0, 3.2, 0.02),
        i("Olives, black", IngredientGroup.Vegetables, 115.0, 0.8, 6.0, 0.0, 11.0, 1.4, 3.2, 3.30),
        i("Olives, green", IngredientGroup.Vegetables, 145.0, 1.0, 3.8, 0.5, 15.0, 2.0, 3.3, 3.50),

        // --- fruit -----------------------------------------------------------------
        i("Banana", IngredientGroup.Fruit, 89.0, 1.1, 23.0, 12.0, 0.3, 0.1, 2.6, 0.00),
        i("Apple", IngredientGroup.Fruit, 52.0, 0.3, 14.0, 10.0, 0.2, 0.0, 2.4, 0.00),
        i("Orange", IngredientGroup.Fruit, 47.0, 0.9, 12.0, 9.0, 0.1, 0.0, 2.4, 0.00),
        i("Lemon juice", IngredientGroup.Fruit, 22.0, 0.4, 6.9, 2.5, 0.2, 0.0, 0.3, 0.00),
        i("Strawberries", IngredientGroup.Fruit, 32.0, 0.7, 7.7, 4.9, 0.3, 0.0, 2.0, 0.00),
        i("Blueberries", IngredientGroup.Fruit, 57.0, 0.7, 14.0, 10.0, 0.3, 0.0, 2.4, 0.00),
        i("Grapes", IngredientGroup.Fruit, 69.0, 0.7, 18.0, 16.0, 0.2, 0.1, 0.9, 0.01),
        i("Peach", IngredientGroup.Fruit, 39.0, 0.9, 9.5, 8.4, 0.3, 0.0, 1.5, 0.00),
        i("Watermelon", IngredientGroup.Fruit, 30.0, 0.6, 7.6, 6.2, 0.2, 0.0, 0.4, 0.00),
        i("Avocado", IngredientGroup.Fruit, 160.0, 2.0, 8.5, 0.7, 15.0, 2.1, 6.7, 0.02),
        i("Raisins", IngredientGroup.Fruit, 299.0, 3.1, 79.0, 59.0, 0.5, 0.1, 3.7, 0.03),
        i("Dates", IngredientGroup.Fruit, 282.0, 2.5, 75.0, 63.0, 0.4, 0.0, 8.0, 0.01),

        // --- pantry and seasoning ------------------------------------------------------
        i("Salt", IngredientGroup.Pantry, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.00),
        i("Black pepper", IngredientGroup.Pantry, 251.0, 10.0, 64.0, 0.6, 3.3, 1.4, 25.0, 0.05),
        i("Oregano, dried", IngredientGroup.Pantry, 265.0, 9.0, 69.0, 4.1, 4.3, 1.6, 42.0, 0.06),
        i("Cinnamon", IngredientGroup.Pantry, 247.0, 4.0, 81.0, 2.2, 1.2, 0.3, 53.0, 0.03),
        i("Paprika", IngredientGroup.Pantry, 282.0, 14.0, 54.0, 10.0, 13.0, 2.1, 35.0, 0.09),
        i("Cumin", IngredientGroup.Pantry, 375.0, 18.0, 44.0, 2.3, 22.0, 1.5, 11.0, 0.42),
        i("Soy sauce", IngredientGroup.Pantry, 53.0, 8.0, 4.9, 0.4, 0.6, 0.1, 0.8, 14.00),
        i("Vinegar", IngredientGroup.Pantry, 18.0, 0.0, 0.9, 0.4, 0.0, 0.0, 0.0, 0.02),
        i("Mustard", IngredientGroup.Pantry, 66.0, 4.4, 5.8, 1.6, 3.4, 0.2, 3.3, 3.30),
        i("Ketchup", IngredientGroup.Pantry, 101.0, 1.2, 26.0, 21.0, 0.1, 0.0, 0.3, 2.50),
        i("Stock cube", IngredientGroup.Pantry, 200.0, 10.0, 20.0, 2.0, 10.0, 4.5, 0.0, 40.00),
        i("Coconut milk", IngredientGroup.Pantry, 230.0, 2.3, 5.5, 3.3, 24.0, 21.0, 2.2, 0.03),
        i("White wine", IngredientGroup.Pantry, 82.0, 0.1, 2.6, 1.0, 0.0, 0.0, 0.0, 0.01),
        i("Water", IngredientGroup.Pantry, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.00),
    )

    val PRODUCTS: List<Product> by lazy { ALL.map { it.toProduct() } }

    fun byBarcode(barcode: String): CatalogIngredient? = ALL.firstOrNull { it.barcode == barcode }

    /** Prefix matches first, so "ba" reaches Banana before Strawberries. */
    fun search(query: String): List<CatalogIngredient> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return ALL
        val (starts, contains) = ALL
            .filter { it.name.lowercase().contains(needle) }
            .partition { it.name.lowercase().startsWith(needle) }
        return starts + contains
    }
}
