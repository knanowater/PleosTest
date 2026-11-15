package com.ryuseilabs.pleostest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.min

/**
 * 로드북(스탬프 투어) 장식 — 표지, 노트 페이지, 펼친 책, 잉크 도장.
 * 글자/버튼/명암비는 Pleos 규격(Type, ButtonH, Palette)을 그대로 쓰고, 책의 질감만 여기서 만든다.
 */

object BookStyle {
    /** 표지 색. 책 표지 이미지 성격이라 테마(라이트/다크)와 무관하게 고정한다 */
    class Cover(val color: Int, val spine: Int, val ribbon: Int)

    private val covers = listOf(
        Cover(0xFF1F5F7A.toInt(), 0xFF154759.toInt(), 0xFFF62E24.toInt()), // 바다
        Cover(0xFF3F6B55.toInt(), 0xFF2D4F3E.toInt(), 0xFFFFC224.toInt()), // 숲
        Cover(0xFF5C4B8A.toInt(), 0xFF43366A.toInt(), 0xFFFFC224.toInt()),
        Cover(0xFF8A4F3E.toInt(), 0xFF6B3A2D.toInt(), 0xFFFFC224.toInt()),
    )

    fun cover(bookId: String): Cover = when (bookId) {
        "uljin-coast" -> covers[0]
        "pangyo-valley" -> covers[1]
        else -> covers[(bookId.hashCode() and 0x7fffffff) % covers.size]
    }

    /** 표지 위 글자색 (표지가 모두 어두운 색이라 흰색 계열) */
    const val onCover = 0xFFFFFFFF.toInt()
    const val onCoverSub = 0xCCFFFFFF.toInt()
}

