package com.loopers.application.product;

import java.util.List;
import java.util.function.Function;

public record ProductPageResult<T>(List<T> content, long totalElements) {
    public <R> ProductPageResult<R> map(Function<T, R> mapper) {
        return new ProductPageResult<>(content.stream().map(mapper).toList(), totalElements);
    }
}
