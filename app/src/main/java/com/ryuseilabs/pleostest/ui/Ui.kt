package com.ryuseilabs.pleostest.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.graphics.text.LineBreaker
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import com.ryuseilabs.pleostest.R
import android.view.View
import android.util.TypedValue
import android.view.ViewGroup
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 앱 전체 디자인 시스템 — Pleos Connect 컬러 토큰(docs/color-guide.md)을 그대로 사용한다.
 * 시스템 라이트/다크 설정을 따르며, 각 Activity.onCreate 첫머리에서 Palette.init(this)를 호출한다.
 *
 * 토큰 매핑 (Light / Dark)
 *  bg        = background_primary(basic_50) / background_secondary(basic_00)
 *  surface   = surface_basic(basic_00)      / basic_50
 *  surfaceHi = surface_high(basic_100)      / basic_100
 *  line      = basic_200                    / basic_100
 *  text      = basic_900 / basic_900(반전),  textSub = basic_500,  textDim = basic_400
 *  btnFill   = button_filled(basic_600)     — 기본 버튼은 포인트 색이 아니라 뉴트럴
 *  blue      = informative_positive,  mint = informative_active,  amber = regulation_yellow
 * 색은 상태 표시(현재/완료/달성)에만 쓰고 나머지는 grayscale로 둔다.
 */
object Palette {
    private var night = false

    fun init(context: Context) {
        night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun pick(day: Long, dark: Long): Int = (if (night) dark else day).toInt()

    // Grayscale (basic_xx)
    val bg get() = pick(0xFFF7F8FA, 0xFF131417)
    val surface get() = pick(0xFFFFFFFF, 0xFF1D1E21)
    val surfaceHi get() = pick(0xFFEDEEF2, 0xFF313236)
    val selected get() = pick(0xFFFFFFFF, 0xFF44464E)
    val line get() = pick(0xFFE0E1E6, 0xFF313236)

    val text get() = pick(0xFF131417, 0xFFFFFFFF)
    val textSub get() = pick(0xFF686A72, 0xFFC7C9CE)
    val textDim get() = pick(0xFFA4A8AE, 0xFFA4A8AE)

    // 종이(로드북 페이지) — Pleos 뉴트럴 위에 살짝 따뜻한 종이 톤. 글자는 text/textSub 그대로 쓴다
    val paper get() = pick(0xFFFFFDF8, 0xFF24252A)
    val paperEdge get() = pick(0xFFE6E1D3, 0xFF32333A)
    val paperLine get() = pick(0xFFEFEBDF, 0xFF2E2F35)
    val paperDot get() = pick(0xFFDDD7C6, 0xFF3A3B42)

    // 기본(filled) 버튼
    val btnFill get() = pick(0xFF44464E, 0xFFE0E1E6)
    val btnText get() = pick(0xFFFFFFFF, 0xFF131417)

    // Informative / Regulation
    val blue get() = pick(0xFF0064FF, 0xFF0082FF)
    val blueSoft get() = pick(0x1A0064FF, 0x330082FF)
    val mint get() = pick(0xFF02C265, 0xFF00E074)
    val mintSoft get() = pick(0x1F02C265, 0x2600E074)
    /** 밝은 배경 위 글자용 그린 (명암비 4.5:1 확보를 위해 토큰보다 어둡게) */
    val mintText get() = pick(0xFF007A3D, 0xFF00E074)
    val amber get() = pick(0xFFFFC224, 0xFFFFC224)
    val amberSoft get() = pick(0x33FFC224, 0x33FFC224)
    val amberText get() = pick(0xFF8F5A00, 0xFFFFC224)
    /** 채워진 원(완료/달성) 위의 글자색 = static_dark_100 */
    val onFill get() = 0xFF131417.toInt()
}

/**
 * Pleos Connect 타이포그래피 스케일 (docs/font-guide.md, Asta Sans = 시스템 sans-serif)
 * 160dpi라 sp = px. Strong/Extra Bold/Bold → bold, Normal/Regular → 일반.
 */
class TextStyle(val size: Float, val lineHeight: Float)

object Type {
    val HeadlineL = TextStyle(60f, 80f); val HeadlineM = TextStyle(56f, 72f); val HeadlineS = TextStyle(48f, 64f)
    val TitleL = TextStyle(40f, 52f); val TitleM = TextStyle(36f, 44f); val TitleS = TextStyle(32f, 40f)
    val BodyL = TextStyle(30f, 38f); val BodyM = TextStyle(28f, 36f); val BodyS = TextStyle(26f, 34f)
    val LabelM = TextStyle(24f, 28f); val LabelS = TextStyle(20f, 24f)
}

fun TextView.applyType(t: TextStyle) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, t.size)
    lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, t.lineHeight, resources.displayMetrics).toInt()
    // 한글을 어절(phrase) 단위로 줄바꿈 — "안내됩니/다." 같은 한 글자 꺾임 방지
    breakStrategy = LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
    lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
}

