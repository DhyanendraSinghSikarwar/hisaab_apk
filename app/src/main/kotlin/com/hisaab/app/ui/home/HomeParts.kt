package com.hisaab.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.AmountPrivacy
import com.hisaab.app.ui.theme.Hx
import com.hisaab.shared.insight.Insight
import com.hisaab.shared.insight.Tone
import java.time.LocalTime

fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> t("Good morning")
    in 12..16 -> t("Good afternoon")
    in 17..21 -> t("Good evening")
    else -> t("Good night")
}

/** Initials in a green disc with a gold ring. */
@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "H" }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF00897B))))
            .border(2.dp, Color(0xFFFFB300).copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Avatar, greeting and name (tap for the profile), then the amount eye, Accounts and the notification bell. */
@Composable
fun HomeHeader(
    name: String, onOpenAccounts: () -> Unit, onOpenProfile: () -> Unit,
    notifications: Int, onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false, photoPath: String? = null, hasName: Boolean = true,
    onToggleHide: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenProfile).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.hisaab.app.ui.profile.ProfileAvatar(name, photoPath, if (compact) 36.dp else 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (!compact) Text(greeting(), style = MaterialTheme.typography.bodySmall, color = Hx.text2)
                Text(name, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact && !hasName) Text(t("Tap to set up your profile"), style = MaterialTheme.typography.labelMedium, color = Hx.accent)
            }
        }
        if (onToggleHide != null) {
            IconButton(onClick = onToggleHide) {
                Icon(if (AmountPrivacy.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (AmountPrivacy.hidden) t("Show amounts") else t("Hide amounts"), tint = Hx.text2)
            }
        }
        IconButton(onClick = onOpenAccounts) { Icon(Icons.Filled.AccountBalance, t("Accounts")) }
        IconButton(onClick = onOpenNotifications) {
            androidx.compose.material3.BadgedBox(badge = { if (notifications > 0) androidx.compose.material3.Badge { Text("$notifications") } }) {
                Icon(Icons.Filled.Notifications, t("Notifications"))
            }
        }
    }
}

/** One thing that needs the user: a review, a locked statement, an update. */
data class HomeNotice(val title: String, val detail: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onOpen: () -> Unit)

/** The bell's sheet: each notice opens what it is about. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(notices: List<HomeNotice>, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            Text(t("Notifications"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            if (notices.isEmpty()) {
                Text(t("You're all caught up."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp))
            }
            notices.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); n.onOpen() }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(n.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(n.title, style = MaterialTheme.typography.bodyLarge)
                        Text(n.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** An insight as a list row: an icon by tone, the title and the detail. Used by Analytics. */
@Composable
fun InsightRow(i: Insight, modifier: Modifier = Modifier) {
    val tint = when (i.tone) {
        Tone.GOOD -> Hx.pos
        Tone.WARN -> Hx.warn
        Tone.INFO -> Hx.accent
    }
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                when (i.tone) {
                    Tone.GOOD -> Icons.AutoMirrored.Filled.TrendingDown
                    Tone.WARN -> Icons.AutoMirrored.Filled.TrendingUp
                    Tone.INFO -> Icons.Filled.Lightbulb
                },
                null, tint = tint,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(i.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(i.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The header that floats over the content once Home is scrolled: a rounded bar. */
@Composable
fun CollapsedHeader(content: @Composable () -> Unit) {
    Box(
        Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}
