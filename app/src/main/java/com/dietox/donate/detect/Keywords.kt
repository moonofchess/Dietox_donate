package com.dietox.donate.detect

/**
 * 화면에서 후원·결제 흐름을 알아보기 위한 키워드 모음.
 *
 * 모든 키워드는 [squash] 를 거친 형태(소문자·공백 제거)로 비교한다.
 * 치지직 앱은 업데이트마다 문구가 조금씩 바뀌므로, 특정 리소스 ID 대신
 * 사람이 읽는 문구를 기준으로 판단한다.
 */
object Keywords {

    /** 인앱 결제 시트를 띄우는 패키지들. 이 패키지가 앞에 나오면 결제 진행 중이다. */
    val BILLING_PACKAGES = setOf(
        "com.android.vending",
        "com.sec.android.app.billing",
        "com.samsung.android.iap",
        "com.skt.skaf.A000Z00040",
        "com.kt.olleh.storefront",
        "com.lguplus.appstore",
    )

    /** 시스템 설정 앱. 잠금 중 접근성 서비스를 끄러 들어가는 걸 막는 데 쓴다. */
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
    )

    /**
     * 티어 A — 결제 확정 신호.
     *
     * 결제·충전 흐름에서만 등장하는 문구라서, 하나라도 보이면 즉시 되돌린다.
     * 방송 시청 화면에는 나오지 않으므로 오탐으로 시청이 끊길 위험이 거의 없다.
     */
    val PURCHASE_SIGNALS = listOf(
        "치즈충전",
        "충전금액",
        "충전할치즈",
        "충전하기",
        "결제수단",
        "결제하기",
        "결제진행",
        "결제금액",
        "결제정보",
        "인앱결제",
        "구매하기",
        "구매확인",
        "주문내역",
        "포인트충전",
        "캐시충전",
        "googleplay결제",
        "결제를진행",
    )

    /**
     * 티어 B — 후원 작성 시트에만 나오는 문구.
     *
     * "후원" 한 단어는 채팅에도 흘러다니므로 절대 단독으로 쓰지 않는다.
     * 금액을 정해 보내는 화면 특유의 표현만 골랐다.
     */
    val DONATION_SHEET_SIGNALS = listOf(
        "보낼치즈",
        "치즈보내기",
        "후원금액",
        "후원하기",
        "후원메시지",
        "메시지후원",
        "영상후원",
        "음성후원",
        "미션후원",
        "치즈후원",
        "도네이션보내기",
        "후원할치즈",
    )

    /**
     * 티어 C — 후원 진입 버튼에 붙는 짧은 라벨.
     *
     * 시청 화면에 상주하기 때문에 되돌리지 않고 오버레이로 덮기만 한다.
     */
    val DONATION_BUTTON_LABELS = listOf(
        "후원",
        "도네",
        "도네이션",
        "치즈",
        "donation",
        "donate",
        "cheese",
        "supportstreamer",
    )

    /** 버튼 리소스 ID에서 후원 진입 지점을 알아보는 조각들. */
    val DONATION_VIEW_ID_HINTS = listOf(
        "donation",
        "donate",
        "cheese",
        "sponsor",
        "charge",
        "billing",
        "purchase",
    )

    /**
     * 남이 한 후원을 알리는 문구들.
     *
     * 채팅창과 후원 알림 배너에 계속 흘러나오므로, 신호를 세기 전에 먼저 걷어낸다.
     * 이 필터가 없으면 방송을 보는 것만으로 차단이 걸린다.
     */
    val OTHERS_DONATION_PATTERNS = listOf(
        Regex("님\\s*(이|의|께서|,)?\\s*.{0,12}(후원|도네|치즈)"),
        Regex("(후원|도네)\\s*(해\\s*주(셔서|신|셨)|감사)"),
        Regex("(후원|도네)\\s*(랭킹|순위|목록|내역|왕)"),
        Regex("^\\s*[0-9,]+\\s*(치즈|개)\\s*$"),
    )

    /** 잠금 중 접근성 설정을 건드리러 들어온 걸 알아보는 문구들. */
    val TAMPER_SIGNALS = listOf(
        "설치된앱",
        "설치된서비스",
        "다운로드한앱",
        "접근성",
        "accessibility",
        "dietox",
    )
}

/** 문구 비교용 정규화: 소문자로 바꾸고 공백류를 모두 없앤다. */
fun squash(text: String): String =
    text.lowercase().replace(Regex("[\\s\\u00A0\\u200B]+"), "")
