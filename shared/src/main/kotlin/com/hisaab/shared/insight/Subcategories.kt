package com.hisaab.shared.insight

import com.hisaab.parser.model.Category

/**
 * The popular sub-categories of each category, with the merchants that usually belong in them. A transaction
 * the user hasn't placed is put in the first sub-category whose pattern matches its merchant; one that matches
 * none is "Other". [icon] is a key in the app's icon library.
 */
object Subcategories {
    data class Sub(val name: String, val icon: String, val match: Regex?)

    const val OTHER = "Other"

    private fun s(name: String, icon: String, pattern: String? = null) =
        Sub(name, icon, pattern?.let { Regex(it, RegexOption.IGNORE_CASE) })

    private val CATALOG: Map<Category, List<Sub>> = mapOf(
        Category.FOOD to listOf(
            s("Food delivery", "delivery_dining", """swiggy|zomato|uber\s*eats|eatsure|box8|faasos|rebel\s*foods|magicpin"""),
            s("Cafés & coffee", "local_cafe", """starbucks|\bcafe|caf[eé]\b|coffee|\bccd\b|chaayos|chai|third\s*wave|blue\s*tokai"""),
            s("Fast food", "fastfood", """mcdonald|domino|\bkfc\b|pizza|burger|subway|wow\s*momo"""),
            s("Bakery & desserts", "bakery_dining", """bakery|bakers|cake|ice\s*cream|baskin|theobroma|naturals\s*ice"""),
            s("Bars & nightlife", "local_bar", """\bbar\b|\bpub\b|brew|liquor|wines?\b|beer"""),
            s("Restaurants", "restaurant", """restaurant|dhaba|bistro|kitchen|biryani|dining|eatery|hotel"""),
        ),
        Category.GROCERIES to listOf(
            s("Quick commerce", "bolt", """blinkit|grofers|zepto|kiranakart|instamart|bb\s*now|dunzo"""),
            s("Supermarket", "local_grocery_store", """d\s*-?mart|avenue\s*supermart|reliance\s*(fresh|smart|retail)|spencer|star\s*bazaar|more\s*retail|bigbasket|jiomart|nature'?s\s*basket"""),
            s("Dairy", "egg", """milk|dairy|amul|mother\s*dairy|country\s*delight|milkbasket|nandini"""),
            s("Fruits & vegetables", "eco", """fruit|vegetable|sabzi|farm"""),
            s("Kirana", "storefront", """kirana|general\s*store|provision|traders"""),
        ),
        Category.TRANSPORT to listOf(
            s("Cabs & rides", "local_taxi", """\buber\b|\bola\b|olacabs|ani\s*technologies|rapido|roppen|blusmart|meru|namma\s*yatri"""),
            s("Metro & bus", "directions_subway", """metro|dmrc|bmrcl|\bbest\b|bmtc|dtc|ksrtc|msrtc"""),
            s("Parking & tolls", "local_parking", """parking|fastag|\btoll|netc"""),
            s("Vehicle service", "car_repair", """service\s*cent|garage|tyre|car\s*wash|motors"""),
        ),
        Category.FUEL to listOf(
            s("Petrol & diesel", "local_gas_station", """hpcl|bpcl|iocl|indian\s*oil|indianoil|bharat\s*petroleum|hindustan\s*petroleum|hp\s*pay|petrol|diesel|shell|nayara|fuel"""),
            s("EV charging", "ev_station", """charging|ez\s*charge|statiq|chargezone|\bev\b"""),
            s("CNG", "propane", """\bcng\b|indraprastha\s*gas|\bigl\b|mahanagar\s*gas"""),
        ),
        Category.SHOPPING to listOf(
            s("Online shopping", "shopping_bag", """amazon|amzn|flipkart|meesho|snapdeal|shopsy"""),
            s("Fashion", "checkroom", """myntra|ajio|zara|h\s*&\s*m|\bmax\b|pantaloons|westside|lifestyle|uniqlo|bata|puma|nike|adidas|tata\s*cliq|trends"""),
            s("Electronics", "devices", """croma|reliance\s*digital|vijay\s*sales|apple|samsung|\bmi\b|xiaomi|oneplus|boat"""),
            s("Home & furniture", "chair", """ikea|pepperfry|urban\s*ladder|home\s*centre|wakefit"""),
            s("Sports & fitness gear", "sports_cricket", """decathlon|sports"""),
            s("Books & stationery", "menu_book", """book|stationery|crossword|sapna"""),
        ),
        Category.BILLS to listOf(
            s("Mobile recharge", "smartphone", """\bjio\b|airtel|\bvi\b|vodafone|idea|bsnl|recharge|prepaid|postpaid"""),
            s("Electricity", "electric_bolt", """electric|bescom|tata\s*power|adani\s*electricity|msedcl|mahadiscom|bses|torrent\s*power|tneb|tangedco|cesc|power"""),
            s("Broadband & DTH", "router", """broadband|fib(er|re)|act\s*fibernet|hathway|excitel|tata\s*play|dish\s*tv|d2h|xstream|sun\s*direct"""),
            s("Gas cylinder", "propane_tank", """indane|hp\s*gas|bharat\s*gas|\blpg\b|gas\s*booking"""),
            s("Water", "water_drop", """water|jal\s*board"""),
            s("Society maintenance", "apartment", """maintenance|society|mygate|nobrokerhood|apartment"""),
        ),
        Category.ENTERTAINMENT to listOf(
            s("Movies", "movie", """\bpvr\b|\binox\b|bookmyshow|bigtree|cinepolis|cinema|movie"""),
            s("Events & shows", "confirmation_number", """event|concert|insider|district|ticket"""),
            s("Gaming", "sports_esports", """steam|playstation|xbox|game|dream11|\bmpl\b|winzo"""),
            s("Outings", "attractions", """wonderla|imagica|park|museum|zoo|bowling|smaaash"""),
        ),
        Category.TRAVEL to listOf(
            s("Cabs", "local_taxi", """\buber\b|\bola\b|olacabs|ani\s*technologies|rapido|roppen|blusmart|meru|namma\s*yatri|savaari|outstation"""),
            s("Flights", "flight", """indigo|interglobe|air\s*india|vistara|spicejet|akasa|airline|airways"""),
            s("Trains", "train", """irctc|railway|\brail\b"""),
            s("Hotels & stays", "hotel", """\boyo\b|hotel|airbnb|treebo|fabhotel|\btaj\b|marriott|booking\.com|agoda|resort|zostel"""),
            s("Bus", "directions_bus", """redbus|abhibus|zingbus|volvo"""),
            s("Travel booking", "luggage", """makemytrip|make\s*my\s*trip|goibibo|cleartrip|yatra|ixigo|easemytrip"""),
        ),
        Category.HEALTH to listOf(
            s("Medicines", "medication", """pharmeasy|netmeds|\b1mg\b|apollo\s*pharm|medplus|pharma|chemist|medical|druggist|wellness\s*forever"""),
            s("Doctor & hospital", "local_hospital", """hospital|clinic|doctor|\bdr\.?\s|practo|fortis|manipal|max\s*health|narayana|aster|medanta|apollo"""),
            s("Lab tests", "biotech", """\blab|diagnostic|thyrocare|lal\s*path|metropolis|redcliffe|healthians|orange\s*health"""),
            s("Fitness", "fitness_center", """cult|gym|fitness|yoga|healthify"""),
            s("Personal care", "spa", """salon|spa\b|parlour|grooming"""),
            s("Eye care", "visibility", """lenskart|optical|optician|eye"""),
        ),
        Category.EDUCATION to listOf(
            s("Courses & coaching", "school", """udemy|coursera|byju|unacademy|upgrad|vedantu|physics\s*wallah|course|coaching|classes|academy"""),
            s("School & college fees", "account_balance", """school|college|university|institute|fees|tuition"""),
            s("Books", "menu_book", """book|kindle"""),
        ),
        Category.RENT to listOf(
            s("House rent", "home", """rent|nobroker|housing|landlord"""),
            s("PG & hostel", "bed", """\bpg\b|hostel|co-?living|stanza|zolo|colive"""),
        ),
        Category.INSURANCE to listOf(
            s("Life insurance", "favorite", """\blic\b|life|term|max\s*life|hdfc\s*life|icici\s*pru|sbi\s*life|tata\s*aia"""),
            s("Health insurance", "health_and_safety", """health|star\s*health|care\s*health|niva\s*bupa|hdfc\s*ergo|aditya\s*birla\s*health"""),
            s("Vehicle insurance", "directions_car", """motor|car\s*insurance|bike\s*insurance|two\s*wheeler|acko|go\s*digit|\bdigit\b"""),
        ),
        Category.EMI_LOAN to listOf(
            s("Home loan", "home", """home\s*loan|housing\s*loan|hfc"""),
            s("Car loan", "directions_car", """car\s*loan|auto\s*loan|vehicle\s*loan"""),
            s("Education loan", "school", """education\s*loan"""),
            s("Card EMI", "credit_card", """\bemi\b|card"""),
            s("Personal loan", "payments", """personal\s*loan|loan|bajaj\s*fin|navi|kreditbee|moneyview|slice"""),
        ),
        Category.INVESTMENT to listOf(
            s("Mutual funds", "pie_chart", """mutual|\bsip\b|cams|kfin|kuvera|\bcoin\b|\bmf\b|amc"""),
            s("Stocks", "show_chart", """zerodha|upstox|angel|dhan|groww|stock|\bnse\b|\bbse\b|indmoney|iccl|nsccl"""),
            s("PPF, EPF & NPS", "savings", """\bppf\b|\bepf\b|\bnps\b|provident|pension"""),
            s("FD & RD", "account_balance", """fixed\s*deposit|recurring\s*deposit|\bfd\b|\brd\b"""),
            s("Gold", "diamond", """gold|\bsgb\b|safegold|augmont"""),
        ),
        Category.SUBSCRIPTIONS to listOf(
            s("Streaming", "live_tv", """netflix|prime\s*video|hotstar|jiocinema|sonyliv|zee5|youtube|aha|voot"""),
            s("Music", "music_note", """spotify|gaana|saavn|apple\s*music|wynk"""),
            s("Cloud & apps", "cloud", """google\s*(one|storage|play)|icloud|dropbox|microsoft|office|chatgpt|openai|adobe|notion|claude|anthropic|canva"""),
            s("News & reading", "newspaper", """times|hindu|express|kindle\s*unlimited|audible|economist|newspaper"""),
            s("Memberships", "card_membership", """prime|flipkart\s*plus|swiggy\s*one|zomato\s*gold|membership"""),
        ),
        Category.PERSONAL_CARE to listOf(
            s("Salon & spa", "content_cut", """salon|spa\b|parlour|urban\s*company|urbanclap|looks|naturals|lakme|jawed"""),
            s("Cosmetics & skincare", "face", """nykaa|purplle|mamaearth|sugar|cosmetic|beauty|minimalist"""),
        ),
        Category.HOUSEHOLD to listOf(
            s("Home services", "handyman", """urban\s*company|urbanclap|plumb|electrician|carpenter|pest|repair"""),
            s("Domestic help", "cleaning_services", """maid|cook|driver|help|cleaning"""),
            s("Laundry", "local_laundry_service", """laundry|dry\s*clean|washing"""),
            s("Home supplies", "kitchen", """utensil|hardware|household"""),
        ),
        Category.GIFTS to listOf(
            s("Flowers & cakes", "local_florist", """fnp|ferns|igp|flower|bloom"""),
            s("Gifts", "redeem", null),
        ),
        Category.DONATIONS to listOf(
            s("Religious", "temple_hindu", """temple|mandir|gurudwara|church|mosque|masjid|devasthan|trust"""),
            s("Charity", "volunteer_activism", null),
        ),
        Category.TAXES to listOf(
            s("Income tax", "receipt_long", """income\s*tax|\bitd\b|\btds\b|advance\s*tax|incometax"""),
            s("GST", "request_quote", """\bgst\b"""),
            s("Property tax", "house", """property\s*tax|municipal|nagar\s*nigam|bbmp|mcgm"""),
        ),
        Category.FEES to listOf(
            s("Interest & late fees", "warning", """late|penalty|interest|finance\s*charge|overdue"""),
            s("Card fees", "credit_card", """annual\s*fee|joining\s*fee|card\s*fee|renewal"""),
            s("Bank charges", "account_balance", null),
        ),
        Category.CASH to listOf(s("ATM withdrawal", "atm", null)),
    )

    fun builtIn(category: Category): List<Sub> = CATALOG[category].orEmpty()

    /** The built-in sub-category a merchant belongs in, or null when none matches. */
    fun guess(category: Category, merchant: String?, upiId: String? = null): String? {
        val text = listOfNotNull(merchant, upiId).joinToString(" ").ifBlank { return null }
        return builtIn(category).firstOrNull { it.match?.containsMatchIn(text) == true }?.name
    }

    /** The sub-category a transaction is counted under: the user's choice, else the guess, else "Other". */
    fun of(category: Category, subcategory: String?, merchant: String?, upiId: String?): String =
        subcategory ?: guess(category, merchant, upiId) ?: builtIn(category).singleOrNull()?.takeIf { it.match == null }?.name ?: OTHER
}
