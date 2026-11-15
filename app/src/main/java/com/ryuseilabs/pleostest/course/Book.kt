package com.ryuseilabs.pleostest.course

/**
 * 스탬프북 = 완주 흐름 묶음. 모든 도장은 "차량 증빙" — 내비 도착 이벤트(DESTINATION)로만 찍힌다.
 * 각 지점은 목적지의 연속으로 안내한다 (경유지 없음).
 * SDK 제약(경유지 2 + 목적지 1 = 3점/경로)과 무관하게, 단순하고 견고한 목적지 연속 구조.
 *
 * 출발은 '현재 차량 위치'다 (고정 시작점 없음). 첫 포인트는 등기산 스카이워크.
 */
data class RouteTarget(
    val poiName: String,
    val poiId: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val poiSubId: String = "0",
)

enum class StampSource { WAYPOINT, DESTINATION }

data class StampPoint(
    val id: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val source: StampSource,
    val chapterIndex: Int,
    val waypointIndex: Int = -1, // WAYPOINT일 때 해당 인덱스
)

data class Chapter(
    val title: String,
    /** 리플레이/다이어그램 기준점. 실제 주행의 출발은 '현재 차량 위치'라 경로 주입에 쓰이지 않음 */
    val start: CoursePoint,
    val waypoints: List<RouteTarget>,
    val destination: RouteTarget,
) {
    fun toCourse(): Course = Course(
        name = title,
        start = start,
        points = (waypoints + destination).map { CoursePoint(it.poiName, it.lat, it.lng) },
    )
}

data class BookCourse(
    val id: String,
    val title: String,
    val available: Boolean,
    /** 완주 흐름 (1개 이상). 잠금 슬롯이면 비어 있음 */
    val chapters: List<Chapter>,
    val stamps: List<StampPoint>,
    /** 목록/영수증에 표시할 한 줄 설명 */
    val subtitle: String,
) {
    val stampCount get() = stamps.size
    fun chapterToCourse(index: Int): Course = chapters[index].toCourse()
}

data class StampBook(
    val id: String,
    val name: String,
    val tagline: String,
    val courses: List<BookCourse>,
) {
    val availableCourses get() = courses.filter { it.available }
}

/**
 * 북 목록.
 *  - 동해 해안 드라이브 북: 울진 후포 → 영덕 창포리 해안 남하 (메인)
 *  - 판교 밸리 드라이브 북: 검증용/참고용 (이전 구현)
 * 좌표는 공개 자료 기반 근사치(±1~2km). 도장 판정은 내비 도착 이벤트로만 하므로 무관.
 * POI ID는 내비 검색·안내 로그에서 실측 확보 (2026-11-04): 등기산 36krs3qf9sfncm5nqhe3 · 고래불 36krrr3pn6f3cm5nqhe3 · 죽도산 36krfhmms57qcm5nqhe3 · 창포말 36krg82pn3fhcm5nqhe3.
 */
object Books {
    private fun locked(id: String) = BookCourse(id, "지정 코스 준비 중", false, emptyList(), emptyList(), "코스 기획 중")

