package com.dietox.donate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dietox.donate.data.GuardSettings
import com.dietox.donate.data.SettingsRepository
import com.dietox.donate.lock.SelfLock
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    settings: GuardSettings,
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    guardedLabels: List<String>,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onPickApps: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAntiTamper: (Boolean) -> Unit,
    onStartLock: (Long) -> Unit,
    onAverageChange: (Int) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val locked = SelfLock.isLocked(settings.lock, now)
    val ready = accessibilityOn && overlayOn && settings.guardedPackages.isNotEmpty()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Dietox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "치지직 후원 버튼과 치즈 충전 화면을 막아 줍니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SetupCard(
            accessibilityOn = accessibilityOn,
            overlayOn = overlayOn,
            guardedLabels = guardedLabels,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlay = onOpenOverlay,
            onPickApps = onPickApps,
        )

        GuardCard(
            settings = settings,
            ready = ready,
            locked = locked,
            now = now,
            onToggleEnabled = onToggleEnabled,
        )

        StatsCard(settings = settings, now = now)

        LockCard(locked = locked, settings = settings, now = now, onStartLock = onStartLock)

        TuningCard(
            settings = settings,
            locked = locked,
            onToggleAntiTamper = onToggleAntiTamper,
            onAverageChange = onAverageChange,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupCard(
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    guardedLabels: List<String>,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onPickApps: () -> Unit,
) {
    SectionCard("준비하기") {
        SetupRow(
            done = accessibilityOn,
            title = "접근성 권한",
            detail = "화면에 후원 버튼이 떴는지 알아보는 데 필요합니다",
            action = "열기",
            onAction = onOpenAccessibility,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SetupRow(
            done = overlayOn,
            title = "다른 앱 위에 표시",
            detail = "후원 버튼을 덮개로 가리는 데 필요합니다",
            action = "열기",
            onAction = onOpenOverlay,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SetupRow(
            done = guardedLabels.isNotEmpty(),
            title = "감시할 앱",
            detail = if (guardedLabels.isEmpty()) "목록에서 치지직을 골라 주세요"
            else guardedLabels.joinToString(", "),
            action = "고르기",
            onAction = onPickApps,
        )
    }
}

@Composable
private fun SetupRow(
    done: Boolean,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (done) "●" else "○",
            color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun GuardCard(
    settings: GuardSettings,
    ready: Boolean,
    locked: Boolean,
    now: Long,
    onToggleEnabled: (Boolean) -> Unit,
) {
    SectionCard("차단") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        !settings.enabled -> "꺼져 있음"
                        ready -> "작동 중"
                        else -> "준비가 덜 됐습니다"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (settings.enabled && ready) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (locked) {
                        "잠금 " + SelfLock.formatRemaining(
                            SelfLock.remainingMillis(settings.lock, now)
                        ) + " 남아 끌 수 없습니다"
                    } else {
                        "후원 버튼은 덮고, 충전·결제 화면은 되돌립니다"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = onToggleEnabled,
                enabled = !locked,
            )
        }
    }
}

@Composable
private fun StatsCard(settings: GuardSettings, now: Long) {
    SectionCard("기록") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                modifier = Modifier.weight(1f),
                value = "${SettingsRepository.streakDays(settings.streakStartedAt, now)}일",
                label = "참는 중",
            )
            Stat(
                modifier = Modifier.weight(1f),
                value = "${settings.blockedTotal}회",
                label = "막은 횟수",
            )
            Stat(
                modifier = Modifier.weight(1f),
                value = "%,d원".format(settings.estimatedSaved),
                label = "아낀 돈(추정)",
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "오늘 ${settings.blockedToday}번 막았습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LockCard(
    locked: Boolean,
    settings: GuardSettings,
    now: Long,
    onStartLock: (Long) -> Unit,
) {
    SectionCard(if (locked) "잠금 중" else "잠금 걸기") {
        Text(
            text = if (locked) {
                "끝나기까지 " + SelfLock.formatRemaining(
                    SelfLock.remainingMillis(settings.lock, now)
                ) + " 남았습니다. 기간을 더 늘릴 수는 있지만 줄이거나 풀 수는 없습니다."
            } else {
                "기간을 정해 두면 그동안은 본인도 차단을 끌 수 없습니다. 지르고 싶은 순간에 설정을 여는 걸 막아 줍니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelfLock.PRESETS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, duration) ->
                        FilledTonalButton(
                            onClick = { onStartLock(duration) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (locked) "+$label" else label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TuningCard(
    settings: GuardSettings,
    locked: Boolean,
    onToggleAntiTamper: (Boolean) -> Unit,
    onAverageChange: (Int) -> Unit,
) {
    SectionCard("세부 설정") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("설정 앱 접근 막기", style = MaterialTheme.typography.titleSmall)
                Text(
                    "잠금 중에 접근성 설정으로 들어가면 홈으로 되돌립니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.antiTamper,
                onCheckedChange = onToggleAntiTamper,
                enabled = !locked || !settings.antiTamper,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = if (settings.averageDonation == 0) "" else settings.averageDonation.toString(),
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(7)
                onAverageChange(digits.toIntOrNull() ?: 0)
            },
            label = { Text("한 번 후원할 때 쓰던 금액(원)") },
            supportingText = { Text("아낀 돈을 어림잡는 데만 씁니다") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
