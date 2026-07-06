package com.loopers.support.dlq;

/** DLT 레코드에 싣는 진단 헤더 키. 발행기(DeadLetterPublisher)와 적재 컨슈머(DlqIngestConsumer)가 공유한다. */
public final class DlqHeaders {

    private DlqHeaders() {
    }

    public static final String ORIGINAL_TOPIC = "x-original-topic";
    public static final String ORIGINAL_PARTITION = "x-original-partition";
    public static final String ORIGINAL_OFFSET = "x-original-offset";
    public static final String EXCEPTION_CLASS = "x-exception-class";
    public static final String EXCEPTION_MESSAGE = "x-exception-message";
}
