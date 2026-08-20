package com.dietox.donate.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dietox.donate.detect.Bounds

/**
 * 후원 버튼 위에 덮개를 씌우고, 차단 안내를 잠깐 띄운다.
 *
 * 되돌리기로 처리하면 방송 시청까지 끊기는 상주 버튼을 다루기 위한 장치다.
 * 덮개는 터치를 그대로 삼키기 때문에 버튼을 누를 수 없다.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val covers = mutableListOf<View>()
    private var shownBounds: List<Bounds> = emptyList()

    private var notice: View? = null
    private val dismissNotice = Runnable { hideNotice() }

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /** 덮개를 [targets] 에 맞춘다. 이미 같은 자리에 떠 있으면 손대지 않는다. */
    fun showCovers(targets: List<Bounds>) {
        if (!canDraw()) return
        if (targets == shownBounds) return

        clearCovers()
        for (bounds in targets) {
            val view = createCoverView()
            val params = coverParams(bounds)
            runCatching { windowManager.addView(view, params) }
                .onSuccess { covers += view }
        }
        shownBounds = targets
    }

    fun clearCovers() {
        for (view in covers) {
            runCatching { windowManager.removeView(view) }
        }
        covers.clear()
        shownBounds = emptyList()
    }

    /** 차단 안내를 화면 위쪽에 잠깐 띄운다. */
    fun showNotice(message: String, durationMillis: Long = 2_600L) {
        if (!canDraw()) return

        handler.removeCallbacks(dismissNotice)
        val view = notice ?: createNoticeView().also { created ->
            runCatching { windowManager.addView(created, noticeParams()) }
                .onSuccess { notice = created }
                .onFailure { return }
        }
        (view as TextView).text = message
        handler.postDelayed(dismissNotice, durationMillis)
    }

    private fun hideNotice() {
        notice?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        notice = null
    }

    /** 서비스가 내려갈 때 띄운 창을 모두 걷는다. */
    fun destroy() {
        handler.removeCallbacks(dismissNotice)
        clearCovers()
        hideNotice()
    }

    private fun createCoverView(): View = View(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(12f)
            setColor(COVER_COLOR)
            setStroke(dp(1f).toInt(), COVER_STROKE)
        }
        isClickable = true
        setOnClickListener { showNotice("후원은 잠겨 있습니다") }
    }

    private fun createNoticeView(): View = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        val padH = dp(20f).toInt()
        val padV = dp(14f).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(NOTICE_COLOR)
        }
    }

    private fun coverParams(bounds: Bounds) = WindowManager.LayoutParams(
        maxOf(1, bounds.width),
        maxOf(1, bounds.height),
        bounds.left,
        bounds.top,
        OVERLAY_TYPE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private fun noticeParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        0,
        dp(96f).toInt(),
        OVERLAY_TYPE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private fun dp(value: Float): Float =
        value * context.resources.displayMetrics.density

    private companion object {
        const val OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val COVER_COLOR = Color.argb(238, 24, 24, 27)
        val COVER_STROKE = Color.argb(255, 99, 102, 241)
        val NOTICE_COLOR = Color.argb(242, 30, 30, 34)
    }
}
