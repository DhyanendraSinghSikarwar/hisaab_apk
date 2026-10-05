package com.hisaab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.theme.LocalDarkTheme
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.CardNetwork

/** A bank, card network or payment app: its colour, its logo when a free one exists, and a monogram otherwise. */
data class Brand(val name: String, val color: Color, val path: String? = null, val monogram: String)

object Brands {
    private data class Rule(val match: Regex, val brand: Brand)

    private fun rule(pattern: String, brand: Brand) = Rule(Regex(pattern, RegexOption.IGNORE_CASE), brand)

    private val BANKS = listOf(
        rule("""\bhdfc""", Brand("HDFC Bank", Color(0xFF004B8D), LogoPaths.HDFC, "HDFC")),
        rule("""\bicici""", Brand("ICICI Bank", Color(0xFFAE282E), LogoPaths.ICICI, "ICICI")),
        rule("""\baxis""", Brand("Axis Bank", Color(0xFF971A4D), LogoPaths.AXIS, "AXIS")),
        rule("""\bhsbc""", Brand("HSBC", Color(0xFFDB0011), LogoPaths.HSBC, "HSBC")),
        rule("""\bsbi\b|state bank""", Brand("SBI", Color(0xFF22409A), monogram = "SBI")),
        rule("""\bkotak""", Brand("Kotak", Color(0xFFED1C24), monogram = "K")),
        rule("""\byes\b""", Brand("Yes Bank", Color(0xFF0067B1), monogram = "YES")),
        rule("""\bidfc""", Brand("IDFC FIRST", Color(0xFF9C1D26), monogram = "IDFC")),
        rule("""\bindusind""", Brand("IndusInd", Color(0xFF98272A), monogram = "IIB")),
        rule("""baroda|\bbob""", Brand("Bank of Baroda", Color(0xFFF15A29), monogram = "BOB")),
        rule("""\bpnb\b|punjab national""", Brand("PNB", Color(0xFFA20A3A), monogram = "PNB")),
        rule("""\bau\b|\bau small""", Brand("AU Bank", Color(0xFF6D2077), monogram = "AU")),
        rule("""\bfederal|\bfedbnk""", Brand("Federal Bank", Color(0xFF1A4C9A), monogram = "FB")),
        rule("""\bcanara""", Brand("Canara Bank", Color(0xFF0091D5), monogram = "CB")),
        rule("""\bunion""", Brand("Union Bank", Color(0xFFE31E24), monogram = "UBI")),
        rule("""\brbl""", Brand("RBL Bank", Color(0xFF21409A), monogram = "RBL")),
        rule("""chartered|\bscb""", Brand("Standard Chartered", Color(0xFF0473EA), monogram = "SC")),
        rule("""\bamex|american express""", Brand("American Express", Color(0xFF2E77BC), LogoPaths.AMEX, "AMEX")),
        rule("""onecard|\bonecrd""", Brand("OneCard", Color(0xFF111111), monogram = "1")),
    )

    fun forBank(bankName: String): Brand =
        BANKS.firstOrNull { it.match.containsMatchIn(bankName) }?.brand ?: generic(bankName)

    fun forNetwork(network: CardNetwork): Brand = when (network) {
        CardNetwork.VISA -> Brand("Visa", Color(0xFF1A1F71), LogoPaths.VISA, "VISA")
        CardNetwork.MASTERCARD -> Brand("Mastercard", Color(0xFFEB001B), LogoPaths.MASTERCARD, "MC")
        CardNetwork.AMEX -> Brand("American Express", Color(0xFF2E77BC), LogoPaths.AMEX, "AMEX")
        CardNetwork.DINERS -> Brand("Diners Club", Color(0xFF004C97), LogoPaths.DINERS, "DC")
        CardNetwork.DISCOVER -> Brand("Discover", Color(0xFFFF6000), LogoPaths.DISCOVER, "D")
        CardNetwork.JCB -> Brand("JCB", Color(0xFF0B4EA2), LogoPaths.JCB, "JCB")
        CardNetwork.RUPAY -> Brand("RuPay", Color(0xFF097A44), monogram = "RuPay")
        CardNetwork.MAESTRO -> Brand("Maestro", Color(0xFF0099DF), monogram = "M")
        CardNetwork.UNIONPAY -> Brand("UnionPay", Color(0xFFE21836), monogram = "UP")
    }

    /** Monogram from the name's initials, in a colour picked from the name so it stays the same every time. */
    private fun generic(name: String): Brand {
        val words = name.split(' ', '-', '_').filter { it.isNotBlank() && !it.equals("bank", ignoreCase = true) }
        val mono = (words.take(2).map { it.first() }.joinToString("").ifEmpty { name.take(2) }).uppercase()
        return Brand(name, PALETTE[Math.floorMod(name.lowercase().hashCode(), PALETTE.size)], monogram = mono)
    }

    private val PALETTE = listOf(Color(0xFF00796B), Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF6D4C41), Color(0xFF00838F), Color(0xFFAD1457))
}

/** One colour per kind, so a card is never mistaken for an account at a glance. */
object KindColors {
    val account: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF8AB4F8) else Color(0xFF1A63C6)
    val creditCard: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFFFB27A) else Color(0xFFC8570F)
    val debitCard: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFC9A7FF) else Color(0xFF6A3FC2)

    @Composable
    @ReadOnlyComposable
    fun of(kind: AccountKind, type: AccountType?): Color = when {
        kind == AccountKind.ACCOUNT -> account
        type == AccountType.DEBIT_CARD || type == AccountType.PREPAID_CARD -> debitCard
        else -> creditCard
    }
}

/** A brand's logo on a tinted tile, or its monogram on a solid one. Dark brand colours are lifted in the dark theme. */
@Composable
fun BrandMark(brand: Brand, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(size * 0.28f)
    val logo = remember(brand.name) { AppLogos.bank(brand.name) ?: AppLogos.merchant(brand.name) }
    if (logo != null && LogoImage(logo, size, modifier, shape)) return
    if (brand.path != null) {
        val vector = remember(brand.path) {
            ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .addPath(PathParser().parsePathString(brand.path).toNodes(), fill = SolidColor(Color.Black))
                .build()
        }
        val tint = if (dark) lerp(brand.color, Color.White, 0.45f) else brand.color
        Box(modifier.size(size).background(tint.copy(alpha = if (dark) 0.16f else 0.10f), shape), contentAlignment = Alignment.Center) {
            Icon(vector, brand.name, tint = tint, modifier = Modifier.size(size * 0.58f))
        }
    } else {
        Box(modifier.size(size).background(brand.color, shape), contentAlignment = Alignment.Center) {
            val chars = brand.monogram.length
            Text(
                brand.monogram, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                fontSize = (size.value * when { chars <= 1 -> 0.46f; chars <= 2 -> 0.38f; chars <= 3 -> 0.3f; chars <= 4 -> 0.24f; else -> 0.2f }).sp,
            )
        }
    }
}

/** An account's bank logo with a corner badge saying whether it is an account, a credit card, or a debit card. */
@Composable
fun AccountAvatar(bankName: String, kind: AccountKind, type: AccountType?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val kindColor = KindColors.of(kind, type)
    Box(modifier.size(size + 4.dp)) {
        BrandMark(Brands.forBank(bankName), size = size)
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(size * 0.42f)
                .background(kindColor, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (kind == AccountKind.CARD) Icons.Filled.CreditCard else Icons.Filled.AccountBalance, null,
                tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(3.dp),
            )
        }
    }
}
