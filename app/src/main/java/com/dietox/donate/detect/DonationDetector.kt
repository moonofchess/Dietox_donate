package com.dietox.donate.detect

/** 화면 좌표. 접근성 노드의 Rect 를 안드로이드 의존 없이 옮겨 담은 것. */
data class Bounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = maxOf(0, width).toLong() * maxOf(0, height).toLong()

    companion object {
        val EMPTY = Bounds()
    }
}

/** 접근성 서비스가 읽어낸 화면 위 요소 하나. */
data class NodeInfo(
    val text: String,
    val viewId: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val bounds: Bounds = Bounds.EMPTY,
)

/** 한 시점의 화면 전체. */
data class Screen(
    val packageName: String,
    val nodes: List<NodeInfo>,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)

/** 화면을 어떻게 처리할지에 대한 판정. */
sealed interface Verdict {
    /** 손대지 않는다. */
    data object Allow : Verdict

    /** 후원 진입 버튼만 오버레이로 덮는다. 방송 시청은 그대로 둔다. */
    data class Cover(val targets: List<NodeInfo>) : Verdict

    /** 화면 자체가 후원·결제 흐름이다. 뒤로 되돌리고 차단 안내를 띄운다. */
    data class Block(val reason: String, val matched: String) : Verdict
}

/**
 * 화면을 보고 후원 흐름인지 판정한다.
 *
 * 판정을 두 단계로 나눈 이유가 핵심이다.
 * 후원 버튼은 방송 시청 화면에 항상 떠 있기 때문에, 버튼이 보인다는 이유로
 * 뒤로가기를 눌러버리면 방송을 볼 수가 없다. 그래서 상주하는 버튼은 덮기만 하고,
 * 되돌리기는 결제·후원 화면에 실제로 진입했을 때만 한다.
 */
object DonationDetector {

    /** 화면의 이 비율을 넘는 큰 요소는 덮지 않는다. 통째로 가려버리는 사고를 막는다. */
    private const val MAX_COVER_AREA_RATIO = 0.25

    /** 버튼 라벨로 보기에 이보다 길면 채팅 같은 본문으로 취급한다. */
    private const val MAX_LABEL_LENGTH = 20

    fun detect(screen: Screen): Verdict {
        if (screen.packageName in Keywords.BILLING_PACKAGES) {
            return Verdict.Block("결제 창이 열렸습니다", screen.packageName)
        }

        // 남이 한 후원 알림은 세기 전에 걷어낸다. 이게 없으면 채팅만 흘러도 차단이 걸린다.
        val nodes = screen.nodes.filterNot { isOthersDonation(it.text) }

        findPurchaseSignal(nodes)?.let {
            return Verdict.Block("치즈 충전·결제 화면", it)
        }

        findDonationSheet(nodes)?.let {
            return Verdict.Block("후원 화면", it)
        }

        val targets = nodes.filter { it.clickable && isDonationEntry(it) && isCoverable(it, screen) }
        return if (targets.isEmpty()) Verdict.Allow else Verdict.Cover(targets)
    }

    /** 티어 A: 결제 흐름에서만 나오는 문구가 하나라도 보이는지. */
    private fun findPurchaseSignal(nodes: List<NodeInfo>): String? {
        for (node in nodes) {
            val text = squash(node.text)
            if (text.isEmpty()) continue
            Keywords.PURCHASE_SIGNALS.firstOrNull { text.contains(it) }?.let { return it }
        }
        return null
    }

    /**
     * 티어 B: 후원 시트에 진입했는지.
     *
     * 버튼 하나만 보이는 건 시청 화면에 상주하는 후원 버튼일 수 있으므로 판단을 미룬다.
     * 라벨(누를 수 없는 텍스트)로 나왔거나 서로 다른 신호가 둘 이상 모였을 때만 시트로 본다.
     */
    private fun findDonationSheet(nodes: List<NodeInfo>): String? {
        val onLabels = linkedSetOf<String>()
        val everywhere = linkedSetOf<String>()

        for (node in nodes) {
            val text = squash(node.text)
            if (text.isEmpty()) continue
            for (signal in Keywords.DONATION_SHEET_SIGNALS) {
                if (!text.contains(signal)) continue
                everywhere += signal
                if (!node.clickable) onLabels += signal
            }
        }

        onLabels.firstOrNull()?.let { return it }
        return if (everywhere.size >= 2) everywhere.joinToString("+") else null
    }

    /** 티어 C: 후원으로 들어가는 버튼인지. */
    private fun isDonationEntry(node: NodeInfo): Boolean {
        val viewId = squash(node.viewId)
        if (viewId.isNotEmpty() && Keywords.DONATION_VIEW_ID_HINTS.any { viewId.contains(it) }) {
            return true
        }

        val text = squash(node.text)
        if (text.isEmpty() || text.length > MAX_LABEL_LENGTH) return false
        return Keywords.DONATION_BUTTON_LABELS.any { text.contains(it) }
    }

    private fun isCoverable(node: NodeInfo, screen: Screen): Boolean {
        if (node.bounds.area <= 0L) return false
        val screenArea = maxOf(0, screen.screenWidth).toLong() * maxOf(0, screen.screenHeight).toLong()
        if (screenArea <= 0L) return true
        return node.bounds.area <= (screenArea * MAX_COVER_AREA_RATIO).toLong()
    }

    /** 남이 한 후원을 알리는 문구인지. 채팅과 후원 알림 배너를 걸러낸다. */
    fun isOthersDonation(text: String): Boolean {
        if (text.isBlank()) return false
        return Keywords.OTHERS_DONATION_PATTERNS.any { it.containsMatchIn(text) }
    }
}
