package com.dietox.donate.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.dietox.donate.MainActivity
import com.dietox.donate.R
import com.dietox.donate.data.GuardSettings
import com.dietox.donate.data.SettingsRepository
import com.dietox.donate.detect.Bounds
import com.dietox.donate.detect.DonationDetector
import com.dietox.donate.detect.Keywords
import com.dietox.donate.detect.NodeInfo
import com.dietox.donate.detect.Screen
import com.dietox.donate.detect.Verdict
import com.dietox.donate.detect.squash
import com.dietox.donate.lock.SelfLock
import com.dietox.donate.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 화면을 읽어 후원 흐름을 막는 접근성 서비스.
 *
 * 상주하는 후원 버튼은 덮개로 가리고, 후원·결제 화면에 진입하면 뒤로 되돌린다.
 * 판정 자체는 [DonationDetector] 에 있고 여기서는 안드로이드와의 연결만 담당한다.
 */
class DonationGuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SettingsRepository
    private lateinit var overlay: OverlayController

    @Volatile
    private var settings = GuardSettings()

    private var lastBlockAt = 0L
    private var lastGuardedSeenAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = SettingsRepository(applicationContext)
        overlay = OverlayController(this)
        running = true

        scope.launch {
            repository.settings.collectLatest { latest ->
                settings = latest
                postStatusNotification(latest)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val current = settings
        if (!current.enabled) {
            overlay.clearCovers()
            return
        }

        val packageName = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return

        val now = System.currentTimeMillis()

        if (packageName in Keywords.SETTINGS_PACKAGES) {
            handleSettingsScreen(current, now)
            return
        }

        val guarded = packageName in current.guardedPackages
        val billingAfterGuarded = packageName in Keywords.BILLING_PACKAGES &&
            now - lastGuardedSeenAt <= BILLING_GRACE_MILLIS

        if (!guarded && !billingAfterGuarded) {
            overlay.clearCovers()
            return
        }
        if (guarded) lastGuardedSeenAt = now

        val root = rootInActiveWindow ?: return
        val screen = readScreen(packageName, root)

        when (val verdict = DonationDetector.detect(screen)) {
            is Verdict.Allow -> overlay.clearCovers()
            is Verdict.Cover -> overlay.showCovers(verdict.targets.map { it.bounds })
            is Verdict.Block -> block(verdict, now)
        }
    }

    /** 잠금 중에 접근성 설정이나 앱 정보로 들어가는 걸 되돌린다. */
    private fun handleSettingsScreen(current: GuardSettings, now: Long) {
        overlay.clearCovers()
        if (!current.antiTamper) return
        if (!SelfLock.isLocked(current.lock, now)) return

        val root = rootInActiveWindow ?: return
        val texts = readScreen(Keywords.SETTINGS_PACKAGES.first(), root).nodes
        val tampering = texts.any { node ->
            val text = squash(node.text)
            text.isNotEmpty() && Keywords.TAMPER_SIGNALS.any { text.contains(it) }
        }
        if (!tampering) return
        if (now - lastBlockAt < BLOCK_COOLDOWN_MILLIS) return

        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val remaining = SelfLock.formatRemaining(SelfLock.remainingMillis(current.lock, now))
        overlay.showNotice("잠금이 $remaining 남았습니다. 지금은 끌 수 없어요")
    }

    private fun block(verdict: Verdict.Block, now: Long) {
        overlay.clearCovers()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MILLIS) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        overlay.showNotice("${verdict.reason}을 막았습니다")
        scope.launch { repository.recordBlock(now) }
    }

    /** 화면의 노드를 훑어 판정에 필요한 만큼만 담아 온다. */
    private fun readScreen(packageName: String, root: AccessibilityNodeInfo): Screen {
        val nodes = ArrayList<NodeInfo>(64)
        collect(root, nodes, depth = 0)
        val metrics = resources.displayMetrics
        return Screen(packageName, nodes, metrics.widthPixels, metrics.heightPixels)
    }

    private fun collect(node: AccessibilityNodeInfo?, into: MutableList<NodeInfo>, depth: Int) {
        if (node == null || depth > MAX_DEPTH || into.size >= MAX_NODES) return

        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val label = if (text.isNotBlank()) text else description
        val viewId = node.viewIdResourceName.orEmpty()

        if (label.isNotBlank() || viewId.isNotEmpty()) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            into += NodeInfo(
                text = label,
                viewId = viewId,
                clickable = node.isClickable,
                editable = node.isEditable,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            )
        }

        for (index in 0 until node.childCount) {
            collect(node.getChild(index), into, depth + 1)
        }
    }

    private fun postStatusNotification(current: GuardSettings) {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "차단 상태", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "후원 차단이 켜져 있는지 알려 줍니다" }
            )
        }

        val locked = SelfLock.isLocked(current.lock, System.currentTimeMillis())
        val text = when {
            !current.enabled -> "차단이 꺼져 있습니다"
            current.guardedPackages.isEmpty() -> "감시할 앱을 골라 주세요"
            locked -> "잠금 " + SelfLock.formatRemaining(
                SelfLock.remainingMillis(current.lock, System.currentTimeMillis())
            ) + " 남음"
            else -> "후원 차단 작동 중"
        }

        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(intent)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        running = false
        overlay.destroy()
        scope.cancel()
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        private const val MAX_DEPTH = 24
        private const val MAX_NODES = 400
        private const val BLOCK_COOLDOWN_MILLIS = 1_200L
        private const val BILLING_GRACE_MILLIS = 60_000L
        private const val CHANNEL_ID = "dietox_guard_status"
        private const val NOTIFICATION_ID = 4801

        @Volatile
        private var running = false

        /** 접근성 설정에서 이 서비스가 켜져 있는지. */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${DonationGuardService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return running || enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
