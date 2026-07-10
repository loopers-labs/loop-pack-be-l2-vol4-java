package com.loopers.application.queue;

/**
 * 대기열에서 유저의 현재 위치.
 * token 이 있으면 입장 차례가 온 것(position 0), 없으면 대기 중이거나 대기열에 없는 상태.
 */
public record QueuePositionInfo(long position, long totalWaiting, long estimatedWaitSeconds, String token) {
}