private abstract class BaseDrawable : Drawable() {
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 책 표지: 책등 + 박음질 선 + 고무밴드 + 안쪽 테두리. 왼쪽이 책등. */
class BookCoverDrawable(private val ctx: Context, private val cover: BookStyle.Cover) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stitch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x66FFFFFF }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF }

    override fun draw(c: Canvas) {
        val b = RectF(bounds)
        val rl = ctx.dp(8f).toFloat(); val rr = ctx.dp(26f).toFloat()
        val path = Path().apply {
            addRoundRect(b, floatArrayOf(rl, rl, rr, rr, rr, rr, rl, rl), Path.Direction.CW)
        }
        fill.color = cover.color; spine.color = cover.spine; band.color = 0x40000000
        c.save(); c.clipPath(path)
        c.drawPath(path, fill)
        val sw = b.width() * 0.085f
        c.drawRect(b.left, b.top, b.left + sw, b.bottom, spine)
        c.drawRect(b.left + sw, b.top, b.left + sw + ctx.dp(2f), b.bottom, band) // 책등 홈
        val bandX = b.right - b.width() * 0.09f
        c.drawRect(bandX, b.top, bandX + ctx.dp(16f), b.bottom, band)            // 고무밴드
        c.restore()
        stitch.strokeWidth = ctx.dp(2f).toFloat()
        stitch.pathEffect = DashPathEffect(floatArrayOf(ctx.dp(8f).toFloat(), ctx.dp(8f).toFloat()), 0f)
        val sx = b.left + sw + ctx.dp(14f)
        c.drawLine(sx, b.top + ctx.dp(14f), sx, b.bottom - ctx.dp(14f), stitch)
        frame.strokeWidth = ctx.dp(2f).toFloat()
        val fr = RectF(sx + ctx.dp(12f), b.top + ctx.dp(18f), bandX - ctx.dp(14f), b.bottom - ctx.dp(18f))
        c.drawRoundRect(fr, ctx.dp(10f).toFloat(), ctx.dp(10f).toFloat(), frame)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 노트 페이지: 줄 + (선택) 아래/오른쪽으로 비껴 쌓인 종이 두께 */
class PaperDrawable(
    private val ctx: Context,
    private val ruled: Boolean = true,
    private val stack: Boolean = true,
    private val radius: Float = 26f,
) : Drawable() {
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** 쌓인 종이가 차지하는 두께(dp). 콘텐츠 padding 계산용 */
    val thickness: Float get() = if (stack) 8f else 0f

    override fun draw(c: Canvas) {
        paper.color = Palette.paper; edge.color = Palette.paper
        edgeLine.color = Palette.paperEdge; edgeLine.strokeWidth = ctx.dp(1.5f).toFloat()
        rule.color = Palette.paperDot
        val b = RectF(bounds)
        val t = ctx.dp(thickness).toFloat()
        val r = ctx.dp(radius).toFloat()
        if (stack) {
            val l2 = RectF(b.left + t, b.top + t, b.right, b.bottom)
            val l1 = RectF(b.left + t / 2, b.top + t / 2, b.right - t / 2, b.bottom - t / 2)
            for (rect in listOf(l2, l1)) {
                c.drawRoundRect(rect, r, r, edge); c.drawRoundRect(rect, r, r, edgeLine)
            }
        }
        val page = RectF(b.left, b.top, b.right - t, b.bottom - t)
        c.drawRoundRect(page, r, r, paper)
        c.drawRoundRect(page, r, r, edgeLine)
        if (ruled) { // 점 격자 노트: 글자와 겹쳐도 거슬리지 않는다
            rule.style = Paint.Style.FILL
            val gap = ctx.dp(30f).toFloat()
            var y = page.top + gap
            while (y < page.bottom - gap / 2) {
                var x = page.left + gap
                while (x < page.right - gap / 2) { c.drawCircle(x, y, ctx.dp(1.6f).toFloat(), rule); x += gap }
                y += gap
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 펼친 책: 표지 보드 + 양쪽 페이지 + 가운데 접힌 홈 + 책갈피 리본 + 아래쪽 종이 두께 */
class SpreadDrawable(private val ctx: Context, private val cover: BookStyle.Cover) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gutter = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbon = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 콘텐츠를 얹을 안쪽 여백(dp): 표지 테두리 + 페이지 안쪽 여백 */
    companion object { const val PAD = 44f }

    override fun draw(c: Canvas) {
        val b = RectF(bounds)
        val big = ctx.dp(30f).toFloat()
        fill.color = cover.color
        c.drawRoundRect(b, big, big, fill)
        line.strokeWidth = ctx.dp(2f).toFloat(); line.color = 0x22000000
        c.drawRoundRect(RectF(b).apply { inset(ctx.dp(3f).toFloat(), ctx.dp(3f).toFloat()) }, big, big, line)

        val inset = ctx.dp(16f).toFloat()
        val pages = RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset - ctx.dp(8f))
        val mid = pages.centerX()
        val gap = ctx.dp(2f).toFloat()
        // 아래쪽 종이 두께
        line.color = Palette.paperEdge; line.strokeWidth = ctx.dp(2f).toFloat()
        for (i in 1..3) {
            val y = pages.bottom + ctx.dp(2.5f) * i
            c.drawLine(pages.left + ctx.dp(8f) * i, y, pages.right - ctx.dp(8f) * i, y, line)
        }
        val pr = ctx.dp(20f).toFloat(); val pi = ctx.dp(4f).toFloat()
        val left = Path().apply {
            addRoundRect(RectF(pages.left, pages.top, mid - gap, pages.bottom),
                floatArrayOf(pr, pr, pi, pi, pi, pi, pr, pr), Path.Direction.CW)
        }
        val right = Path().apply {
            addRoundRect(RectF(mid + gap, pages.top, pages.right, pages.bottom),
                floatArrayOf(pi, pi, pr, pr, pr, pr, pi, pi), Path.Direction.CW)
        }
        paper.color = Palette.paper
        c.drawPath(left, paper); c.drawPath(right, paper)
        // 가운데 접힌 홈(그라데이션)
        val gw = ctx.dp(80f).toFloat()
        c.save(); c.clipPath(left)
        gutter.shader = LinearGradient(mid - gw, 0f, mid, 0f, 0x00000000, 0x2B000000, Shader.TileMode.CLAMP)
        c.drawRect(mid - gw, pages.top, mid, pages.bottom, gutter)
        c.restore()
        c.save(); c.clipPath(right)
        gutter.shader = LinearGradient(mid, 0f, mid + gw, 0f, 0x2B000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(mid, pages.top, mid + gw, pages.bottom, gutter)
        c.restore()
        gutter.shader = null
        // 책갈피 리본 (표지 위에서 늘어져 오른쪽 페이지 위로)
        val rw = ctx.dp(22f).toFloat()
        val rx = mid - ctx.dp(30f)                 // 지도가 닿지 않는 가운데 여백에 걸린다
        val rb = pages.top + ctx.dp(96f)
        ribbon.color = cover.ribbon
        val rp = Path().apply {
            moveTo(rx, b.top + ctx.dp(2f)); lineTo(rx + rw, b.top + ctx.dp(2f))
            lineTo(rx + rw, rb); lineTo(rx + rw / 2, rb - ctx.dp(16f)); lineTo(rx, rb); close()
        }
        c.drawPath(rp, ribbon)
        ribbon.color = 0x22000000
        c.drawRect(rx + rw * 0.62f, b.top + ctx.dp(2f), rx + rw, rb - ctx.dp(6f), ribbon)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * 잉크 도장. done=true 이면 이중 링(닳은 틈) + 체크, false 이면 번호가 적힌 빈 도장 칸(점선).
 * 큰 도장은 topText/bottomText 를 링을 따라 둥글게 찍는다.
 */
class SealView(context: Context) : View(context) {
    var done: Boolean = true
        set(v) { field = v; invalidate() }
    var number: Int = 0
        set(v) { field = v; invalidate() }
    var ink: Int = Palette.mint
        set(v) { field = v; invalidate() }
    var tilt: Float = -8f
        set(v) { field = v; invalidate() }
    /** 빈 칸(done=false)의 점선/번호 색. 현재 진행 칸은 파란색으로 */
    var emptyColor: Int = Palette.textDim
        set(v) { field = v; invalidate() }
    var topText: String? = null
        set(v) { field = v; invalidate() }
    var bottomText: String? = null
        set(v) { field = v; invalidate() }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillDisc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - context.dp(2f)
        if (!done) {
            empty.color = emptyColor; empty.strokeWidth = r * 0.07f
            empty.pathEffect = DashPathEffect(floatArrayOf(r * 0.22f, r * 0.16f), 0f)
            c.drawCircle(cx, cy, r * 0.88f, empty)
            text.color = emptyColor; text.textSize = r * 0.78f
            val fm = text.fontMetrics
            c.drawText(number.toString(), cx, cy - (fm.ascent + fm.descent) / 2f, text)
            return
        }
        c.save(); c.rotate(tilt, cx, cy)
        fillDisc.color = ink; fillDisc.alpha = 0x1F
        c.drawCircle(cx, cy, r * 0.90f, fillDisc)               // 옅은 잉크 면
        ring.color = ink; ring.strokeWidth = r * 0.13f
        val oval = RectF(cx - r * 0.90f, cy - r * 0.90f, cx + r * 0.90f, cy + r * 0.90f)
        c.drawArc(oval, 8f, 150f, false, ring)      // 잉크가 닳은 틈 3곳
        c.drawArc(oval, 172f, 118f, false, ring)
        c.drawArc(oval, 300f, 56f, false, ring)
        val big = topText != null || bottomText != null
        thin.color = ink; thin.strokeWidth = r * 0.05f
        c.drawCircle(cx, cy, r * (if (big) 0.80f else 0.70f), thin)
        text.color = ink
        if (big) {
            text.textSize = r * 0.20f; text.letterSpacing = 0.06f
            topText?.let {
                val p = Path().apply { addArc(RectF(cx - r * 0.50f, cy - r * 0.50f, cx + r * 0.50f, cy + r * 0.50f), 180f, 180f) }
                c.drawTextOnPath(it, p, 0f, 0f, text)
            }
            bottomText?.let {
                val rb = r * 0.50f + text.textSize * 0.72f
                val p = Path().apply { addArc(RectF(cx - rb, cy - rb, cx + rb, cy + rb), 180f, -180f) }
                c.drawTextOnPath(it, p, 0f, 0f, text)
            }
            text.letterSpacing = 0f
        }
        val k = if (big) r * 0.22f else r * 0.36f
        check.color = ink; check.strokeWidth = if (big) r * 0.11f else r * 0.15f
        val cp = Path().apply {
            moveTo(cx - k, cy + k * 0.05f); lineTo(cx - k * 0.25f, cy + k * 0.75f); lineTo(cx + k, cy - k * 0.65f)
        }
        c.drawPath(cp, check)
        c.restore()
    }
}

/** 도장 칸 한 줄 (done 개수만큼 잉크 도장, 나머지는 빈 칸) */
fun Context.stampSlots(done: List<Boolean>, sizeDp: Float, ink: Int = Palette.mint): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        done.forEachIndexed { i, d ->
            addView(SealView(context).apply {
                this.done = d; number = i + 1; this.ink = ink
                tilt = if (i % 2 == 0) -8f else 6f
            }, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(10f) })
        }
    }
