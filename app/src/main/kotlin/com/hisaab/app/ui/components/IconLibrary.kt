package com.hisaab.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons by key, so a sub-category or a category of the user's own can store its icon as text. [GENERAL] is
 * the everyday set offered first in the icon picker; [CATEGORY] holds the ones the built-in sub-categories use.
 */
object IconLibrary {
    val CATEGORY: Map<String, ImageVector> = linkedMapOf(
        "delivery_dining" to Icons.Filled.DeliveryDining, "restaurant" to Icons.Filled.Restaurant, "local_cafe" to Icons.Filled.LocalCafe,
        "fastfood" to Icons.Filled.Fastfood, "bakery_dining" to Icons.Filled.BakeryDining, "local_bar" to Icons.Filled.LocalBar,
        "bolt" to Icons.Filled.Bolt, "local_grocery_store" to Icons.Filled.LocalGroceryStore, "eco" to Icons.Filled.Eco, "egg" to Icons.Filled.Egg,
        "storefront" to Icons.Filled.Storefront, "local_taxi" to Icons.Filled.LocalTaxi, "directions_subway" to Icons.Filled.DirectionsSubway,
        "local_parking" to Icons.Filled.LocalParking, "car_repair" to Icons.Filled.CarRepair, "local_gas_station" to Icons.Filled.LocalGasStation,
        "ev_station" to Icons.Filled.EvStation, "propane" to Icons.Filled.Propane, "shopping_bag" to Icons.Filled.ShoppingBag,
        "checkroom" to Icons.Filled.Checkroom, "devices" to Icons.Filled.Devices, "chair" to Icons.Filled.Chair,
        "sports_cricket" to Icons.Filled.SportsCricket, "menu_book" to Icons.Filled.MenuBook, "smartphone" to Icons.Filled.Smartphone,
        "electric_bolt" to Icons.Filled.ElectricBolt, "router" to Icons.Filled.Router, "propane_tank" to Icons.Filled.PropaneTank,
        "water_drop" to Icons.Filled.WaterDrop, "apartment" to Icons.Filled.Apartment, "movie" to Icons.Filled.Movie,
        "confirmation_number" to Icons.Filled.ConfirmationNumber, "sports_esports" to Icons.Filled.SportsEsports, "attractions" to Icons.Filled.Attractions,
        "flight" to Icons.Filled.Flight, "train" to Icons.Filled.Train, "hotel" to Icons.Filled.Hotel, "directions_bus" to Icons.Filled.DirectionsBus,
        "luggage" to Icons.Filled.Luggage, "medication" to Icons.Filled.Medication, "local_hospital" to Icons.Filled.LocalHospital,
        "biotech" to Icons.Filled.Biotech, "fitness_center" to Icons.Filled.FitnessCenter, "spa" to Icons.Filled.Spa, "visibility" to Icons.Filled.Visibility,
        "school" to Icons.Filled.School, "account_balance" to Icons.Filled.AccountBalance, "home" to Icons.Filled.Home, "bed" to Icons.Filled.Bed,
        "favorite" to Icons.Filled.Favorite, "health_and_safety" to Icons.Filled.HealthAndSafety, "directions_car" to Icons.Filled.DirectionsCar,
        "credit_card" to Icons.Filled.CreditCard, "payments" to Icons.Filled.Payments, "pie_chart" to Icons.Filled.PieChart,
        "show_chart" to Icons.Filled.ShowChart, "savings" to Icons.Filled.Savings, "diamond" to Icons.Filled.Diamond, "live_tv" to Icons.Filled.LiveTv,
        "music_note" to Icons.Filled.MusicNote, "cloud" to Icons.Filled.Cloud, "newspaper" to Icons.Filled.Newspaper,
        "card_membership" to Icons.Filled.CardMembership, "content_cut" to Icons.Filled.ContentCut, "face" to Icons.Filled.Face,
        "handyman" to Icons.Filled.Handyman, "cleaning_services" to Icons.Filled.CleaningServices, "local_laundry_service" to Icons.Filled.LocalLaundryService,
        "kitchen" to Icons.Filled.Kitchen, "local_florist" to Icons.Filled.LocalFlorist, "redeem" to Icons.Filled.Redeem,
        "temple_hindu" to Icons.Filled.TempleHindu, "volunteer_activism" to Icons.Filled.VolunteerActivism, "receipt_long" to Icons.Filled.ReceiptLong,
        "request_quote" to Icons.Filled.RequestQuote, "house" to Icons.Filled.House, "warning" to Icons.Filled.Warning, "atm" to Icons.Filled.Atm,
    )

    /** Everyday icons for categories the user makes. */
    val GENERAL: Map<String, ImageVector> = linkedMapOf(
        "category" to Icons.Filled.Category, "label" to Icons.Filled.Label, "star" to Icons.Filled.Star, "shopping_cart" to Icons.Filled.ShoppingCart,
        "pets" to Icons.Filled.Pets, "child_care" to Icons.Filled.ChildCare, "toys" to Icons.Filled.Toys, "elderly" to Icons.Filled.Elderly,
        "celebration" to Icons.Filled.Celebration, "cake" to Icons.Filled.Cake, "work" to Icons.Filled.Work, "laptop" to Icons.Filled.Laptop,
        "sports_soccer" to Icons.Filled.SportsSoccer, "self_improvement" to Icons.Filled.SelfImprovement, "hiking" to Icons.Filled.Hiking,
        "pool" to Icons.Filled.Pool, "park" to Icons.Filled.Park, "beach_access" to Icons.Filled.BeachAccess, "weekend" to Icons.Filled.Weekend,
        "brush" to Icons.Filled.Brush, "palette" to Icons.Filled.Palette, "photo_camera" to Icons.Filled.PhotoCamera, "headphones" to Icons.Filled.Headphones,
        "watch" to Icons.Filled.Watch, "lightbulb" to Icons.Filled.Lightbulb, "wifi" to Icons.Filled.Wifi, "local_pharmacy" to Icons.Filled.LocalPharmacy,
        "vaccines" to Icons.Filled.Vaccines, "two_wheeler" to Icons.Filled.TwoWheeler, "electric_car" to Icons.Filled.ElectricCar, "tram" to Icons.Filled.Tram,
        "sailing" to Icons.Filled.Sailing, "agriculture" to Icons.Filled.Agriculture, "construction" to Icons.Filled.Construction, "build" to Icons.Filled.Build,
        "grass" to Icons.Filled.Grass, "liquor" to Icons.Filled.Liquor, "lunch_dining" to Icons.Filled.LunchDining, "icecream" to Icons.Filled.Icecream,
        "stadium" to Icons.Filled.Stadium, "theater_comedy" to Icons.Filled.TheaterComedy, "diversity" to Icons.Filled.Diversity3,
        "hearing" to Icons.Filled.Hearing, "nightlife" to Icons.Filled.Nightlife, "subscriptions" to Icons.Filled.Subscriptions, "sell" to Icons.Filled.Sell,
    )

    private val ALL: Map<String, ImageVector> = GENERAL + CATEGORY

    /** Every icon, general ones first, for the picker. */
    val pickable: List<Pair<String, ImageVector>> get() = ALL.toList()

    fun get(key: String?): ImageVector = key?.let { ALL[it] } ?: Icons.Filled.Label
}
