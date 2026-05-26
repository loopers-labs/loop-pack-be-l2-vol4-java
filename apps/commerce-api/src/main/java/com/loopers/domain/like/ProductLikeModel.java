package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_login_id", "product_id"})
)
public class ProductLikeModel extends BaseEntity {

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected ProductLikeModel() {}

    public ProductLikeModel(String userLoginId, Long productId) {
        validateUserLoginId(userLoginId);
        validateProductId(productId);

        this.userLoginId = userLoginId;
        this.productId = productId;
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
