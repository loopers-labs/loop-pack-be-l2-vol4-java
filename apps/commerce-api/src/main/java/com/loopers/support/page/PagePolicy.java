package com.loopers.support.page;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 목록 조회 페이지 정책 (01 §7.2). 한 페이지 상한은 100건, page는 0-base.
 * 잘못된 값(page<0, size<1, size>100)은 BAD_REQUEST.
 */
public final class PagePolicy {

    public static final int MAX_SIZE = 100;

    private PagePolicy() {}

    public static void validate(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        validateSize(size);
    }

    /** 키셋(커서) 페이지네이션은 page 오프셋이 없으므로 size 상한만 검증한다. */
    public static void validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }
}
