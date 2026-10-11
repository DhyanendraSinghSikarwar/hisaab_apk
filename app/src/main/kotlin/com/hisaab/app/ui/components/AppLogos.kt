package com.hisaab.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisaab.shared.db.TransactionEntity

/**
 * Real logos of banks, payment apps and common merchants, bundled in the APK under assets/logos. Nothing is
 * fetched at run time, so showing a logo never tells anyone what you bought.
 */
object AppLogos {
    private class Rule(val match: Regex, val key: String)

    private fun r(pattern: String, key: String) = Rule(Regex(pattern, RegexOption.IGNORE_CASE), key)

    private val BANKS = listOf(
        r("""sbi\s*card|sbicard""", "sbicard"),
        r("""\bhdfc""", "hdfc"), r("""\bicici""", "icici"), r("""\baxis""", "axis"), r("""\bhsbc""", "hsbc"),
        r("""\bsbi\b|state bank|\byono""", "sbi"), r("""\bkotak""", "kotak"), r("""\byes\s*bank|\byes\b""", "yes"),
        r("""\bidfc""", "idfc"), r("""\bindusind""", "indusind"), r("""baroda|\bbob\b""", "bob"),
        r("""\bpnb\b|punjab national""", "pnb"), r("""\bau\b|\bau small|\baubank""", "au"),
        r("""\bfederal|\bfedbnk""", "federal"), r("""\bcanara""", "canara"), r("""union bank|\bubi\b|\bunion\b""", "union"),
        r("""\brbl""", "rbl"), r("""chartered|\bscb\b""", "scb"), r("""\bamex|american express""", "amex"),
        r("""onecard|\bonecrd""", "onecard"), r("""bank of india|\bboi\b""", "boi"), r("""overseas|\biob\b""", "iob"),
        r("""indian bank|\bindbnk""", "indianbank"), r("""\bidbi""", "idbi"), r("""\bciti""", "citi"),
        r("""\bbandhan""", "bandhan"), r("""\bequitas""", "equitas"), r("""\bjupiter""", "jupiter"), r("""\bslice""", "slice"),
    )

    // Order matters: the more specific names come first (Amazon Pay before Amazon, Instamart before Swiggy).
    private val MERCHANTS = listOf(
        r("""instamart""", "instamart"), r("""swiggy|bundl tech""", "swiggy"), r("""zomato|zomato""", "zomato"),
        r("""blinkit|grofers""", "blinkit"), r("""zepto|kiranakart""", "zepto"), r("""bigbasket|big basket|supermarket grocery""", "bigbasket"),
        r("""amazon\s*pay|amazonpay""", "amazonpay"), r("""prime\s*video""", "primevideo"), r("""amazon|amzn""", "amazon"),
        r("""flipkart""", "flipkart"), r("""myntra""", "myntra"), r("""\bajio""", "ajio"), r("""nykaa""", "nykaa"),
        r("""meesho""", "meesho"), r("""tata\s*cliq""", "tatacliq"), r("""d\s*-?mart|avenue supermart""", "dmart"),
        r("""jiomart""", "jiomart"), r("""\buber\b""", "uber"), r("""\bola\b|olacabs|ani technologies""", "ola"),
        r("""rapido|roppen""", "rapido"), r("""irctc""", "irctc"), r("""makemytrip|make my trip""", "makemytrip"),
        r("""goibibo""", "goibibo"), r("""cleartrip""", "cleartrip"), r("""indigo|interglobe""", "indigo"),
        r("""air\s*india""", "airindia"), r("""redbus""", "redbus"), r("""netflix""", "netflix"), r("""spotify""", "spotify"),
        r("""hotstar|jiocinema|jiohotstar""", "hotstar"), r("""youtube""", "youtube"), r("""\bjio\b|reliance jio""", "jio"),
        r("""airtel""", "airtel"), r("""\bvi\b|vodafone|idea cellular""", "vi"), r("""starbucks""", "starbucks"),
        r("""mcdonald""", "mcdonalds"), r("""domino""", "dominos"), r("""\bkfc\b""", "kfc"), r("""pizza\s*hut""", "pizzahut"),
        r("""bookmyshow|bigtree""", "bookmyshow"), r("""\bpvr\b|\binox\b""", "pvr"), r("""zerodha""", "zerodha"),
        r("""groww""", "groww"), r("""upstox""", "upstox"), r("""indmoney""", "indmoney"), r("""\blic\b|life insurance corp""", "lic"),
        r("""policybazaar""", "policybazaar"), r("""\bhpcl\b|hindustan petroleum|\bhp pay""", "hpcl"),
        r("""\biocl?\b|indian oil|indianoil""", "iocl"), r("""\bbpcl\b|bharat petroleum""", "bpcl"),
        r("""apollo""", "apollo"), r("""netmeds""", "netmeds"), r("""\b1mg\b|tata 1mg""", "1mg"),
        r("""urban\s*company|urbanclap""", "urbancompany"), r("""decathlon""", "decathlon"), r("""\bikea\b""", "ikea"),
        r("""lenskart""", "lenskart"), r("""\bcred\b|dreamplug""", "cred"), r("""paytm""", "paytm"), r("""phonepe""", "phonepe"),
        r("""google\s*pay|\bgpay\b|okaxis|okhdfcbank|okicici|oksbi""", "gpay"), r("""\bbhim\b""", "bhim"),
    )

