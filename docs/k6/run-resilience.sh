#!/usr/bin/env bash
# =============================================================================
# B1/B2/B3 오케스트레이션 — k6 부하 도중 PG 를 내렸다 다시 올린다.
#
# k6(JS)는 프로세스를 죽일 수 없으므로, 이 래퍼가 타이밍을 조율한다:
#   1) resilience.js 를 백그라운드로 시작
#   2) DOWN_AT 초 후  → :8082 리스너(pg-simulator) kill  = PG 다운
#   3) UP_AFTER 초 후 → PG_START 명령으로 재기동           = PG 복귀
#   4) k6 종료 대기
#
# 사용법:
#   PG_START="<pg-simulator 기동 명령>" \
#   DURATION=3m DOWN_AT=45 UP_AFTER=60 \
#   docs/k6/run-resilience.sh
#
# 예) PG_START="java -jar ~/pg-simulator/build/libs/pg-simulator.jar"
#
# B3(보정 회수) 확인은 이 스크립트가 끝난 뒤:
#   - 약 30초(reconciler 주기) 기다린 뒤 commerce-api 로그에서 "주문 기준 보정: 무거래 정리" 확인
#   - 또는 해당 주문 상태가 PAYMENT_FAILED 로 정리됐는지 조회
# =============================================================================
set -euo pipefail

PG_PORT="${PG_PORT:-8082}"
DOWN_AT="${DOWN_AT:-45}"      # k6 시작 후 몇 초에 PG 를 내릴지
UP_AFTER="${UP_AFTER:-60}"    # 내린 뒤 몇 초 후 다시 올릴지
DURATION="${DURATION:-3m}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${PG_START:-}" ]]; then
  echo "[!] PG_START 환경변수에 pg-simulator 기동 명령을 넣어주세요 (PG 복귀에 필요)."
  echo "    예: PG_START=\"java -jar pg-simulator.jar\" DURATION=$DURATION $0"
  exit 1
fi

echo "[*] k6 시작 (DURATION=$DURATION) — ${DOWN_AT}s 후 PG 다운, +${UP_AFTER}s 후 복귀"
DURATION="$DURATION" k6 run "$SCRIPT_DIR/resilience.js" &
K6_PID=$!

# 1) 다운 시점까지 대기 → PG 리스너 kill
sleep "$DOWN_AT"
echo "[*] (${DOWN_AT}s) PG 다운 — :${PG_PORT} 리스너 kill"
lsof -ti "tcp:${PG_PORT}" | xargs kill -9 2>/dev/null || echo "    (이미 죽어있거나 포트 점유 없음)"

# 2) 복귀 시점까지 대기 → PG 재기동
sleep "$UP_AFTER"
echo "[*] (+${UP_AFTER}s) PG 복귀 — 재기동: $PG_START"
eval "$PG_START" >/tmp/pg-simulator-restart.log 2>&1 &

# 3) k6 종료 대기
wait "$K6_PID"
echo "[*] k6 종료. B3 확인: ~30s 뒤 commerce-api 로그의 '주문 기준 보정: 무거래 정리' / 주문 상태(PAYMENT_FAILED) 점검"
