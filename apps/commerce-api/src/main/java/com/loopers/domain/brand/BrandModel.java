package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Brand Aggregate 루트 — 순수 도메인 객체. 검증/활성 상태 같은 비즈니스 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.brand.BrandEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 BrandEntityMapper가 처리한다.
 */
public class BrandModel {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private String name;
    private String description;
    private ZonedDateTime deletedAt;   // null이면 활성 (soft delete, 01 §7.5)

    public BrandModel(String name, String description) {
        this.id = null;
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.deletedAt = null;
    }

    private BrandModel(Long id, String name, String description, ZonedDateTime deletedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.deletedAt = deletedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static BrandModel reconstitute(Long id, String name, String description, ZonedDateTime deletedAt) {
        return new BrandModel(id, name, description, deletedAt);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 null이거나 공백일 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return name;
    }

    // description은 nullable (04 §2.2). 값이 있을 때만 길이 검증.
    private static String validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return description;
    }

    /** 활성 여부 — deletedAt이 null이면 활성 (01 §7.5). */
    public boolean isActive() {
        return deletedAt == null;
    }

    /** soft delete. 멱등 — 이미 삭제됐으면 시각을 유지한다. */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** soft delete 복원. 멱등. */
    public void restore() {
        this.deletedAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
