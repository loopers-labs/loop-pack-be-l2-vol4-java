package com.loopers.tddstudy.domain.brand;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brand")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String status;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Brand() {}

    public Brand(String name, String description, Long ownerId) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void publish() {
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.status = "DELETED";
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isVisibleTo(Long userId, String userRole) {
        if ("OPERATOR".equals(userRole)) return true;
        if (isDeleted()) return false;
        if (this.ownerId != null && this.ownerId.equals(userId)) return true;
        return isActive();
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("브랜드 이름은 필수입니다.");
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Long getOwnerId() { return ownerId; }
    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isDeleted() { return "DELETED".equals(status); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
