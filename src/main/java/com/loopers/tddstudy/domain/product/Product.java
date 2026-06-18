package com.loopers.tddstudy.domain.product;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "product",
        indexes = {
                @Index(name = "idx_product_brand_like", columnList = "brand_id, like_count"),
                @Index(name = "idx_product_brand_id", columnList = "brand_id")
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brandId;
    private String name;
    private int price;
    private int stock;
    private long likeCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Product() {}

    public Product(String name, int price, int stock, Long brandId) {
        validateName(name);
        validatePrice(price);
        validateStock(stock);
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
        this.likeCount = 0;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughStock(int quantity) {
        return this.stock >= quantity;
    }

    public void decreaseStock(int quantity) {
        if (!hasEnoughStock(quantity)) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.stock -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseLikeCount() {
        this.likeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void publish() {
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.status = "DELETED";
        this.updatedAt = LocalDateTime.now();
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품 이름은 필수입니다.");
        }
    }

    private void validatePrice(int price) {
        if (price <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
    }

    private void validateStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }
    }

    public void update(String name, int price, int stock) {
        validateName(name);
        validatePrice(price);
        validateStock(stock);
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.updatedAt = LocalDateTime.now();
    }


    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public int getStock() { return stock; }
    public long getLikeCount() { return likeCount; }
    public String getStatus() { return status; }
    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isDeleted() { return "DELETED".equals(status); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
