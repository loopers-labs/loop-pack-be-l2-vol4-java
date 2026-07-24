package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;

public record QueueInfo(long rank, long total) {

    public static QueueInfo from(QueuePosition position) {
        return new QueueInfo(position.rank(), position.total());
    }
}
