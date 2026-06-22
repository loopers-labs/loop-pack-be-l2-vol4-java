package com.loopers.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final String customMessage;
    private final String logMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, null);
    }

    public CoreException(ErrorType errorType, String customMessage) {
        this(errorType, customMessage, customMessage, null);
    }

    public CoreException(ErrorType errorType, String customMessage, Throwable cause) {
        this(errorType, customMessage, customMessage, cause);
    }

    public CoreException(ErrorType errorType, String customMessage, String logMessage, Throwable cause) {
        super(customMessage != null ? customMessage : errorType.getMessage(), cause);
        this.errorType = errorType;
        this.customMessage = customMessage;
        this.logMessage = logMessage != null ? logMessage : (customMessage != null ? customMessage : errorType.getMessage());
    }
}
