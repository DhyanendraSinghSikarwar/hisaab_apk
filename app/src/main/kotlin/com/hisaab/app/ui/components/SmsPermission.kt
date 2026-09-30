package com.hisaab.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hisaab.app.security.AppLockGate

/**
 * SMS permission as Compose state. It is read again every time the screen resumes, so a grant made
 * in system Settings (or through Android's "Allow restricted settings" step for sideloaded apps) is
 * seen as soon as the user comes back, and the permission card goes away.
 */
@Stable
class SmsPermissionState internal constructor(private val context: Context) {
    var granted by mutableStateOf(check(context))
        internal set

    /** True after a request that Android answered without showing a dialog: only App settings can grant it now. */
    var blocked by mutableStateOf(false)
        internal set

    internal var launch: () -> Unit = {}

    /** Shows the system dialog, or opens App settings when Android will no longer show it. */
    fun request() {
        if (blocked) openAppSettings() else launch()
    }

    fun openAppSettings() {
        AppLockGate.skipNextLock()
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun refresh(): Boolean {
        val now = check(context)
        val newlyGranted = now && !granted
        granted = now
        if (now) blocked = false
        return newlyGranted
    }

    companion object {
        fun check(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }
}

/** [onGranted] runs once each time the permission goes from missing to granted, however it was granted. */
@Composable
fun rememberSmsPermission(onGranted: () -> Unit): SmsPermissionState {
    val context = LocalContext.current
    val state = remember { SmsPermissionState(context.applicationContext) }
    val callback by rememberUpdatedState(onGranted)
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        // Trust the real permission state, not the result map: some phones report the map inaccurately.
        if (state.refresh()) {
            callback()
        } else if (!state.granted) {
            // No rationale after a refusal means "don't ask again", or a restricted setting: the dialog won't show again.
            state.blocked = activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_SMS) } ?: true
        }
    }
    // The permission dialog does not stop the activity, so no AppLockGate skip here (it would linger).
    state.launch = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
    LifecycleResumeEffect(state) {
        if (state.refresh()) callback()
        onPauseOrDispose { }
    }
    return state
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
