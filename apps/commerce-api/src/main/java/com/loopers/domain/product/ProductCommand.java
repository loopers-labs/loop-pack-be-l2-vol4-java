package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public final class ProductCommand {

    private ProductCommand() {
    }

    public record Search(int page, int size, Long brandId, ProductSortType sort) {

        public Search {
            if (page < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "page 는 0 이상이어야 합니다.");
            }
            if (size <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "size 는 1 이상이어야 합니다.");
            }
            if (sort == null) {
                sort = ProductSortType.LATEST;
            }
        }
    }
}