/** 앱 window 폭이 넓으면(landscape/확장) 2단 구성, 좁으면(portrait) 세로 스택 */
fun Context.isWide(): Boolean = resources.configuration.screenWidthDp >= 1000

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

/** 둥근 사각형 배경 */
fun Context.rounded(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Float = 1.5f): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(strokeWidth), stroke)
    }

/** 원형 배경 */
fun Context.circle(fill: Int, stroke: Int = 0, strokeWidth: Float = 2.5f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (stroke != 0) setStroke(dp(strokeWidth), stroke)
    }

fun Context.uiText(text: String, style: TextStyle, color: Int = Palette.text, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        applyType(style)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

/** Pleos 버튼 높이(디자인 가이드 Basic Button: Large 100 / Medium 80 / Small 64) */
object ButtonH { const val Large = 100f; const val Medium = 80f; const val Small = 64f }

/** 48 그리드 벡터 아이콘 (Outlined). 색은 tint 로 지정 */
fun Context.icon(res: Int, sizeDp: Float, tint: Int): ImageView = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(tint)
    layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** 원형/둥근 배경 + 가운데 콘텐츠 */
fun Context.badge(sizeDp: Float, bg: Drawable, content: View): FrameLayout = FrameLayout(this).apply {
    background = bg
    addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** 작은 알약형 라벨 (상태 표시 등). iconRes 를 주면 앞에 아이콘이 붙는다 */
fun Context.pill(label: String, fg: Int, bg: Int, iconRes: Int = 0): TextView = TextView(this).apply {
    text = label
    applyType(Type.LabelS)
    setTextColor(fg)
    gravity = Gravity.CENTER
    setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
    background = rounded(bg, 20f)
    if (iconRes != 0) {
        val d = androidx.core.content.ContextCompat.getDrawable(context, iconRes)!!.mutate()
        d.setTint(fg)
        d.setBounds(0, 0, dp(18f), dp(18f))
        setCompoundDrawables(d, null, null, null)
        compoundDrawablePadding = dp(6f)
    }
}

/** 상단바 (뒤로가기가 있으면 왼쪽에 둥근 버튼) */
fun Context.topBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null): LinearLayout {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24f), dp(18f), dp(24f), dp(10f))
        setBackgroundColor(Palette.bg)
    }
    if (onBack != null) {
        bar.addView(
            badge(68f, circle(Palette.surfaceHi), icon(R.drawable.ic_chevron_left, 32f, Palette.text)).apply {
                contentDescription = "뒤로"
                setOnClickListener { onBack() }
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(16f)
            }
        )
    }
    val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    texts.addView(uiText(title, Type.TitleM, Palette.text, bold = true))
    subtitle?.let {
        texts.addView(uiText(it, Type.LabelM, Palette.textSub).apply { setPadding(0, dp(2f), 0, 0) })
    }
    bar.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    return bar
}

fun Context.primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    applyType(Type.BodyL)
    isAllCaps = false
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(Palette.btnText)
    stateListAnimator = null
    background = rounded(Palette.btnFill, 28f)
    setOnClickListener { onClick() }
}

fun Context.secondaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    applyType(Type.BodyM)
    isAllCaps = false
    setTextColor(Palette.text)
    stateListAnimator = null
    background = rounded(Palette.surfaceHi, 24f)
    setOnClickListener { onClick() }
}

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(24f), dp(22f), dp(24f), dp(22f))
    background = rounded(Palette.surface, 28f)
}

