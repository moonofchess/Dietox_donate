package com.dietox.donate

import com.dietox.donate.detect.Bounds
import com.dietox.donate.detect.DonationDetector
import com.dietox.donate.detect.NodeInfo
import com.dietox.donate.detect.Screen
import com.dietox.donate.detect.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHZZK = "com.naver.chzzk"
private const val SCREEN_W = 1080
private const val SCREEN_H = 2400

private fun screen(vararg nodes: NodeInfo, pkg: String = CHZZK) =
    Screen(pkg, nodes.toList(), SCREEN_W, SCREEN_H)

private fun button(text: String, viewId: String = "", bounds: Bounds = Bounds(900, 2100, 1040, 2240)) =
    NodeInfo(text = text, viewId = viewId, clickable = true, bounds = bounds)

private fun label(text: String) =
    NodeInfo(text = text, clickable = false, bounds = Bounds(40, 200, 600, 260))

private fun chatInput() =
    NodeInfo(text = "채팅을 입력해 주세요", clickable = true, editable = true, bounds = Bounds(40, 2200, 800, 2300))

class DonationDetectorTest {

    @Test
    fun `빈 화면은 그대로 둔다`() {
        assertEquals(Verdict.Allow, DonationDetector.detect(screen()))
    }

    @Test
    fun `방송 시청 중 남의 후원 알림이 흘러도 시청을 끊지 않는다`() {
        // 이게 무너지면 방송을 볼 수가 없다. 후원 버튼은 덮되 되돌리지는 않아야 한다.
        val verdict = DonationDetector.detect(
            screen(
                label("우주최강게이머 님이 1,000 치즈 후원!"),
                label("감자칩 님이 5,000 치즈 후원하셨습니다"),
                label("후원해주셔서 감사합니다"),
                NodeInfo(text = "노랑이: 오늘 방송 언제까지 해요?", bounds = Bounds(40, 1500, 900, 1560)),
                chatInput(),
                button("후원", viewId = "com.naver.chzzk:id/btn_donation"),
            )
        )

        assertTrue("되돌리기가 아니라 덮기여야 한다. 실제: $verdict", verdict is Verdict.Cover)
        assertEquals(1, (verdict as Verdict.Cover).targets.size)
        assertEquals("후원", verdict.targets.first().text)
    }

    @Test
    fun `치즈 충전 화면은 되돌린다`() {
        val verdict = DonationDetector.detect(
            screen(
                label("치즈 충전"),
                label("충전 금액"),
                button("10,000원"),
            )
        )
        assertTrue(verdict is Verdict.Block)
        assertEquals("치즈 충전·결제 화면", (verdict as Verdict.Block).reason)
    }

    @Test
    fun `후원 시트는 되돌린다`() {
        val verdict = DonationDetector.detect(
            screen(
                label("보낼 치즈"),
                NodeInfo(text = "후원 메시지를 입력하세요", editable = true, bounds = Bounds(40, 900, 1000, 1000)),
                button("후원하기"),
            )
        )
        assertTrue("실제: $verdict", verdict is Verdict.Block)
        assertEquals("후원 화면", (verdict as Verdict.Block).reason)
    }

    @Test
    fun `후원 신호가 라벨로 하나만 있어도 시트로 본다`() {
        val verdict = DonationDetector.detect(screen(label("영상 후원")))
        assertTrue("실제: $verdict", verdict is Verdict.Block)
    }

    @Test
    fun `상주하는 후원 버튼 하나만으로는 되돌리지 않는다`() {
        // "후원하기"는 시트 문구이기도 하지만, 누를 수 있는 버튼 하나뿐이면 판단을 미룬다.
        val verdict = DonationDetector.detect(screen(button("후원하기")))
        assertTrue("실제: $verdict", verdict is Verdict.Cover)
    }

    @Test
    fun `서로 다른 후원 신호가 둘 이상 모이면 되돌린다`() {
        val verdict = DonationDetector.detect(
            screen(
                button("후원하기"),
                button("치즈 보내기", bounds = Bounds(100, 800, 500, 900)),
            )
        )
        assertTrue("실제: $verdict", verdict is Verdict.Block)
    }

    @Test
    fun `구글 플레이 결제창은 패키지만으로 되돌린다`() {
        val verdict = DonationDetector.detect(screen(pkg = "com.android.vending"))
        assertTrue(verdict is Verdict.Block)
        assertEquals("결제 창이 열렸습니다", (verdict as Verdict.Block).reason)
    }

    @Test
    fun `리소스 아이디만으로도 후원 버튼을 찾는다`() {
        val verdict = DonationDetector.detect(
            screen(button(text = "", viewId = "com.naver.chzzk:id/cheese_charge_entry"))
        )
        assertTrue("실제: $verdict", verdict is Verdict.Cover)
    }

    @Test
    fun `화면을 통째로 덮을 만큼 큰 요소는 덮지 않는다`() {
        val verdict = DonationDetector.detect(
            screen(button("후원", bounds = Bounds(0, 0, SCREEN_W, SCREEN_H)))
        )
        assertEquals(Verdict.Allow, verdict)
    }

    @Test
    fun `버튼 라벨로 보기에 긴 채팅 문장은 덮지 않는다`() {
        val verdict = DonationDetector.detect(
            screen(
                button(
                    "치즈 모아서 다음 주에 같이 게임하기로 했어요 다들 오세요",
                    bounds = Bounds(40, 1500, 900, 1560),
                )
            )
        )
        assertEquals(Verdict.Allow, verdict)
    }

    @Test
    fun `후원 랭킹 문구는 남의 후원으로 걸러낸다`() {
        assertTrue(DonationDetector.isOthersDonation("후원 랭킹"))
        assertTrue(DonationDetector.isOthersDonation("도네 순위"))
        assertTrue(DonationDetector.isOthersDonation("후원 내역"))
        assertFalse(DonationDetector.isOthersDonation("후원하기"))
        assertFalse(DonationDetector.isOthersDonation(""))
    }

    @Test
    fun `공백이 끼어 있어도 결제 문구를 찾아낸다`() {
        val verdict = DonationDetector.detect(screen(label("결 제  수 단")))
        assertTrue("실제: $verdict", verdict is Verdict.Block)
    }
}
