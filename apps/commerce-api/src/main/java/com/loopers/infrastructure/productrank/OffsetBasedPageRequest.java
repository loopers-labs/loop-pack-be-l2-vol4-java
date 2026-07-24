package com.loopers.infrastructure.productrank;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Pageable(page/size)은 offset을 page*size로만 표현할 수 있어, limit 배수가 아닌 임의의 offset(예: 랭킹 페이지네이션)을
// JPA 쿼리에 그대로 전달하기 위한 offset 기반 Pageable 구현.
public class OffsetBasedPageRequest implements Pageable {

    private final long offset;
    private final int limit;
    private final Sort sort;

    public OffsetBasedPageRequest(long offset, int limit, Sort sort) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset은 0 이상이어야 합니다: " + offset);
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetBasedPageRequest(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return offset - limit < 0 ? first() : new OffsetBasedPageRequest(offset - limit, limit, sort);
    }

    @Override
    public Pageable first() {
        return new OffsetBasedPageRequest(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetBasedPageRequest((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
