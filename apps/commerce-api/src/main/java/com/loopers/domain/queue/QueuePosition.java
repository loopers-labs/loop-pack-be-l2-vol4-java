package com.loopers.domain.queue;

/**
 * 대기열에서의 현재 위치.
 *
 * @param rank  0-based 순번 (0이면 맨 앞)
 * @param total 현재 대기열 전체 인원
 */
public record QueuePosition(long rank, long total) {
}
