package com.loopers.infrastructure.queue;

final class QueueRedisKeys {

    static final String WAITING_QUEUE_KEY = "queue:waiting-queue";
    static final String ENTRY_TOKEN_KEY_PREFIX = "queue:entry-token:";

    private QueueRedisKeys() {
    }
}
