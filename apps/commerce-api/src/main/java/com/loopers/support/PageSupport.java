package com.loopers.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 메모리에 적재된 전체 목록을 page/size 기준으로 잘라 {@link Page} 로 감싸는 유틸리티.
 *
 * <p>{@code totalElements} 는 슬라이스 이전 전체 건수를 유지하므로, 응답 계층에서 {@code totalPages}/{@code
 * first}/{@code last} 를 일관되게 계산할 수 있다.
 */
public final class PageSupport {

    private PageSupport() {}

    /**
     * 전체 목록을 0-base {@code page} 와 {@code size} 기준으로 슬라이스해 {@link Page} 로 반환한다.
     *
     * @param content 슬라이스 대상 전체 목록
     * @param page 0-base 페이지 번호 (음수는 0 으로 보정)
     * @param size 페이지당 건수 (음수는 0 으로 보정)
     */
    public static <T> Page<T> paginate(List<T> content, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 0);
        long totalElements = content.size();

        if (safeSize == 0) {
            return new PageImpl<>(List.of(), Pageable.unpaged(), totalElements);
        }

        int fromIndex = (int) Math.min((long) safePage * safeSize, content.size());
        int toIndex = Math.min(fromIndex + safeSize, content.size());
        List<T> slice = List.copyOf(content.subList(fromIndex, toIndex));
        return new PageImpl<>(slice, PageRequest.of(safePage, safeSize), totalElements);
    }
}
