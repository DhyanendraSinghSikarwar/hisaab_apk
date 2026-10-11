package com.hisaab.app

import com.hisaab.app.ui.components.AppLogos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogosTest {
    private val cases = mapOf(
        "Myntra Designs Pvt Ltd" to "myntra", "AMAZON PAY INDIA" to "amazonpay", "Amazon Seller Services" to "amazon",
        "AMZN Marketplace" to "amazon", "Flipkart Internet" to "flipkart", "Zomato Ltd" to "zomato", "Swiggy Instamart" to "instamart",
        "BUNDL TECHNOLOGIES" to "swiggy", "Blinkit" to "blinkit", "Ola Cabs" to "ola", "Coca Cola Stores" to null,
        "Olam Agro" to null, "Uber India" to "uber", "Audible India" to "audible", "Google Pay" to "gpay",
        "Google One storage" to "google", "GOOGLE*YouTube Premium" to "youtube", "Apple Services" to "apple",
        "Microsoft Corp" to "microsoft", "OpenAI LLC" to "openai", "Anthropic PBC" to "anthropic", "GitHub Inc" to "github",
        "Burger King India" to "burgerking", "Cafe Coffee Day" to "ccd", "Haldiram Snacks" to "haldirams", "Shell Petrol Pump" to "shell",
        "Seashell Resort" to null, "OYO Rooms" to "oyo", "Booking.com Hotel" to "booking", "Airbnb Payments" to "airbnb",
        "ixigo" to "ixigo", "Yatra Online" to "yatra", "EaseMyTrip" to "easemytrip", "SpiceJet Ltd" to "spicejet", "Vistara" to "vistara",
        "Sony LIV" to "sonyliv", "ZEE5 Global" to "zee5", "Steam Games" to "steam", "Razorpay Software" to "razorpay",
        "PharmEasy" to "pharmeasy", "Practo Technologies" to "practo", "Coursera Inc" to "coursera", "Udemy" to "udemy",
        "Notion Labs" to "notion", "Canva Pty" to "canva", "Zoom Video" to "zoom", "Zoomcar India" to "zoomcar", "LinkedIn Premium" to "linkedin",
        "HDFC Life Insurance" to "hdfclife", "Acko General" to "acko", "Digit Insurance" to "digit", "Kuvera" to "kuvera",
        "Shoppers Stop" to "shoppersstop", "Croma Electronics" to "croma", "Pepperfry" to "pepperfry", "Reliance Fresh" to "relianceretail",
        "Delhivery Ltd" to "delhivery", "Porter Logistics" to "porter", "Adobe Systems" to "adobe", "FreshToHome" to "freshtohome",
        "Netflix.com" to "netflix", "Prime Video" to "primevideo", "IRCTC" to "irctc", "Swiggy" to "swiggy",
    )

    @Test fun merchantsMapToExpectedLogo() {
        cases.forEach { (name, key) -> assertEquals(name, key, AppLogos.merchant(name)) }
    }

    @Test fun everyRuleKeyHasAnAsset() {
        val dir = listOf("src/main/assets/logos", "app/src/main/assets/logos").map(::File).first { it.isDirectory }
        val missing = AppLogos.allKeys().filterNot { File(dir, "$it.webp").isFile }
        assertTrue("Missing logo files: $missing", missing.isEmpty())
    }
}