    private val uljinCourse = BookCourse(
        id = "dg-01",
        title = "등기산 → 고래불 → 죽도산 → 창포말",
        available = true,
        chapters = listOf(
            Chapter(
                title = "1구간 · 등기산 스카이워크",
                start = CoursePoint("후포항", 36.6780, 129.4500),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "등기산스카이워크",
                    poiId = "36krs3qf9sfncm5nqhe3", // 내비 검색·안내 로그에서 확보
                    address = "경상북도 울진군 후포면 울진대게로 287-11",
                    lat = 36.6786, lng = 129.4545,
                ),
            ),
            Chapter(
                title = "2구간 · 고래불 해수욕장",
                start = CoursePoint("등기산 스카이워크", 36.6786, 129.4545),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "병곡고래불해수욕장",
                    poiId = "36krrr3pn6f3cm5nqhe3", // 내비 검색·안내 로그에서 확보
                    address = "경상북도 영덕군 병곡면 병곡리 195-7",
                    lat = 36.5727, lng = 129.4330,
                ),
            ),
            Chapter(
                title = "3구간 · 죽도산 전망대",
                start = CoursePoint("병곡고래불해수욕장", 36.5727, 129.4330),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "죽도산전망대",
                    poiId = "36krfhmms57qcm5nqhe3", // 내비 검색·안내 로그에서 확보
                    address = "경상북도 영덕군 축산면 축산리",
                    lat = 36.4670, lng = 129.4310,
                ),
            ),
            Chapter(
                title = "4구간 · 창포말등대",
                start = CoursePoint("죽도산전망대", 36.4670, 129.4310),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "창포말등대",
                    poiId = "36krg82pn3fhcm5nqhe3", // 내비 검색·안내 로그에서 확보
                    address = "경상북도 영덕군 영덕읍 창포리 산5-5",
                    lat = 36.3980, lng = 129.4360,
                ),
            ),
        ),
        stamps = listOf(
            StampPoint("dg-deunggisan", "등기산 스카이워크", 36.6786, 129.4545, StampSource.DESTINATION, 0),
            StampPoint("dg-goraebul", "고래불 해수욕장", 36.5727, 129.4330, StampSource.DESTINATION, 1),
            StampPoint("dg-jukdosan", "죽도산 전망대", 36.4670, 129.4310, StampSource.DESTINATION, 2),
            StampPoint("dg-changpomal", "창포말등대", 36.3980, 129.4360, StampSource.DESTINATION, 3),
        ),
        subtitle = "현재 위치에서 출발 · 목적지 연속 4번 · 약 45 km · 4도장",
    )

    val uljinCoast = StampBook(
        id = "uljin-coast",
        name = "동해 해안 드라이브 북",
        tagline = "후포에서 창포말까지, 동해 해안을 완주하는 코스",
        courses = listOf(uljinCourse),
    )

    private val pangyoCourse = BookCourse(
        id = "pg-01",
        title = "판교역 → 판교현대백화점 → 42dot",
        available = true,
        chapters = listOf(
            Chapter(
                title = "1구간 · 판교현대백화점",
                start = CoursePoint("판교역 부근", 37.3948, 127.1112),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "Pangyo Hyundai Department Store",
                    poiId = "36khp2hdj2cfcm5nqhe3",
                    address = "6 Changup-ro 0beon-gil, Sujeong-gu, Seongnam-si, Gyeonggi-do",
                    lat = 37.41275833, lng = 127.09481944,
                ),
            ),
            Chapter(
                title = "2구간 · 42dot",
                start = CoursePoint("Pangyo Hyundai Department Store", 37.41275833, 127.09481944),
                waypoints = emptyList(),
                destination = RouteTarget(
                    poiName = "42dot",
                    poiId = "36khp37hh8gqcm5nqhe5",
                    address = "2621 Nambusunhwan-ro, Gangnam-gu, Seoul",
                    lat = 37.48544722, lng = 127.03636666,
                ),
            ),
        ),
        stamps = listOf(
            StampPoint("pg-dept", "판교현대백화점", 37.41275833, 127.09481944, StampSource.DESTINATION, 0),
            StampPoint("pg-42dot", "42dot", 37.48544722, 127.03636666, StampSource.DESTINATION, 1),
        ),
        subtitle = "현재 위치에서 출발 · 목적지 연속 2번 · 약 13 km · 도장 2개",
    )

    val pangyo = StampBook(
        id = "pangyo-valley",
        name = "판교 밸리 드라이브 북",
        tagline = "판교를 차로 완주하는 코스",
        courses = listOf(pangyoCourse, locked("pg-02"), locked("pg-03")),
    )

    // 메인 북을 앞에
    val all: List<StampBook> = listOf(uljinCoast, pangyo)

    fun byId(id: String?): StampBook = all.firstOrNull { it.id == id } ?: uljinCoast

    fun course(bookId: String?, courseId: String?): BookCourse? =
        byId(bookId).courses.firstOrNull { it.id == courseId }
}