/** 카드/행용 레이아웃 파라미터: 좌우 여백 + 상단 간격 (폭은 꽉 채움) */
fun Context.insetParams(side: Float = 24f, top: Float = 12f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        leftMargin = dp(side); rightMargin = dp(side); topMargin = dp(top)
    }

/** 얇은 둥근 진행 바 */
class SlimProgress(context: Context) : View(context) {
    var max = 1
    var progress = 0
    var fill = Palette.blue
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.surfaceHi }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, track)
        val ratio = progress.toFloat() / max.coerceAtLeast(1)
        if (ratio > 0f) {
            bar.color = fill
            val w = (width * ratio).coerceAtLeast(height.toFloat())
            c.drawRoundRect(RectF(0f, 0f, w, height.toFloat()), r, r, bar)
        }
    }
}

/** 원형 진행 링 — 가운데에 큰 숫자 + 작은 보조 텍스트 */
class RingProgress(context: Context) : View(context) {
    var max = 1
    var progress = 0
    var fill = Palette.mint
    var center = ""
    var sub = ""
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.surfaceHi; style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Palette.text
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Palette.textSub }

    override fun onDraw(c: Canvas) {
        val d = minOf(width, height).toFloat()
        val sw = d * 0.085f
        track.strokeWidth = sw; arc.strokeWidth = sw
        val box = RectF(sw / 2 + (width - d) / 2, sw / 2 + (height - d) / 2,
            width - sw / 2 - (width - d) / 2, height - sw / 2 - (height - d) / 2)
        c.drawArc(box, 0f, 360f, false, track)
        val ratio = progress.toFloat() / max.coerceAtLeast(1)
        if (ratio > 0f) { arc.color = fill; c.drawArc(box, -90f, 360f * ratio, false, arc) }
        big.textSize = d * 0.34f
        small.textSize = d * 0.095f
        val cx = width / 2f; val cy = height / 2f
        c.drawText(center, cx, cy + d * 0.07f, big)
        c.drawText(sub, cx, cy + d * 0.07f + d * 0.17f, small)
    }
}

/**
 * Pleos 시스템 팝업 규격: 아이콘 → 제목 → 설명 → 버튼(가로, 확인=어두운 채움 / 취소=연회색)
 * 폭은 Large 1200 / Medium 760.
 */
fun Context.showPopup(
    iconRes: Int, iconBg: Int, iconTint: Int,
    title: String, message: String,
    confirmLabel: String, onConfirm: () -> Unit,
    cancelLabel: String,
    header: View? = null,
) {
    val dlg = androidx.appcompat.app.AlertDialog.Builder(this).create()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(36f), dp(40f), dp(36f), dp(36f))
    }
    box.addView(header ?: badge(88f, circle(iconBg), icon(iconRes, 48f, iconTint)))
    box.addView(uiText(title, Type.TitleM, Palette.text, bold = true).apply {
        gravity = Gravity.CENTER; setPadding(0, dp(20f), 0, 0)
    })
    box.addView(uiText(message, Type.BodyM, Palette.textSub).apply {
        gravity = Gravity.CENTER; setPadding(0, dp(12f), 0, dp(32f))
    })
    val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    buttons.addView(primaryButton(confirmLabel) { dlg.dismiss(); onConfirm() },
        LinearLayout.LayoutParams(0, dp(ButtonH.Large), 1f).apply { marginEnd = dp(12f) })
    buttons.addView(secondaryButton(cancelLabel) { dlg.dismiss() },
        LinearLayout.LayoutParams(0, dp(ButtonH.Large), 1f))
    box.addView(buttons, LinearLayout.LayoutParams(-1, -2))

    dlg.setView(box)
    dlg.show()
    dlg.window?.apply {
        setBackgroundDrawable(rounded(Palette.surface, 40f))
        val popupW = if (isWide()) 1200f else 760f
        setLayout(minOf(dp(popupW), (resources.displayMetrics.widthPixels * 0.9f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
