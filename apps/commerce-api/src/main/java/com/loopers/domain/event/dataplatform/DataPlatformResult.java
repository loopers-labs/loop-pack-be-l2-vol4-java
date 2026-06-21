package com.loopers.domain.event.dataplatform;

public record DataPlatformResult(boolean succeeded, String message) {
    public static DataPlatformResult success() {
        return new DataPlatformResult(true, null);
    }

    public static DataPlatformResult failed(String message) {
        return new DataPlatformResult(false, message);
    }
}
