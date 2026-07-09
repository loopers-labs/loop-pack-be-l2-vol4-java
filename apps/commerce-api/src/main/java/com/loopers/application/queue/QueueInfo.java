package com.loopers.application.queue;

public final class QueueInfo {

    private QueueInfo() {
    }

    /**
     * 대기 상태 스냅샷. 두 상태 중 하나다:
     * <ul>
     *   <li><b>대기 중</b> — {@code position}(1-based)·예상 대기·다음 폴링 간격, {@code token=null}</li>
     *   <li><b>입장 가능</b> — {@code position=0}, 발급된 {@code token}, {@code pollAfterMillis=null}(더 폴링할 필요 없음)</li>
     * </ul>
     *
     * <p>{@code totalWaiting} 은 조회 시점의 전체 대기 인원(ZCARD) — "N명이 함께 대기 중" 사회적 증거로
     * 이탈을 늦춘다. 순번과 달리 유저 자신을 포함한 전체 스냅샷이며, 토큰·이탈로 흔들리는 추정값이다.</p>
     */
    public record Position(
            long position,
            long estimatedWaitSeconds,
            Long pollAfterMillis,
            String token,
            long totalWaiting
    ) {

        public static Position waiting(long position, long estimatedWaitSeconds, long pollAfterMillis, long totalWaiting) {
            return new Position(position, estimatedWaitSeconds, pollAfterMillis, null, totalWaiting);
        }

        public static Position ready(String token, long totalWaiting) {
            return new Position(0, 0, null, token, totalWaiting);
        }
    }
}
