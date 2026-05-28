package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class OrderLine {

    private Long id;
    private Long productId;
    private String productName;
    private Long price;
    private Integer quantity;

    public OrderLine(Long productId, String productName, Long price, Integer quantity) {
        this(null, productId, productName, price, quantity);
    }

    private OrderLine(Long id, Long productId, String productName, Long price, Integer quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validatePrice(price);
        validateQuantity(quantity);

        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderLine reconstruct(Long id, Long productId, String productName, Long price, Integer quantity) {
        return new OrderLine(id, productId, productName, price, quantity);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getAmount() {
        return price * quantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
