package com.loopers.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final String customMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, null, null);
    }

    public CoreException(ErrorType errorType, String customMessage) {
        this(errorType, customMessage, null);
    }

    public CoreException(ErrorType errorType, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage : errorType.getMessage(), cause);
        this.errorType = errorType;
        this.customMessage = customMessage;
    }
}
