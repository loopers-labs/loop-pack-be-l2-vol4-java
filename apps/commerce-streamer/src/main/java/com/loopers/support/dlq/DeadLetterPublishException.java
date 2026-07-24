package com.loopers.support.dlq;

/**
 * DLT 발행이 확인되지 않았을 때 던진다. 컨슈머 밖으로 전파돼 ack 를 막고 원본을 재소비하게 한다
 * — 유실 대신 at-least-once. 이 예외가 반복되면 그 파티션은 멈추는데, DLT broker 장애 상황에서는
 * 조용히 유실하는 것보다 멈추는 편이 낫다.
 */
public class DeadLetterPublishException extends RuntimeException {

    public DeadLetterPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
