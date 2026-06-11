package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "stock")
@SQLRestriction("deleted_at IS NULL")
public class StockModel extends BaseEntity {

    private Long productId;
    private Long quantity;

    protected StockModel() {
    }

    public StockModel(Long productId, Long quantity) {
        validate(quantity);

        this.productId = productId;
        this.quantity = quantity;
    }

    public void update(Long newQuantity) {
        validate(newQuantity);

        this.quantity = newQuantity;
    }

    private void validate(Long quantity) {
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
    }
}
