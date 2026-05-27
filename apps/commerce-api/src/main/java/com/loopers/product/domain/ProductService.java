package com.loopers.product.domain;

import com.loopers.brand.domain.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProductService {

    public ProductModel getOrThrow(Optional<ProductModel> product) {
        return product.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
    }

    public String resolveBrandName(Optional<BrandModel> brand) {
        return brand.map(BrandModel::getName).orElse(null);
    }
}
