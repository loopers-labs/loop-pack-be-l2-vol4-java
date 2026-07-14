package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.WaitingQueueInfo;

public final class QueueDto {

    private QueueDto() {}

    public static final class Position {

        private Position() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long position,
                Long waitingCount,
                Long estimatedWaitSeconds,
                Integer pollingIntervalSeconds,
                String entryToken
            ) {
                public static Response from(WaitingQueueInfo.Position position) {
                    return new Response(
                        position.position(),
                        position.waitingCount(),
                        position.estimatedWaitSeconds(),
                        position.pollingIntervalSeconds(),
                        position.entryToken()
                    );
                }
            }
        }
    }
}
