package com.dietox.donate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dietox.donate.lock.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dietox_guard")

/** 설정과 기록을 저장하고 읽는다. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val PACKAGES = stringSetPreferencesKey("guarded_packages")
        val LOCK_STARTED = longPreferencesKey("lock_started_at")
        val LOCK_UNTIL = longPreferencesKey("lock_until")
        val ANTI_TAMPER = booleanPreferencesKey("anti_tamper")
        val AVERAGE_DONATION = intPreferencesKey("average_donation")
        val BLOCKED_TOTAL = intPreferencesKey("blocked_total")
        val BLOCKED_TODAY = intPreferencesKey("blocked_today")
        val TODAY_KEY = stringPreferencesKey("today_key")
        val STREAK_STARTED = longPreferencesKey("streak_started_at")
    }

    val settings: Flow<GuardSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (GuardSettings) -> GuardSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.ENABLED] = next.enabled
            prefs[Keys.PACKAGES] = next.guardedPackages
            prefs[Keys.LOCK_STARTED] = next.lock.startedAt
            prefs[Keys.LOCK_UNTIL] = next.lock.lockedUntil
            prefs[Keys.ANTI_TAMPER] = next.antiTamper
            prefs[Keys.AVERAGE_DONATION] = next.averageDonation
            prefs[Keys.BLOCKED_TOTAL] = next.blockedTotal
            prefs[Keys.BLOCKED_TODAY] = next.blockedToday
            prefs[Keys.TODAY_KEY] = next.todayKey
            prefs[Keys.STREAK_STARTED] = next.streakStartedAt
        }
    }

    /** 한 번 막았다고 기록한다. 날짜가 바뀌었으면 오늘 집계를 새로 시작한다. */
    suspend fun recordBlock(now: Long) {
        val today = dayKey(now)
        update { current ->
            current.copy(
                blockedTotal = current.blockedTotal + 1,
                blockedToday = if (current.todayKey == today) current.blockedToday + 1 else 1,
                todayKey = today,
                streakStartedAt = if (current.streakStartedAt == 0L) now else current.streakStartedAt,
            )
        }
    }

    private fun Preferences.toSettings(): GuardSettings = GuardSettings(
        enabled = this[Keys.ENABLED] ?: true,
        guardedPackages = this[Keys.PACKAGES] ?: emptySet(),
        lock = LockState(
            startedAt = this[Keys.LOCK_STARTED] ?: 0L,
            lockedUntil = this[Keys.LOCK_UNTIL] ?: 0L,
        ),
        antiTamper = this[Keys.ANTI_TAMPER] ?: true,
        averageDonation = this[Keys.AVERAGE_DONATION] ?: 5_000,
        blockedTotal = this[Keys.BLOCKED_TOTAL] ?: 0,
        blockedToday = this[Keys.BLOCKED_TODAY] ?: 0,
        todayKey = this[Keys.TODAY_KEY] ?: "",
        streakStartedAt = this[Keys.STREAK_STARTED] ?: 0L,
    )

    companion object {
        fun dayKey(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        /** 참기 시작한 날부터 오늘까지 며칠째인지. */
        fun streakDays(startedAt: Long, now: Long): Long {
            if (startedAt <= 0L || now < startedAt) return 0L
            val zone = ZoneId.systemDefault()
            val from = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
            val to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
        }
    }
}
