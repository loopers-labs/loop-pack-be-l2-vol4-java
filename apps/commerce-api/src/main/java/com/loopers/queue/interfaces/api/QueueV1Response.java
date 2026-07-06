package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueResult;

public class QueueV1Response {

    private QueueV1Response() {
    }

    public record Enter(long position) {
        public static Enter from(QueueResult.Enter result) {
            return new Enter(result.position());
        }
    }

    public record Position(long position, long estimatedWaitSeconds, long pollAfterMs) {
        public static Position from(QueueResult.Position result) {
            return new Position(result.position(), result.estimatedWaitSeconds(), result.pollAfterMs());
        }
    }
}
