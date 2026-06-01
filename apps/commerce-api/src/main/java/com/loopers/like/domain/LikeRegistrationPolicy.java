package com.loopers.like.domain;

import com.loopers.product.domain.ProductExistsSpecification;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LikeRegistrationPolicy {

    private final ProductExistsSpecification productExists = new ProductExistsSpecification();
    private final LikeNotDuplicateSpecification notDuplicate = new LikeNotDuplicateSpecification();

    public void check(Optional<ProductModel> product, Optional<LikeModel> existing) {
        if (!productExists.isSatisfiedBy(product)) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다.");
        }
        if (!notDuplicate.isSatisfiedBy(existing)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
    }
}
