package com.loopers.interfaces.api;

public record ApiResponse<T>(Metadata meta, T data) {

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(Metadata.success(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    public static ApiResponse<Void> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(Metadata.fail(errorCode, errorMessage), null);
    }

    public record Metadata(Result result, String errorCode, String message) {

        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }

        public enum Result {

            SUCCESS,
            FAIL
        }
    }
}
