package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 키셋(커서) 페이지네이션 커서 — 마지막으로 읽은 행의 정렬 위치를 표현하는 순수 도메인 VO.
 *
 * <p>정렬 기준({@code sort}) + 그 정렬 컬럼의 값({@code sortValue}) + tie-break용 {@code id} 로 구성된다.
 * {@code LATEST}는 정렬 컬럼이 id 단독이라 {@code sortValue}가 null이다.
 *
 * <p>직렬화(Base64/JSON)는 이 VO가 아니라 {@code ProductCursorCodec}이 담당한다 — VO는 순수하게 유지한다.
 */
public record ProductCursor(ProductSortType sort, Long sortValue, Long id) {
    public ProductCursor {
        if (sort == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "커서의 정렬 기준(sort)은 필수입니다.");
        }
        if (id == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "커서의 id는 필수입니다.");
        }
    }
}
