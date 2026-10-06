package com.hisaab.parser.merchant

import com.hisaab.parser.model.Category

/**
 * Known merchants: the words that identify them, their display name, and their category.
 * Keys are uppercase. A single-word key must equal one token of the merchant string; a
 * multi-word key must appear as a phrase. Tokens prevent "OLA" from matching "COCA COLA".
 */
object MerchantDirectory {
    data class Entry(val name: String, val category: Category)

    private val entries: List<Pair<List<String>, Entry>> = listOf(
        // Food
        listOf("SWIGGY INSTAMART", "INSTAMART") to Entry("Swiggy Instamart", Category.GROCERIES),
        listOf("SWIGGY", "BUNDL") to Entry("Swiggy", Category.FOOD),
        listOf("ZOMATO") to Entry("Zomato", Category.FOOD),
        listOf("EATSURE", "FAASOS") to Entry("EatSure", Category.FOOD),
        listOf("DOMINOS", "JUBILANT") to Entry("Domino's", Category.FOOD),
        listOf("MCDONALDS", "MCDONALD") to Entry("McDonald's", Category.FOOD),
        listOf("KFC") to Entry("KFC", Category.FOOD),
        listOf("STARBUCKS") to Entry("Starbucks", Category.FOOD),
        listOf("HALDIRAM", "HALDIRAMS") to Entry("Haldiram's", Category.FOOD),
        // Groceries
        listOf("BIGBASKET", "BIG BASKET", "INNOVATIVE RETAIL") to Entry("BigBasket", Category.GROCERIES),
        listOf("BLINKIT", "GROFERS") to Entry("Blinkit", Category.GROCERIES),
        listOf("ZEPTO", "KIRANAKART") to Entry("Zepto", Category.GROCERIES),
        listOf("DMART", "AVENUE SUPERMARTS") to Entry("DMart", Category.GROCERIES),
        listOf("JIOMART") to Entry("JioMart", Category.GROCERIES),
        // Transport
        listOf("UBER", "UBERIND", "UBER INDIA") to Entry("Uber", Category.TRANSPORT),
        listOf("OLA", "OLACABS", "OLAMONEY", "ANI TECHNOLOGIES") to Entry("Ola", Category.TRANSPORT),
        listOf("RAPIDO", "ROPPEN") to Entry("Rapido", Category.TRANSPORT),
        listOf("BLUSMART", "BLU SMART") to Entry("BluSmart", Category.TRANSPORT),
        listOf("NAMMA YATRI", "NAMMAYATRI") to Entry("Namma Yatri", Category.TRANSPORT),
        listOf("MERU") to Entry("Meru", Category.TRANSPORT),
        listOf("FASTAG") to Entry("FASTag", Category.TRANSPORT),
        listOf("METRO", "DMRC", "BMRCL") to Entry("Metro", Category.TRANSPORT),
        // Fuel
        listOf("HPCL", "HINDUSTAN PETROLEUM") to Entry("HPCL", Category.FUEL),
        listOf("BPCL", "BHARAT PETROLEUM") to Entry("BPCL", Category.FUEL),
        listOf("IOCL", "INDIAN OIL") to Entry("Indian Oil", Category.FUEL),
        // Shopping
        listOf("AMAZON", "AMZN") to Entry("Amazon", Category.SHOPPING),
        listOf("FLIPKART") to Entry("Flipkart", Category.SHOPPING),
        listOf("MYNTRA") to Entry("Myntra", Category.SHOPPING),
        listOf("AJIO") to Entry("AJIO", Category.SHOPPING),
        listOf("MEESHO") to Entry("Meesho", Category.SHOPPING),
        listOf("NYKAA") to Entry("Nykaa", Category.SHOPPING),
        listOf("CROMA") to Entry("Croma", Category.SHOPPING),
        listOf("DECATHLON") to Entry("Decathlon", Category.SHOPPING),
        listOf("IKEA") to Entry("IKEA", Category.SHOPPING),
        // Bills
        listOf("AIRTEL", "BHARTI AIRTEL") to Entry("Airtel", Category.BILLS),
        listOf("JIO", "RELIANCE JIO") to Entry("Jio", Category.BILLS),
        listOf("VODAFONE", "VODAFONE IDEA") to Entry("Vi", Category.BILLS),
        listOf("BSNL") to Entry("BSNL", Category.BILLS),
        listOf("BESCOM", "TATA POWER", "ADANI ELECTRICITY", "MSEDCL", "TNEB") to Entry("Electricity", Category.BILLS),
        listOf("TATA PLAY", "TATASKY") to Entry("Tata Play", Category.BILLS),
        // Entertainment
        listOf("NETFLIX") to Entry("Netflix", Category.ENTERTAINMENT),
        listOf("SPOTIFY") to Entry("Spotify", Category.ENTERTAINMENT),
        listOf("HOTSTAR", "JIOHOTSTAR", "NOVI DIGITAL") to Entry("JioHotstar", Category.ENTERTAINMENT),
        listOf("PRIME VIDEO") to Entry("Prime Video", Category.ENTERTAINMENT),
        listOf("BOOKMYSHOW", "BIGTREE") to Entry("BookMyShow", Category.ENTERTAINMENT),
        listOf("PVR", "INOX") to Entry("PVR INOX", Category.ENTERTAINMENT),
        listOf("YOUTUBE") to Entry("YouTube", Category.ENTERTAINMENT),
        // Travel
        listOf("IRCTC") to Entry("IRCTC", Category.TRAVEL),
        listOf("MAKEMYTRIP", "MMT") to Entry("MakeMyTrip", Category.TRAVEL),
        listOf("GOIBIBO") to Entry("Goibibo", Category.TRAVEL),
        listOf("CLEARTRIP") to Entry("Cleartrip", Category.TRAVEL),
        listOf("INDIGO", "INTERGLOBE") to Entry("IndiGo", Category.TRAVEL),
        listOf("AIR INDIA") to Entry("Air India", Category.TRAVEL),
        listOf("REDBUS") to Entry("redBus", Category.TRAVEL),
        listOf("OYO") to Entry("OYO", Category.TRAVEL),
        listOf("IXIGO") to Entry("ixigo", Category.TRAVEL),
        // Health
        listOf("APOLLO") to Entry("Apollo", Category.HEALTH),
        listOf("PHARMEASY") to Entry("PharmEasy", Category.HEALTH),
        listOf("1MG", "TATA 1MG") to Entry("Tata 1mg", Category.HEALTH),
        listOf("NETMEDS") to Entry("Netmeds", Category.HEALTH),
        listOf("MEDPLUS") to Entry("MedPlus", Category.HEALTH),
        listOf("CULTFIT", "CULT FIT", "CUREFIT") to Entry("cult.fit", Category.HEALTH),
        // Education
        listOf("UDEMY") to Entry("Udemy", Category.EDUCATION),
        listOf("COURSERA") to Entry("Coursera", Category.EDUCATION),
        listOf("UNACADEMY") to Entry("Unacademy", Category.EDUCATION),
        // Investment
        listOf("ZERODHA") to Entry("Zerodha", Category.INVESTMENT),
        listOf("GROWW") to Entry("Groww", Category.INVESTMENT),
        listOf("UPSTOX") to Entry("Upstox", Category.INVESTMENT),
        listOf("KUVERA") to Entry("Kuvera", Category.INVESTMENT),
        listOf("ICCL", "INDIAN CLEARING") to Entry("ICCL", Category.INVESTMENT),
        listOf("SMALLCASE") to Entry("smallcase", Category.INVESTMENT),
        // Insurance and loans
        listOf("LIC", "LIFE INSURANCE CORPORATION") to Entry("LIC", Category.INSURANCE),
        listOf("POLICYBAZAAR") to Entry("Policybazaar", Category.INSURANCE),
        listOf("ACKO") to Entry("Acko", Category.INSURANCE),
        listOf("HDFC ERGO", "HDFCERGO") to Entry("HDFC ERGO", Category.INSURANCE),
        listOf("ICICI LOMBARD", "ICICILOMBARD") to Entry("ICICI Lombard", Category.INSURANCE),
        listOf("STAR HEALTH", "STARHEALTH") to Entry("Star Health", Category.INSURANCE),
        listOf("NIVA BUPA", "NIVABUPA", "MAX BUPA") to Entry("Niva Bupa", Category.INSURANCE),
        listOf("CARE HEALTH", "CAREHEALTH", "RELIGARE HEALTH") to Entry("Care Health", Category.INSURANCE),
        listOf("ADITYA BIRLA HEALTH", "ABHICL") to Entry("Aditya Birla Health", Category.INSURANCE),
        listOf("MANIPAL CIGNA", "MANIPALCIGNA") to Entry("ManipalCigna", Category.INSURANCE),
        listOf("TATA AIG", "TATAAIG") to Entry("Tata AIG", Category.INSURANCE),
        listOf("BAJAJ ALLIANZ", "BAJAJALLIANZ") to Entry("Bajaj Allianz", Category.INSURANCE),
        listOf("GO DIGIT", "GODIGIT", "DIGIT INSURANCE") to Entry("Digit Insurance", Category.INSURANCE),
        listOf("RELIANCE GENERAL") to Entry("Reliance General", Category.INSURANCE),
        listOf("NEW INDIA ASSURANCE") to Entry("New India Assurance", Category.INSURANCE),
        listOf("MAX LIFE", "MAXLIFE", "AXIS MAX LIFE") to Entry("Axis Max Life", Category.INSURANCE),
        listOf("HDFC LIFE", "HDFCLIFE") to Entry("HDFC Life", Category.INSURANCE),
        listOf("SBI LIFE", "SBILIFE") to Entry("SBI Life", Category.INSURANCE),
        listOf("ICICI PRU", "ICICIPRU", "ICICI PRUDENTIAL LIFE") to Entry("ICICI Prudential Life", Category.INSURANCE),
        listOf("TATA AIA", "TATAAIA") to Entry("Tata AIA", Category.INSURANCE),
        listOf("KOTAK LIFE") to Entry("Kotak Life", Category.INSURANCE),
        listOf("BAJAJ FINANCE", "BAJAJ FINSERV") to Entry("Bajaj Finance", Category.EMI_LOAN),
        // Rent
        listOf("NOBROKER") to Entry("NoBroker", Category.RENT),
    )

