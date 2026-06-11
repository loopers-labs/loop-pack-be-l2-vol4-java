package com.loopers.config;

import com.loopers.domain.product.ProductSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ProductSortConverter implements Converter<String, ProductSort> {

    @Override
    public ProductSort convert(String value) {
        return ProductSort.valueOf(value.toUpperCase());
    }
}
