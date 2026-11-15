package com.ryuseilabs.pleostest

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ryuseilabs.pleostest.course.BookCourse
import com.ryuseilabs.pleostest.course.Books
import com.ryuseilabs.pleostest.course.BookStore
import com.ryuseilabs.pleostest.ui.CourseDiagramView
import com.ryuseilabs.pleostest.R
import com.ryuseilabs.pleostest.ui.ButtonH
import com.ryuseilabs.pleostest.ui.BookStyle
import com.ryuseilabs.pleostest.ui.PaperDrawable
import com.ryuseilabs.pleostest.ui.SealView
import com.ryuseilabs.pleostest.ui.SpreadDrawable
import com.ryuseilabs.pleostest.ui.badge
import com.ryuseilabs.pleostest.ui.icon
import com.ryuseilabs.pleostest.ui.Palette
import com.ryuseilabs.pleostest.ui.Type
import com.ryuseilabs.pleostest.ui.applyType
import com.ryuseilabs.pleostest.ui.circle
import com.ryuseilabs.pleostest.ui.isWide
import com.ryuseilabs.pleostest.ui.pill
import com.ryuseilabs.pleostest.ui.showPopup
import com.ryuseilabs.pleostest.ui.rounded
import com.ryuseilabs.pleostest.ui.card
import com.ryuseilabs.pleostest.ui.dp
import com.ryuseilabs.pleostest.ui.insetParams
import com.ryuseilabs.pleostest.ui.primaryButton
import com.ryuseilabs.pleostest.ui.topBar
import com.ryuseilabs.pleostest.ui.uiText

/**
 * 북 상세 — 상단바(뒤로가기) + 코스 다이어그램 + 도장 페이지 + 코스 상세.
 * 다이어그램/도장은 스크롤 영역, 출발 버튼은 하단 고정.
 */
class BookActivity : AppCompatActivity() {

    private val book by lazy { Books.byId(intent.getStringExtra("bookId")) }
    private lateinit var store: BookStore
    private var selectedId: String? = null

    private lateinit var mapContainer: FrameLayout
    private lateinit var stampsRow: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private lateinit var startButton: Button
    private lateinit var masterView: TextView

    private val secondary get() = Palette.textSub