    private val byToken = HashMap<String, Entry>()
    private val byPhrase = ArrayList<Pair<String, Entry>>()

    init {
        for ((keys, entry) in entries) for (k in keys) {
            if (' ' in k) byPhrase += k to entry else byToken.putIfAbsent(k, entry)
        }
        // Longest phrase first so "SWIGGY INSTAMART" wins over "SWIGGY".
        byPhrase.sortByDescending { it.first.length }
    }

    fun lookup(upper: String, tokens: List<String>): Entry? {
        for ((phrase, entry) in byPhrase) if (upper.contains(phrase)) return entry
        for (t in tokens) byToken[t]?.let { return it }
        return null
    }

    // Generic words that give away the category of an unknown merchant.
    private val keywordCategory: Map<String, Category> = buildMap {
        listOf("RESTAURANT", "CAFE", "PIZZA", "BURGER", "BIRYANI", "BAKERY", "FOODS", "KITCHEN", "DHABA", "SWEETS").forEach { put(it, Category.FOOD) }
        listOf("SUPERMARKET", "GROCERY", "GROCERIES", "MART", "KIRANA", "FRESH").forEach { put(it, Category.GROCERIES) }
        listOf("PETROL", "FUEL", "FUELS", "PETROLEUM").forEach { put(it, Category.FUEL) }
        listOf("CAB", "CABS", "TAXI", "PARKING", "TOLL").forEach { put(it, Category.TRANSPORT) }
        listOf("HOSPITAL", "CLINIC", "PHARMACY", "MEDICAL", "MEDICALS", "CHEMIST", "DIAGNOSTICS", "LAB", "LABS").forEach { put(it, Category.HEALTH) }
        listOf("SCHOOL", "COLLEGE", "UNIVERSITY", "ACADEMY", "TUITION", "FEES").forEach { put(it, Category.EDUCATION) }
        listOf("ELECTRICITY", "RECHARGE", "BROADBAND", "BILL", "BILLDESK", "GAS", "WATER", "DTH").forEach { put(it, Category.BILLS) }
        listOf("HOTEL", "HOTELS", "AIRLINES", "AIRWAYS", "TRAVELS", "TOURS", "RAILWAY").forEach { put(it, Category.TRAVEL) }
        listOf("INSURANCE", "PREMIUM").forEach { put(it, Category.INSURANCE) }
        listOf("EMI", "LOAN", "FINANCE").forEach { put(it, Category.EMI_LOAN) }
        listOf("RENT").forEach { put(it, Category.RENT) }
        listOf("MUTUAL", "SIP", "MF", "SECURITIES", "BROKING", "CAPITAL").forEach { put(it, Category.INVESTMENT) }
        listOf("MOVIES", "CINEMA", "CINEMAS", "GAMING").forEach { put(it, Category.ENTERTAINMENT) }
    }

    fun categoryByKeyword(tokens: List<String>): Category? {
        for (t in tokens) keywordCategory[t]?.let { return it }
        return null
    }
}
