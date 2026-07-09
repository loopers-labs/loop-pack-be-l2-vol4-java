package com.loopers.application.queue;

public final class WaitingQueueInfo {

    private WaitingQueueInfo() {}

    public record Position(
        Long position,
        Long waitingCount,
        Long estimatedWaitSeconds,
        Integer pollingIntervalSeconds,
        String entryToken
    ) {
        public static Position admitted(Long waitingCount, String entryToken) {
            return new Position(0L, waitingCount, 0L, 1, entryToken);
        }

        public static Position waiting(Long position, Long waitingCount, Long estimatedWaitSeconds) {
            return new Position(position, waitingCount, estimatedWaitSeconds, pollingIntervalSeconds(position), null);
        }

        private static int pollingIntervalSeconds(Long position) {
            if (position == null || position <= 100) {
                return 1;
            }
            if (position <= 1_000) {
                return 3;
            }
            return 5;
        }
    }
}
