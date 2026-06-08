package com.loopers.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final ErrorCode errorCode;
    private final String customMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, errorType, null);
    }

    public CoreException(ErrorType errorType, String customMessage) {
        this(errorType, errorType, customMessage);
    }

    public CoreException(ErrorType errorType, ErrorCode errorCode) {
        this(errorType, errorCode, null);
    }

    private CoreException(ErrorType errorType, ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.getMessage());
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }
}
