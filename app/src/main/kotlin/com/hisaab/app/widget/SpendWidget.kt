package com.hisaab.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hisaab.app.MainActivity
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Home-screen widget: today's spend and this month's spend. */
class SpendWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun transactions(): TransactionDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).transactions()
        val now = System.currentTimeMillis()
        val spend = listOf("DEBIT", "INVESTMENT")
        val today = dao.total(spend, Periods.startOfDay(now), now)
        val month = dao.total(spend, Periods.startOfMonth(now), now)
        provideContent { GlanceTheme { Content(today, month) } }
    }

    @Composable
    private fun Content(today: Long, month: Long) {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(GlanceTheme.colors.widgetBackground)
                .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(t("Spent today"), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Text(Money.format(today), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
            Text(t("This month"), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Text(Money.format(month), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}

class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) :
    com.hisaab.shared.repo.TransactionsChangedNotifier {
    override suspend fun onTransactionsChanged() {
        runCatching { SpendWidget().updateAll(context) }
    }
}
