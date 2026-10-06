package com.hisaab.app.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.theme.clearTopBar

/** The frame every More detail screen shares: a clear top bar with a back arrow, on the app backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBar(),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
    ) { inner -> Box(Modifier.fillMaxSize()) { content(inner) } }
}

/** Content padding for a detail list: 16dp sides, below the top bar, and some room at the bottom. */
internal fun listPadding(inner: PaddingValues, bottomExtra: Dp = 24.dp) = PaddingValues(
    start = 16.dp, end = 16.dp,
    top = inner.calculateTopPadding() + 4.dp,
    bottom = inner.calculateBottomPadding() + bottomExtra,
)

/** A setting row inside an [com.hisaab.app.ui.components.HCard]: title, one line of help, and a switch. */
@Composable
internal fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    com.hisaab.app.ui.components.HRow(
        title, subtitle,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
    ) {
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A short line of explanatory text under a card's controls. */
@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.sp, color = com.hisaab.app.ui.theme.Hx.text2)
}
