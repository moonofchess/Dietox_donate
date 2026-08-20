package com.dietox.donate

import com.dietox.donate.lock.ChangeKind
import com.dietox.donate.lock.LockState
import com.dietox.donate.lock.SelfLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val DAY = 24 * 60 * 60 * 1000L

class SelfLockTest {

    @Test
    fun `잠금을 걸면 기간 동안 잠긴다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 7 * DAY)

        assertTrue(SelfLock.isLocked(state, NOW))
        assertTrue(SelfLock.isLocked(state, NOW + 6 * DAY))
        assertFalse(SelfLock.isLocked(state, NOW + 7 * DAY))
    }

    @Test
    fun `잠금 중에는 본인도 해제할 수 없다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 3 * DAY)
        assertNull(SelfLock.release(state, NOW + 2 * DAY))
    }

    @Test
    fun `기간이 지나면 해제된다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 3 * DAY)
        val released = SelfLock.release(state, NOW + 3 * DAY)

        assertNotNull(released)
        assertEquals(LockState.NONE, released)
    }

    @Test
    fun `잠금 기간을 줄이려는 시도는 무시한다`() {
        val long = SelfLock.start(LockState.NONE, NOW, 30 * DAY)
        val attempted = SelfLock.start(long, NOW, 1 * DAY)

        assertEquals(long, attempted)
        assertTrue(SelfLock.isLocked(attempted, NOW + 29 * DAY))
    }

    @Test
    fun `연장은 잠금 중에도 된다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 3 * DAY)
        val extended = SelfLock.extend(state, NOW + 1 * DAY, 7 * DAY)

        assertEquals(NOW + 10 * DAY, extended.lockedUntil)
        assertEquals(state.startedAt, extended.startedAt)
    }

    @Test
    fun `잠금 중에는 느슨하게 바꾸지 못하고 조이는 건 된다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 3 * DAY)

        assertFalse(SelfLock.isChangeAllowed(ChangeKind.RELAX, state, NOW))
        assertTrue(SelfLock.isChangeAllowed(ChangeKind.TIGHTEN, state, NOW))
    }

    @Test
    fun `잠금이 끝나면 어떤 변경이든 허용한다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 3 * DAY)
        val later = NOW + 4 * DAY

        assertTrue(SelfLock.isChangeAllowed(ChangeKind.RELAX, state, later))
        assertTrue(SelfLock.isChangeAllowed(ChangeKind.TIGHTEN, state, later))
    }

    @Test
    fun `남은 시간을 읽기 좋게 보여준다`() {
        assertEquals("잠금 없음", SelfLock.formatRemaining(0))
        assertEquals("3일 4시간", SelfLock.formatRemaining(3 * DAY + 4 * 60 * 60 * 1000L))
        assertEquals("5시간 30분", SelfLock.formatRemaining(5 * 60 * 60 * 1000L + 30 * 60 * 1000L))
        assertEquals("12분", SelfLock.formatRemaining(12 * 60 * 1000L))
        assertEquals("1분 미만", SelfLock.formatRemaining(30 * 1000L))
    }

    @Test
    fun `남은 시간은 음수가 되지 않는다`() {
        val state = SelfLock.start(LockState.NONE, NOW, 1 * DAY)
        assertEquals(0L, SelfLock.remainingMillis(state, NOW + 5 * DAY))
    }
}
