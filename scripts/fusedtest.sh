#!/bin/bash
# FusedLocation 도착 판정 자동 검증 (터치 없음, adb 만 사용).
#   ./fusedtest.sh dg-01          # 코스의 지점을 순서대로 위치 주입 → 도장 로그 확인
#   ./fusedtest.sh pg-01 --mock   # geo fix 대신 mock location 사용
# 사전: 앱에서 해당 코스의 '출발'을 한 번 누른다 (DriveService 는 비공개 컴포넌트라 adb 로 못 띄움).
# 주입 방식: 기본 `adb emu geo fix <lng> <lat>` (에뮬레이터). --mock 은 appops mock_location + 에뮬 geo.
# 확인: logcat 태그 RoadStampDrive 의 "FusedLocation 도착 판정" / "stamp ..." 줄.
COURSE="$1"; shift
[ -n "$COURSE" ] || { sed -n 2,6p "$0"; exit 1; }
SRC=$(dirname "$0")/../app/src/main/java/com/ryuseilabs/pleostest/course/Book.kt
adb get-state >/dev/null 2>&1 || { echo "ERROR: adb 기기 없음"; exit 1; }
MOCK=0; [ "$1" = "--mock" ] && MOCK=1
PTS=$(python3 -I - "$SRC" "$COURSE" <<'PY'
import re,sys
t=open(sys.argv[1],encoding="utf-8").read()
m=re.search(r'id = "%s".*?(?=\n    private val |\n    val |\Z)'%re.escape(sys.argv[2]),t,re.S)
if not m: sys.exit(1)
for a,b in re.findall(r'destination = RouteTarget\(.*?lat = ([\d.]+), lng = ([\d.]+)',m.group(0),re.S): print(a,b)
PY
) || { echo "ERROR: 코스 $COURSE 를 찾지 못함"; exit 1; }
[ "$MOCK" = 1 ] && adb shell appops set --user current com.ryuseilabs.pleostest android:mock_location allow
adb logcat -c
n=0
while read -r lat lng; do
  n=$((n+1)); echo "[$n] 주입 lat=$lat lng=$lng"
  # 도착 반경(50m) 밖에서 접근 → 도착 지점 순으로 3점 주입
  adb emu geo fix "$lng" "$lat" >/dev/null; sleep 3
  adb logcat -d -s RoadStampDrive:I | grep -E "FusedLocation 도착|stamp |완주" | tail -3
  sleep 4   # 구간 완료 후 다음 구간은 앱에서 '다음 구간 출발' 필요
done <<< "$PTS"
echo "--- 요약"; adb logcat -d -s RoadStampDrive:I RoadStampLoc:I | grep -E "FusedLocation|stamp |완주"