    // Checked before MERCHANTS: brands whose names would otherwise be swallowed by a broader rule (Audible by Amazon).
    private val SPECIFIC = listOf(
        r("""audible""", "audible"), r("""zoom\s*car|zoomcar""", "zoomcar"),
    )

    // Broad names (Google, Apple, Microsoft) come last so Google Pay, Apple-branded cards etc. match their own rules first.
    private val GENERIC = listOf(
        r("""croma|infiniti retail""", "croma"), r("""pepperfry""", "pepperfry"),
        r("""reliance\s*(retail|fresh|smart|trends)""", "relianceretail"), r("""freshtohome|fresh to home""", "freshtohome"),
        r("""eatsure|rebel foods""", "eatsure"), r("""\bbox8\b""", "box8"), r("""burger\s*king""", "burgerking"),
        r("""haldiram""", "haldirams"), r("""cafe coffee day|\bccd\b|coffee day""", "ccd"), r("""\bshell\b""", "shell"),
        r("""\bixigo|le travenues""", "ixigo"), r("""\byatra""", "yatra"), r("""easemytrip|easy trip planners""", "easemytrip"),
        r("""airbnb""", "airbnb"), r("""\boyo\b|oyo rooms""", "oyo"), r("""vistara|tata sia""", "vistara"), r("""spicejet""", "spicejet"),
        r("""booking\.com""", "booking"), r("""sony\s*liv|sonyliv""", "sonyliv"), r("""zee\s*5|zee5""", "zee5"),
        r("""\bsteam(powered|games)?\b|valve corp""", "steam"), r("""gaana""", "gaana"), r("""razorpay""", "razorpay"),
        r("""pharmeasy""", "pharmeasy"), r("""practo""", "practo"), r("""healthifyme""", "healthifyme"),
        r("""coursera""", "coursera"), r("""udemy""", "udemy"), r("""unacademy""", "unacademy"), r("""openai|chatgpt""", "openai"),
        r("""anthropic|claude\.ai""", "anthropic"), r("""github""", "github"), r("""notion""", "notion"), r("""canva""", "canva"),
        r("""\bzoom\b""", "zoom"), r("""linkedin""", "linkedin"), r("""hdfc\s*life""", "hdfclife"), r("""\bdigit\b|godigit""", "digit"),
        r("""\backo\b""", "acko"), r("""kuvera""", "kuvera"), r("""shoppers\s*stop""", "shoppersstop"), r("""\bporter\b""", "porter"),
        r("""delhivery""", "delhivery"), r("""adobe""", "adobe"), r("""microsoft|msft|office\s*365""", "microsoft"),
        r("""\bapple\b|itunes|icloud""", "apple"), r("""\bgoogle\b|google (one|workspace|cloud|storage)""", "google"),
    )

    fun bank(name: String?): String? = name?.let { n -> BANKS.firstOrNull { it.match.containsMatchIn(n) }?.key }

    fun merchant(name: String?): String? = name?.let { n ->
        SPECIFIC.firstOrNull { it.match.containsMatchIn(n) }?.key
            ?: MERCHANTS.firstOrNull { it.match.containsMatchIn(n) }?.key
            ?: GENERIC.firstOrNull { it.match.containsMatchIn(n) }?.key
    }

    /** Every logo key the rules can return; each must have a file under assets/logos. */
    internal fun allKeys(): Set<String> = (BANKS + SPECIFIC + MERCHANTS + GENERIC).map { it.key }.toSet()

    private val cache by lazy { LruCache<String, ImageBitmap>(64) }

    fun load(context: android.content.Context, key: String): ImageBitmap? = cache.get(key) ?: runCatching {
        context.assets.open("logos/$key.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()?.also { cache.put(key, it) }
}

/** A bundled logo in a rounded tile, or nothing when the key has no file. */
@Composable
fun LogoImage(key: String, size: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(size * 0.28f)): Boolean {
    val context = LocalContext.current
    val bitmap = remember(key) { AppLogos.load(context, key) } ?: return false
    Image(bitmap, key, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    return true
}

/**
 * What a transaction row leads with: the merchant's logo when it is a known brand, otherwise the category icon.
 * A small bank logo sits on the corner either way, so where the money came from is visible at a glance.
 */
@Composable
fun TransactionAvatar(tx: TransactionEntity, size: Dp = 40.dp) {
    val context = LocalContext.current
    val merchantKey = remember(tx.merchant) { AppLogos.merchant(tx.merchant) }
    val bankKey = remember(tx.bankName) { AppLogos.bank(tx.bankName) }
    val hasMerchant = merchantKey != null && remember(merchantKey) { AppLogos.load(context, merchantKey) } != null
    Box(Modifier.size(size + 4.dp)) {
        // Every icon shares one shape: a rounded square with a hairline edge.
        val tile = RoundedCornerShape(size * 0.28f)
        if (hasMerchant) LogoImage(merchantKey!!, size, Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, tile), tile)
        else CategoryBadge(tx.category, size = size.value.toInt())
        if (bankKey != null && bankKey != merchantKey) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(size * 0.45f)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LogoImage(bankKey, size * 0.42f, shape = CircleShape) }
        }
    }
}
