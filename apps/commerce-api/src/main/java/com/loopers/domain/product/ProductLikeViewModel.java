package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_like_view")
public class ProductLikeViewModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    protected ProductLikeViewModel() {}

    public ProductLikeViewModel(Long productId) {
        this.productId = productId;
    }

    public void increment() {
        this.likeCount++;
    }

    public void decrement() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "[productId = " + productId + "] 좋아요 수가 이미 0입니다.");
        }
        this.likeCount--;
    }

    public Long getProductId() {
        return productId;
    }

    public int getLikeCount() {
        return likeCount;
    }
}
