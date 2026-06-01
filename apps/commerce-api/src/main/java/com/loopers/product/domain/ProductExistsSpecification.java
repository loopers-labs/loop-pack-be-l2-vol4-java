package com.loopers.product.domain;

import com.loopers.support.Specification;

import java.util.Optional;

public class ProductExistsSpecification implements Specification<Optional<ProductModel>> {

    @Override
    public boolean isSatisfiedBy(Optional<ProductModel> product) {
        return product.isPresent();
    }
}
