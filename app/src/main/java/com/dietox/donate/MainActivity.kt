package com.dietox.donate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietox.donate.data.GuardSettings
import com.dietox.donate.data.SettingsRepository
import com.dietox.donate.lock.ChangeKind
import com.dietox.donate.lock.SelfLock
import com.dietox.donate.service.DonationGuardService
import com.dietox.donate.ui.AppPickerScreen
import com.dietox.donate.ui.DietoxTheme
import com.dietox.donate.ui.HomeScreen
import com.dietox.donate.util.InstalledApp
import com.dietox.donate.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(applicationContext)
        setContent {
            DietoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DietoxApp(repository)
                }
            }
        }
    }
}

@Composable
private fun DietoxApp(repository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = GuardSettings())
    var picking by rememberSaveable { mutableStateOf(false) }

    AskForNotificationPermission()

    // 설정 화면에 다녀오면 권한 상태가 바뀌어 있으므로 돌아올 때마다 다시 읽는다.
    val resumeTick = rememberResumeTick()
    val accessibilityOn = remember(resumeTick) { DonationGuardService.isEnabled(context) }
    val overlayOn = remember(resumeTick) { Settings.canDrawOverlays(context) }

    val apps by produceState(initialValue = emptyList<InstalledApp>(), resumeTick) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }
    val guardedLabels = remember(settings.guardedPackages, apps) {
        settings.guardedPackages.map { pkg ->
            apps.firstOrNull { it.packageName == pkg }?.label
                ?: InstalledApps.labelFor(context, pkg)
        }.sorted()
    }

    fun edit(kind: ChangeKind, transform: (GuardSettings) -> GuardSettings) {
        scope.launch {
            repository.update { current ->
                val allowed = SelfLock.isChangeAllowed(kind, current.lock, System.currentTimeMillis())
                if (allowed) transform(current) else current
            }
        }
    }

    BackHandler(enabled = picking) { picking = false }

    if (picking) {
        val locked = SelfLock.isLocked(settings.lock, System.currentTimeMillis())
        AppPickerScreen(
            apps = apps,
            selected = settings.guardedPackages,
            canRemove = !locked,
            onToggle = { packageName, checked ->
                val kind = if (checked) ChangeKind.TIGHTEN else ChangeKind.RELAX
                edit(kind) { current ->
                    val next = current.guardedPackages.toMutableSet()
                    if (checked) next += packageName else next -= packageName
                    current.copy(guardedPackages = next)
                }
            },
            onBack = { picking = false },
        )
        return
    }

    HomeScreen(
        settings = settings,
        accessibilityOn = accessibilityOn,
        overlayOn = overlayOn,
        guardedLabels = guardedLabels,
        onOpenAccessibility = { context.openAccessibilitySettings() },
        onOpenOverlay = { context.openOverlaySettings() },
        onPickApps = { picking = true },
        onToggleEnabled = { enabled ->
            val kind = if (enabled) ChangeKind.TIGHTEN else ChangeKind.RELAX
            edit(kind) { it.copy(enabled = enabled) }
        },
        onToggleAntiTamper = { on ->
            val kind = if (on) ChangeKind.TIGHTEN else ChangeKind.RELAX
            edit(kind) { it.copy(antiTamper = on) }
        },
        onStartLock = { duration ->
            edit(ChangeKind.TIGHTEN) { current ->
                val now = System.currentTimeMillis()
                current.copy(
                    enabled = true,
                    lock = SelfLock.start(current.lock, now, duration),
                    streakStartedAt = if (current.streakStartedAt == 0L) now else current.streakStartedAt,
                )
            }
        },
        onAverageChange = { amount ->
            edit(ChangeKind.TIGHTEN) { it.copy(averageDonation = amount) }
        },
    )
}

/** 화면으로 돌아올 때마다 값이 하나씩 오르는 카운터. 권한 상태를 다시 읽는 방아쇠로 쓴다. */
@Composable
private fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
private fun AskForNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun Context.openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.openOverlaySettings() {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
