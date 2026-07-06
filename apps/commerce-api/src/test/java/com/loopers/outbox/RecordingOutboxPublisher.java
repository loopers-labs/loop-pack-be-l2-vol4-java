package com.loopers.outbox;

import com.loopers.outbox.application.OutboxMessagePublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 발행 사실과 첫 발행 시각(nanos)을 기록하는 테스트용 발행기. 스케줄러 스레드와 테스트 스레드가 함께 접근한다.
 */
public class RecordingOutboxPublisher implements OutboxMessagePublisher {

    private final List<String> payloads = Collections.synchronizedList(new ArrayList<>());
    private volatile long firstPublishedAtNanos = -1;

    @Override
    public synchronized void publish(String topic, String key, String payload) {
        if (firstPublishedAtNanos < 0) {
            firstPublishedAtNanos = System.nanoTime();
        }
        payloads.add(payload);
    }

    public List<String> payloads() {
        return payloads;
    }

    public long firstPublishedAtNanos() {
        return firstPublishedAtNanos;
    }

    public synchronized void clear() {
        payloads.clear();
        firstPublishedAtNanos = -1;
    }
}
