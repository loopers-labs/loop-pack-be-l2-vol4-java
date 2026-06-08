package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record ProductSearchCondition(
    Long brandId,
    ProductSortType sortType,
    ProductSortDirection sortDirection,
    int page,
    int size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public static ProductSearchCondition of(Long brandId, String sort, String direction, Integer page, Integer size) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        if (resolvedPage < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (resolvedSize < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
        if (resolvedSize > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 100 이하여야 합니다.");
        }
        
        ProductSortType sortType = ProductSortType.from(sort);
        ProductSortDirection defaultDirection = sortType == ProductSortType.PRICE
            ? ProductSortDirection.ASC
            : ProductSortDirection.DESC;
        return new ProductSearchCondition(
            brandId,
            sortType,
            ProductSortDirection.from(direction, defaultDirection),
            resolvedPage,
            resolvedSize
        );
    }
}
