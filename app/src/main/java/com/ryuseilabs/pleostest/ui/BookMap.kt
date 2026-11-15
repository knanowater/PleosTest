package com.ryuseilabs.pleostest.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.ryuseilabs.pleostest.course.BookCourse

/**
 * 코스 다이어그램 — 외부 지도 데이터 없이 출발점 + 도장 포인트를 그린다.
 * 다크 배경에 은은한 점 격자를 깔고, 완주 구간은 민트 실선, 미완주 구간은 회색 파선으로 표시한다.
 * 다음 목표 지점에는 블루 후광을 둔다. 4점 코스는 ㄹ자(90도 각진 지그재그) 배치:
 *
 *  출발 ──── 1
 *              │
 *  3 ──────── 2
 *  │
 *  4
 *
 * 모든 크기는 화면 폭 기준으로 자동 스케일.
 */
class CourseDiagramView(
    context: Context,
    private val course: BookCourse,
    private val stampedIds: Set<String>,
) : View(context) {

    private val gridDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.paperDot; style = Paint.Style.FILL }
    private val doneGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.mintSoft; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val doneStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.mint; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val guideStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.textDim; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val startDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.text; style = Paint.Style.FILL }
    private val startRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.text; style = Paint.Style.STROKE }
    private val nodeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.surfaceHi; style = Paint.Style.FILL }
    private val nodeRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.textDim; style = Paint.Style.STROKE }
    private val nextRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.blue; style = Paint.Style.STROKE }
    private val nextHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.blueSoft; style = Paint.Style.FILL }
    private val doneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.mint; style = Paint.Style.FILL }
    private val dotText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.text; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val startLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.textSub; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.paper; style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        val stamps = course.stamps
        val w = width.toFloat()
        val h = height.toFloat()

        // 점 격자 배경
        val gap = w * 0.06f
        var gy = gap / 2
        while (gy < h) {
            var gx = gap / 2
            while (gx < w) { c.drawCircle(gx, gy, w * 0.0026f, gridDot); gx += gap }
            gy += gap
        }

        if (stamps.isEmpty()) {
            labelPaint.textSize = w * 0.024f
            c.drawText("지정된 코스가 없습니다.", w / 2, h / 2, labelPaint)
            return
        }

        val dotR = w * 0.025f
        val labelSize = w * 0.036f
        val startSize = w * 0.030f
        doneStroke.strokeWidth = w * 0.009f
        doneGlow.strokeWidth = w * 0.026f
        guideStroke.strokeWidth = w * 0.0055f
        guideStroke.pathEffect = DashPathEffect(floatArrayOf(w * 0.018f, w * 0.016f), 0f)
        nodeRing.strokeWidth = w * 0.005f
        nextRing.strokeWidth = w * 0.007f
        startRing.strokeWidth = w * 0.005f

        val (start, pts) = layout(stamps.size, w, h)
        val nextIdx = stamps.indexOfFirst { it.id !in stampedIds }

        // 출발→1, 1→2→3→4 구간선
        var prev = start
        stamps.forEachIndexed { i, s ->
            val p = pts[i]
            val done = s.id in stampedIds
            if (done) c.drawLine(prev.first, prev.second, p.first, p.second, doneGlow)
            c.drawLine(prev.first, prev.second, p.first, p.second, if (done) doneStroke else guideStroke)
            prev = p
        }

        // 출발점
        c.drawCircle(start.first, start.second, dotR * 0.5f, startDot)
        c.drawCircle(start.first, start.second, dotR * 0.95f, startRing)
        drawLabel(c, "출발 · 현재 위치", start.first, start.second + dotR + labelSize * 1.2f, startLabelPaint, startSize, w, h)

        // 도장 포인트
        stamps.forEachIndexed { idx, s ->
            val (x, y) = pts[idx]
            val done = s.id in stampedIds
            val next = idx == nextIdx
            if (next) c.drawCircle(x, y, dotR * 1.75f, nextHalo)
            when {
                done -> c.drawCircle(x, y, dotR, doneFill)
                next -> { c.drawCircle(x, y, dotR, nodeFill); c.drawCircle(x, y, dotR, nextRing) }
                else -> { c.drawCircle(x, y, dotR, nodeFill); c.drawCircle(x, y, dotR, nodeRing) }
            }
            dotText.color = if (done) Palette.onFill else if (next) Palette.blue else Palette.textSub
            dotText.textSize = dotR * 1.1f
            val fm = dotText.fontMetrics
            if (done) {
                checkPaint.color = Palette.onFill; checkPaint.strokeWidth = dotR * 0.24f
                val k = dotR * 0.42f
                val path = Path().apply { moveTo(x - k, y + k * 0.05f); lineTo(x - k * 0.25f, y + k * 0.75f); lineTo(x + k, y - k * 0.65f) }
                c.drawPath(path, checkPaint)
            } else {
                c.drawText((idx + 1).toString(), x, y - (fm.ascent + fm.descent) / 2f, dotText)
            }
            drawLabel(c, s.label, x, y + dotR + labelSize * 1.2f, labelPaint, labelSize, w, h)
        }
    }

    /** 라벨을 점 아래 중앙에 그리고, 종이색 라운드 배경으로 선/겹침을 가린다 */
    private fun drawLabel(c: Canvas, text: String, cx: Float, baseline: Float, paint: Paint, size: Float, w: Float, h: Float) {
        paint.textSize = size
        val tw = paint.measureText(text)
        val fm = paint.fontMetrics
        val padH = size * 0.35f
        val padV = size * 0.18f
        // 가장자리에 걸리면 라벨 박스를 잘라내지 않고 화면 안으로 통째로 밀어 넣는다.
        // (박스를 자르면 글자 중심의 허용 범위가 뒤집혀 coerceIn 이 예외를 던진다)
        val half = tw / 2 + padH
        val mid = if (w - 4f >= half * 2) cx.coerceIn(2f + half, w - 2f - half) else w / 2
        val top = (baseline + fm.ascent - padV).coerceAtLeast(2f)
        val bottom = (baseline + fm.descent + padV).coerceAtMost(h - 2f)
        val radius = size * 0.5f
        c.drawRoundRect(RectF(mid - half, top, mid + half, bottom), radius, radius, labelBg)
        c.drawText(text, mid, baseline.coerceAtMost(bottom - fm.descent), paint)
    }

    /** 4점 코스는 출발+4점 = ㄹ자 배치, 그 외는 실제 좌표 정규화 배치 */
    private fun layout(n: Int, w: Float, h: Float): Pair<Pair<Float, Float>, List<Pair<Float, Float>>> {
        if (n == 4) {
            val l = w * 0.18f
            val r = w * 0.82f
            val top = h * 0.19f
            val mid = h * 0.50f
            val bot = h * 0.81f
            val start = l to top
            // 출발(좌상) → 1(우상) → 2(우중) → 3(좌중) → 4(좌하)
            val pts = listOf(r to top, r to mid, l to mid, l to bot)
            return start to pts
        }

        val stamps = course.stamps
        val startPoint = course.chapters.firstOrNull()?.start
        val lats = stamps.map { it.lat } + listOfNotNull(startPoint?.lat)
        val lngs = stamps.map { it.lng } + listOfNotNull(startPoint?.lng)
        val minLat = lats.min(); val maxLat = lats.max()
        val minLng = lngs.min(); val maxLng = lngs.max()
        val pad = 80f
        fun x(lng: Double) = pad + ((lng - minLng) / (maxLng - minLng).coerceAtLeast(1e-9) * (w - 2 * pad)).toFloat()
        fun y(lat: Double) = h - pad - ((lat - minLat) / (maxLat - minLat).coerceAtLeast(1e-9) * (h - 2 * pad)).toFloat()
        val start = startPoint?.let { x(it.lng) to y(it.lat) } ?: (w * 0.15f to h * 0.15f)
        return start to stamps.map { x(it.lng) to y(it.lat) }
    }
}