    private val cover by lazy { BookStyle.cover(book.id) }
    private lateinit var tabsBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        store = BookStore(getSharedPreferences("stampbook", MODE_PRIVATE))
        selectedId = intent.getStringExtra("courseId")
            ?: book.availableCourses.firstOrNull()?.id

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
        }

        // 상단바 (뒤로가기)
        root.addView(topBar(book.name, book.tagline) { finish() })

        // 왼쪽 페이지: 코스 지도 (투명 — 페이지 종이 위에 그대로 얹힌다)
        mapContainer = FrameLayout(this)

        // 코스 탭 (코스가 여럿인 북만)
        tabsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // 도장 목록 (세로 행)
        stampsRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 오른쪽 페이지: 코스 제목 + 부제 + 도장 목록
        val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = uiText("", Type.TitleS, Palette.text, bold = true)
        subView = uiText("", Type.LabelM, secondary)
        detail.addView(titleView)
        detail.addView(subView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f); bottomMargin = dp(8f) })
        detail.addView(stampsRow)

        // 출발 버튼 영역
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        startButton = primaryButton("드라이브 출발") { selected()?.let { onStartClicked(it) } }
        bottom.addView(startButton, LinearLayout.LayoutParams(-1, dp(ButtonH.Large)))
        masterView = uiText("", Type.LabelS, secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
        }
        bottom.addView(masterView)

        if (isWide()) {
            // 펼친 책: 표지 보드 위에 두 쪽. 가운데 홈 + 책갈피 리본
            val pad = dp(SpreadDrawable.PAD)
            val gutter = dp(24f)
            val spread = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = SpreadDrawable(this@BookActivity, cover)
                setPadding(pad, pad, pad, pad)
            }
            spread.addView(mapContainer, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = gutter })
            val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            right.addView(tabsBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16f) })
            right.addView(ScrollView(this).apply { isFillViewport = false; addView(detail) }, LinearLayout.LayoutParams(-1, 0, 1f))
            right.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
            spread.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = gutter })
            root.addView(spread, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                leftMargin = dp(24f); rightMargin = dp(24f); topMargin = dp(6f); bottomMargin = dp(18f)
            })
        } else {
            // 좁은 화면: 지도 종이 + 상세 종이를 세로로
            val scroll = ScrollView(this).apply { isFillViewport = true }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            mapContainer.background = PaperDrawable(this, ruled = true, stack = false)
            detail.background = PaperDrawable(this, ruled = false, stack = false)
            detail.setPadding(dp(22f), dp(20f), dp(22f), dp(20f))
            inner.addView(tabsBox, insetParams(top = 6f))
            inner.addView(mapContainer, insetParams(top = 12f).apply { height = dp(520f) })
            inner.addView(detail, insetParams(top = 14f))
            inner.setPadding(0, 0, 0, dp(16f))
            scroll.addView(inner)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            bottom.setPadding(dp(24f), dp(12f), dp(24f), dp(16f))
            bottom.setBackgroundColor(Palette.surface)
            root.addView(bottom)
        }

        setContentView(root)
        render()
    }

    private fun render() {
        renderTabs()
        renderDiagram()
        renderStamps()
        renderDetail()
    }

    /** 코스 탭 — 코스가 둘 이상인 북만 보인다. 준비 중 코스는 흐리게 */
    private fun renderTabs() {
        tabsBox.removeAllViews()
        if (book.courses.size <= 1) { tabsBox.visibility = View.GONE; return }
        tabsBox.visibility = View.VISIBLE
        tabsBox.background = rounded(Palette.surfaceHi, 26f)
        tabsBox.setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        book.courses.forEachIndexed { i, c ->
            val sel = c.id == selectedId
            tabsBox.addView(uiText(
                "코스 ${i + 1}", Type.BodyS,
                if (sel) Palette.text else if (c.available) Palette.textSub else Palette.textDim,
                bold = sel,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
                background = if (sel) rounded(Palette.selected, 20f) else null
                setOnClickListener {
                    if (c.available) { selectedId = c.id; render() }
                    else Toast.makeText(this@BookActivity, "지정 코스를 준비하고 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun renderDiagram() {
        mapContainer.removeAllViews()
        val c = selected()
        if (c == null || !c.available) {
            mapContainer.addView(uiText("지정 코스를 준비하고 있습니다.", Type.BodyM, secondary).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
            return
        }
        val doneIds = c.stamps.filter { store.isStampDone(book.id, c.id, it.id) }.map { it.id }.toSet()
        mapContainer.addView(CourseDiagramView(this, c, doneIds), FrameLayout.LayoutParams(-1, -1))
        val legend = uiText("파선: 미완주 · 실선: 완주(증빙)", Type.LabelS, Palette.textSub).apply {
            setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
            background = rounded(Palette.paper, 12f, Palette.paperEdge, 1.5f)
        }
        mapContainer.addView(legend, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = dp(4f); bottomMargin = dp(4f)
        })
    }

    private fun renderStamps() {
        stampsRow.removeAllViews()
        val c = selected() ?: return
        c.stamps.forEachIndexed { i, s -> stampsRow.addView(stampSlot(i + 1, s.label, c, s.id)) }
        val allDone = c.available && c.stamps.isNotEmpty() && c.stamps.all { store.isStampDone(book.id, c.id, it.id) }
        val finale = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = if (allDone) rounded(Palette.amberSoft, 18f, Palette.amber, 1.5f) else rounded(Palette.paperLine, 18f)
        }
        finale.addView(badge(56f,
            if (allDone) circle(Palette.amber) else circle(Palette.paper, Palette.paperEdge, 2f),
            icon(R.drawable.ic_crown, 30f, if (allDone) Palette.onFill else Palette.textDim)))
        finale.addView(uiText("COMPLETE", Type.BodyS, if (allDone) Palette.amberText else Palette.textDim, bold = true).apply {
            setPadding(dp(16f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        finale.addView(if (allDone) pill("달성", Palette.amberText, Palette.amberSoft) else pill("전 구간 완주 시", Palette.textSub, Palette.paper))
        stampsRow.addView(finale, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })
    }

    /** 도장 페이지 한 줄: 잉크 도장(또는 빈 칸) + 장소 + 증빙 상태, 아래에 얇은 구분선 */
    private fun stampSlot(index: Int, label: String, course: BookCourse, stampId: String): View {
        val done = course.available && store.isStampDone(book.id, course.id, stampId)
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(10f), dp(4f), dp(10f))
        }
        row.addView(SealView(this).apply {
            this.done = done; number = index; ink = Palette.mint
            tilt = if (index % 2 == 1) -8f else 7f
        }, LinearLayout.LayoutParams(dp(72f), dp(72f)))
        row.addView(uiText(label, Type.BodyM, if (done) Palette.text else Palette.textSub, bold = done).apply {
            setPadding(dp(20f), 0, dp(8f), 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(if (done) pill("증빙", Palette.mintText, Palette.mintSoft) else pill("대기", Palette.textDim, Palette.paperLine))
        wrap.addView(row)
        wrap.addView(View(this).apply { setBackgroundColor(Palette.paperEdge) }, LinearLayout.LayoutParams(-1, dp(1.5f)))
        return wrap
    }

    private fun renderDetail() {
        val c = selected() ?: return
        titleView.text = c.title
        val doneCount = c.stamps.count { store.isStampDone(book.id, c.id, it.id) }
        subView.text = when {
            !c.available -> "준비 중"
            doneCount == c.stamps.size -> "완주 — 증빙 완료"
            else -> c.subtitle
        }
        startButton.isEnabled = c.available
        startButton.alpha = if (c.available) 1f else 0.4f
        startButton.text = if (doneCount == c.stamps.size && c.available) "다시 도전하기" else "드라이브 출발"
        masterView.text = if (c.available)
            "${c.stamps.size}도장 중 ${doneCount}개 증빙 완료 — 전 구간 완주 시 COMPLETE 도장"
        else ""
    }

    private fun selected(): BookCourse? = book.courses.firstOrNull { it.id == selectedId }

    /** 완주한 코스의 "다시 도전하기"는 도장을 초기화해야 하므로 확인을 먼저 받는다 */
    private fun onStartClicked(course: BookCourse) {
        val completed = course.available && course.stamps.isNotEmpty() &&
            course.stamps.all { store.isStampDone(book.id, course.id, it.id) }
        if (!completed) { startDrive(course); return }
        showPopup(
            iconRes = R.drawable.ic_refresh, iconBg = Palette.surfaceHi, iconTint = Palette.text,
            title = "처음부터 다시 도전할까요?",
            message = "'${course.title}'의 도장 ${course.stamps.size}개가 모두 초기화됩니다.\n이미 받은 완주 도장은 사라집니다.",
            confirmLabel = "다시 도전", onConfirm = { resetAndStart(course) },
            cancelLabel = "취소",
        )
    }

    private fun resetAndStart(course: BookCourse) {
        store.resetCourse(book.id, course.id)

        // 같은 코스의 실제 주행 세션이 남아 있으면 함께 정리한다.
        // (데모 리플레이로 완주해도 실제 세션은 살아 있을 수 있어, 남기면 주행 화면이 "주행 기록 중"에 막힌다)
        val saved = DriveSessionStore(this).load()
        val snap = DriveHub.get()
        val sameCourse = { b: String?, c: String? -> b == book.id && c == course.id }
        if ((saved.active && sameCourse(saved.bookId, saved.courseId)) ||
            (snap.active && sameCourse(snap.bookId, snap.courseId))
        ) {
            DriveService.stop(this)
        } else if (sameCourse(snap.bookId, snap.courseId)) {
            // 주행 화면이 이전 완주 상태(finished)를 들고 있지 않도록 화면 공유 상태도 비운다
            DriveHub.publish { DriveSnapshot() }
        }
        render()
        startDrive(course)
    }

    private fun startDrive(course: BookCourse) {
        val i = Intent(this, DriveActivity::class.java)
        i.putExtra("bookId", book.id)
        i.putExtra("courseId", course.id)
        startActivity(i)
    }
}
