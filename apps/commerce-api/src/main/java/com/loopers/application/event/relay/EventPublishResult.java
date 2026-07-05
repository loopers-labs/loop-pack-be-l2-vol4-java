package com.loopers.application.event.relay;

public record EventPublishResult(boolean succeeded, String failureReason) {

    public static EventPublishResult success() {
        return new EventPublishResult(true, null);
    }

    public static EventPublishResult failed(String failureReason) {
        return new EventPublishResult(false, failureReason);
    }
}
