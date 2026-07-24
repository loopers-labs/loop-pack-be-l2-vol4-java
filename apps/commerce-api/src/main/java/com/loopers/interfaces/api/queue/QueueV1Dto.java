package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record QueueResponse(long rank, long total) {

        public static QueueResponse from(QueueInfo info) {
            return new QueueResponse(info.rank(), info.total());
        }
    }
}
