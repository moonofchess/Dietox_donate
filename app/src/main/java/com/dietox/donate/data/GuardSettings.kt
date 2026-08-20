package com.dietox.donate.data

import com.dietox.donate.lock.LockState

/** 앱 전체 설정과 기록. */
data class GuardSettings(
    /** 차단 동작 여부. 잠금 중에는 끌 수 없다. */
    val enabled: Boolean = true,

    /** 감시할 앱 패키지들. 설치된 앱 목록에서 직접 고른다. */
    val guardedPackages: Set<String> = emptySet(),

    /** 스스로 건 잠금. */
    val lock: LockState = LockState.NONE,

    /** 접근성 설정을 끄러 들어가는 걸 막을지. */
    val antiTamper: Boolean = true,

    /** 절약 금액을 어림잡는 데 쓰는 1회 평균 후원액(원). */
    val averageDonation: Int = 5_000,

    /** 지금까지 막은 횟수. */
    val blockedTotal: Int = 0,

    /** 오늘 막은 횟수. */
    val blockedToday: Int = 0,

    /** [blockedToday] 가 가리키는 날짜. yyyy-MM-dd. */
    val todayKey: String = "",

    /** 후원을 참기 시작한 날. 연속 일수를 세는 기준. */
    val streakStartedAt: Long = 0L,
) {
    /** 막은 횟수로 어림잡은 절약 금액(원). */
    val estimatedSaved: Long get() = blockedTotal.toLong() * averageDonation
}
