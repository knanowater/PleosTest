package com.ryuseilabs.pleostest

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.ryuseilabs.pleostest.course.BookStore
import com.ryuseilabs.pleostest.course.Books
import com.ryuseilabs.pleostest.course.StampBook
import com.ryuseilabs.pleostest.ui.BookCoverDrawable
import com.ryuseilabs.pleostest.ui.BookStyle
import com.ryuseilabs.pleostest.ui.ButtonH
import com.ryuseilabs.pleostest.ui.PaperDrawable
import com.ryuseilabs.pleostest.ui.Palette
import com.ryuseilabs.pleostest.ui.SealView
import com.ryuseilabs.pleostest.ui.Type
import com.ryuseilabs.pleostest.ui.applyType
import com.ryuseilabs.pleostest.ui.dp
import com.ryuseilabs.pleostest.ui.insetParams
import com.ryuseilabs.pleostest.ui.isWide
import com.ryuseilabs.pleostest.ui.pill
import com.ryuseilabs.pleostest.ui.primaryButton
import com.ryuseilabs.pleostest.ui.showPopup
import com.ryuseilabs.pleostest.ui.stampSlots
import com.ryuseilabs.pleostest.ui.topBar
import com.ryuseilabs.pleostest.ui.uiText

/**
 * 홈 — 로드북 목록. 책장에 꽂힌 로드북이 표지 카드로 주르륵 나열된다.
 * 카드: [표지(책등/박음질/고무밴드)] + [노트 페이지: 소개, 도장 칸, 진행, 펼치기]
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var store: BookStore
    private lateinit var body: LinearLayout
    private val books = Books.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        store = BookStore(getSharedPreferences("stampbook", MODE_PRIVATE))
        val root = ScrollView(this).apply { setBackgroundColor(Palette.bg); isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(28f))
        }
        root.addView(body)
        setContentView(root)
        celebrateIfRequested()
    }

    /** 이미 떠 있는 홈이 재사용될 때(CLEAR_TOP|SINGLE_TOP)도 도장 획득 팝업을 띄운다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        celebrateIfRequested()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        body.removeAllViews()
        val wide = isWide()
        val (doneAll, totalAll) = books.fold(0 to 0) { acc, b ->
            val (d, t) = stampTotals(b); (acc.first + d) to (acc.second + t)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(24f), 0)
        }
        header.addView(topBar("로드스탬프", "나의 로드북 · 스탬프 투어"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("받은 도장 $doneAll / $totalAll", Palette.text, Palette.surfaceHi).apply {
            applyTypeBody()
        })
        body.addView(header)

        books.forEach { body.addView(bookCard(it, wide), insetParams(top = 20f)) }
        body.addView(nextBookSlot(), insetParams(top = 24f))
    }

    private fun android.widget.TextView.applyTypeBody() {
        applyType(Type.BodyS)
        setPadding(dp(20f), dp(10f), dp(20f), dp(10f))
    }

    private fun stampTotals(book: StampBook): Pair<Int, Int> {
        var done = 0; var total = 0
        book.availableCourses.forEach { c ->
            total += c.stampCount
            done += c.stamps.count { store.isStampDone(book.id, c.id, it.id) }
        }
        return done to total
    }

    /** 로드북 한 권 = 표지 + 노트 페이지 */
    private fun bookCard(book: StampBook, wide: Boolean): View {
        val (done, total) = stampTotals(book)
        val allDone = total > 0 && done == total
        val cover = BookStyle.cover(book.id)
        val paper = PaperDrawable(this)
        val t = dp(paper.thickness)

        val card = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            background = paper
            setPadding(dp(16f), dp(16f), dp(16f) + t, dp(16f) + t)
            setOnClickListener { openBook(book, null) }
        }

        // --- 표지 ---
        val coverBox = FrameLayout(this).apply {
            background = BookCoverDrawable(this@HomeActivity, cover)
            setPadding(dp(74f), dp(34f), dp(72f), dp(28f))
        }
        val coverTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        coverTexts.addView(uiText("ROAD BOOK", Type.LabelS, BookStyle.onCoverSub).apply { letterSpacing = 0.18f })
        coverTexts.addView(uiText(book.name, Type.TitleS, BookStyle.onCover, bold = true).apply {
            setPadding(0, dp(10f), 0, 0)
        })
        coverTexts.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val coverFoot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        coverFoot.addView(uiText("$done / $total", Type.BodyM, BookStyle.onCover, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        if (allDone) {
            coverFoot.addView(SealView(this).apply { ink = 0xFFFFC224.toInt(); tilt = -12f },
                LinearLayout.LayoutParams(dp(76f), dp(76f)))
        }
        coverTexts.addView(coverFoot, LinearLayout.LayoutParams(-1, -2))
        coverBox.addView(coverTexts, FrameLayout.LayoutParams(-1, -1))
        card.addView(coverBox,
            if (wide) LinearLayout.LayoutParams(dp(360f), dp(310f))
            else LinearLayout.LayoutParams(-1, dp(250f)))

        // --- 노트 페이지 ---
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (wide) 34f else 14f), dp(if (wide) 8f else 22f), dp(if (wide) 18f else 14f), 0)
        }
        val pills = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val locked = book.courses.size - book.availableCourses.size
        pills.addView(pill("코스 ${book.availableCourses.size}개", Palette.blue, Palette.blueSoft),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
        if (locked > 0) pills.addView(pill("준비 중 ${locked}개", Palette.textSub, Palette.surfaceHi),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
        pills.addView(
            when {
                allDone -> pill("북 완성", Palette.amberText, Palette.amberSoft)
                done > 0 -> pill("진행 중", Palette.mintText, Palette.mintSoft)
                else -> pill("시작 전", Palette.textSub, Palette.surfaceHi)
            }
        )
        page.addView(pills)
        page.addView(uiText(book.tagline, Type.BodyL, Palette.text, bold = true).apply { setPadding(0, dp(16f), 0, 0) })

        // 도장 칸: 코스의 도장을 순서대로 (최대 8칸)
        val flags = book.availableCourses.flatMap { c -> c.stamps.map { store.isStampDone(book.id, c.id, it.id) } }
        page.addView(stampSlots(flags.take(8), 64f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20f) })

        page.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        foot.addView(uiText(
            if (allDone) "모든 도장을 모았어요" else "완주까지 ${total - done}도장",
            Type.BodyM, Palette.textSub
        ), LinearLayout.LayoutParams(0, -2, 1f))
        foot.addView(primaryButton(if (done > 0) "이어서 펼치기" else "펼치기") { openBook(book, null) },
            LinearLayout.LayoutParams(dp(260f), dp(ButtonH.Medium)))
        page.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        card.addView(page, if (wide) LinearLayout.LayoutParams(0, dp(310f), 1f)
            else LinearLayout.LayoutParams(-1, -2))
        return card
    }

    /** 다음 로드북 자리: 점선 빈 칸 */
    private fun nextBookSlot(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(20f), dp(30f), dp(20f), dp(30f))
        background = GradientDrawable().apply {
            cornerRadius = dp(26f).toFloat()
            setStroke(dp(2f), Palette.textDim, dp(10f).toFloat(), dp(8f).toFloat())
        }
        addView(uiText("다음 로드북", Type.BodyL, Palette.textSub, bold = true))
        addView(uiText("새로운 코스가 곧 책장에 꽂혀요", Type.LabelM, Palette.textDim).apply { setPadding(0, dp(4f), 0, 0) })
    }

    private fun openBook(book: StampBook, courseId: String?) {
        val i = Intent(this, BookActivity::class.java)
        i.putExtra("bookId", book.id)
        courseId?.let { i.putExtra("courseId", it) }
        startActivity(i)
    }

    private fun celebrateIfRequested() {
        val courseId = intent.getStringExtra("celebrate_course") ?: return
        intent.removeExtra("celebrate_course")
        val book = books.firstOrNull { b -> b.courses.any { it.id == courseId } } ?: return
        val course = Books.course(book.id, courseId) ?: return

        showPopup(
            iconRes = R.drawable.ic_crown, iconBg = Palette.amber, iconTint = Palette.onFill,
            title = "도장 획득!",
            message = "'${course.title}'\n완주가 차량 증빙으로 확인되었습니다.\n스탬프북에 도장이 찍혔습니다.",
            confirmLabel = "스탬프북 열기", onConfirm = { openBook(book, course.id) },
            cancelLabel = "닫기",
            header = SealView(this).apply {
                ink = Palette.amberText; tilt = -10f
                topText = "ROAD STAMP"; bottomText = "COMPLETE"
                layoutParams = LinearLayout.LayoutParams(dp(210f), dp(210f))
            },
        )
    }
}
