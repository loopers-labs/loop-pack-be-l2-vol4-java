package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class ProductLike {

    private Long id;
    private String userLoginId;
    private Long productId;

    public ProductLike(String userLoginId, Long productId) {
        this(null, userLoginId, productId);
    }

    private ProductLike(Long id, String userLoginId, Long productId) {
        validateUserLoginId(userLoginId);
        validateProductId(productId);

        this.id = id;
        this.userLoginId = userLoginId;
        this.productId = productId;
    }

    public static ProductLike reconstruct(Long id, String userLoginId, Long productId) {
        return new ProductLike(id, userLoginId, productId);
    }

    public Long getId() {
        return id;
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public Long getProductId() {
        return productId;
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
    }
}
