package com.loopers.domain.catalog.product;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class Product extends DomainEntity {

    private Long brandId;

    private String name;

    private String description;

    private Money price;

    private StockQuantity stockQuantity;

    private Long likeCount;

    private ProductStatus status;

    public Product(Long brandId, String name, String description, Long price, Integer stockQuantity) {
        this(brandId, name, description, price, stockQuantity, null);
    }

    public Product(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus status
    ) {
        validateBrandId(brandId);
        validateName(name);
        validateDescription(description);

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = new Money(price);
        this.stockQuantity = new StockQuantity(stockQuantity);
        this.likeCount = 0L;
        this.status = resolveStatus(status);
        if (this.status == ProductStatus.STOPPED) {
            delete();
        }
    }

    public static Product reconstruct(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        Long likeCount,
        ProductStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Product product = new Product(brandId, name, description, price, stockQuantity);
        product.likeCount = likeCount == null ? 0L : likeCount;
        product.status = status == null ? product.status : status;
        product.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return product;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPriceAmount() {
        return price.getAmount();
    }

    public Integer getStockQuantity() {
        return stockQuantity.getQuantity();
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public void update(String name, String description, Long price, Integer stockQuantity) {
        update(name, description, price, stockQuantity, null);
    }

    public void update(
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus requestedStatus
    ) {
        ensureNotStopped();
        validateName(name);
        validateDescription(description);

        this.name = name;
        this.description = description;
        this.price = new Money(price);
        this.stockQuantity = new StockQuantity(stockQuantity);
        this.status = resolveStatus(requestedStatus);
        if (this.status == ProductStatus.STOPPED) {
            delete();
        }
    }

    public void decreaseStock(Integer quantity) {
        ensureOrderable();
        this.stockQuantity = stockQuantity.decrease(quantity);
        if (stockQuantity.isZero()) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    public void increaseStock(Integer quantity) {
        ensureNotStopped();
        this.stockQuantity = stockQuantity.increase(quantity);
        this.status = ProductStatus.ON_SALE;
    }

    public void restoreStock(Integer quantity) {
        this.stockQuantity = stockQuantity.increase(quantity);
        if (status != ProductStatus.STOPPED) {
            this.status = ProductStatus.ON_SALE;
        }
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            this.likeCount--;
        }
    }

    public void stopSelling() {
        this.status = ProductStatus.STOPPED;
        delete();
    }

    private void ensureOrderable() {
        if (!isOnSale()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "판매 중인 상품만 주문할 수 있습니다.");
        }
    }

    private ProductStatus resolveStatus(ProductStatus requestedStatus) {
        if (requestedStatus == null) {
            return stockQuantity.isZero() ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE;
        }
        if (requestedStatus == ProductStatus.ON_SALE && stockQuantity.isZero()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 0인 상품은 판매 가능 상태로 변경할 수 없습니다.");
        }

        return requestedStatus;
    }

    private void ensureNotStopped() {
        if (status == ProductStatus.STOPPED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "판매 중지된 상품은 변경할 수 없습니다.");
        }
    }

    private void validateBrandId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validateDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
    }
}
