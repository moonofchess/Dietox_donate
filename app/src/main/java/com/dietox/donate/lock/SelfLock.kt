package com.dietox.donate.lock

/** 잠금 상태. 시각은 모두 epoch 밀리초. */
data class LockState(
    val startedAt: Long = 0L,
    val lockedUntil: Long = 0L,
) {
    companion object {
        val NONE = LockState()
    }
}

/** 설정 변경이 차단을 느슨하게 하는지 조이는지. */
enum class ChangeKind {
    /** 차단을 약하게 만드는 변경. 잠금 중에는 막는다. */
    RELAX,

    /** 차단을 강하게 만드는 변경. 언제나 허용한다. */
    TIGHTEN,
}

/**
 * 스스로 건 잠금.
 *
 * 후원을 끊는 데 가장 크게 걸리는 건 차단 기능 자체가 아니라, 지르고 싶은 순간에
 * 사람이 설정으로 들어가 차단을 꺼버린다는 점이다. 그래서 잠금 기간을 정해두면
 * 그 기간에는 본인도 차단을 풀 수 없게 한다. 조이는 방향은 언제든 열려 있다.
 */
object SelfLock {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun isLocked(state: LockState, now: Long): Boolean = now < state.lockedUntil

    fun remainingMillis(state: LockState, now: Long): Long =
        maxOf(0L, state.lockedUntil - now)

    /** 잠금을 시작한다. 이미 잠겨 있으면 더 늦게 끝나는 쪽을 남긴다. */
    fun start(state: LockState, now: Long, durationMillis: Long): LockState {
        val requested = now + maxOf(0L, durationMillis)
        if (requested <= state.lockedUntil) return state
        return LockState(
            startedAt = if (isLocked(state, now)) state.startedAt else now,
            lockedUntil = requested,
        )
    }

    /** 잠금을 연장한다. 남은 시간에 더하므로 언제나 허용된다. */
    fun extend(state: LockState, now: Long, additionalMillis: Long): LockState {
        val base = maxOf(now, state.lockedUntil)
        return LockState(
            startedAt = if (state.startedAt == 0L) now else state.startedAt,
            lockedUntil = base + maxOf(0L, additionalMillis),
        )
    }

    /**
     * 잠금 해제를 시도한다.
     *
     * 기간이 남아 있으면 거부한다. 예외 통로를 두지 않는 것이 이 기능의 전부다.
     */
    fun release(state: LockState, now: Long): LockState? =
        if (isLocked(state, now)) null else LockState.NONE

    /** 이 설정 변경을 지금 허용해도 되는지. */
    fun isChangeAllowed(kind: ChangeKind, state: LockState, now: Long): Boolean =
        kind == ChangeKind.TIGHTEN || !isLocked(state, now)

    /** 남은 시간을 "3일 4시간" 처럼 읽히게 만든다. */
    fun formatRemaining(millis: Long): String {
        if (millis <= 0L) return "잠금 없음"

        val days = millis / DAY
        val hours = (millis % DAY) / HOUR
        val minutes = (millis % HOUR) / MINUTE

        return when {
            days > 0 -> "${days}일 ${hours}시간"
            hours > 0 -> "${hours}시간 ${minutes}분"
            minutes > 0 -> "${minutes}분"
            else -> "1분 미만"
        }
    }

    /** 설정 화면에서 고르게 할 잠금 기간들. */
    val PRESETS: List<Pair<String, Long>> = listOf(
        "1일" to DAY,
        "3일" to 3 * DAY,
        "1주" to 7 * DAY,
        "2주" to 14 * DAY,
        "1개월" to 30 * DAY,
        "3개월" to 90 * DAY,
    )
}
