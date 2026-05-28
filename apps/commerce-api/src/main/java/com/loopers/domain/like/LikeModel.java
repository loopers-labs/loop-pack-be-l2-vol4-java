package com.loopers.domain.like;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeModel {

    @EmbeddedId
    private LikeId id;

    @Column(name = "liked_at")
    private ZonedDateTime likedAt;

    private LikeModel(LikeId id, ZonedDateTime likedAt) {
        this.id = id;
        this.likedAt = likedAt;
    }

    public static LikeModel of(Long userId, Long productId) {
        return new LikeModel(LikeId.of(userId, productId), ZonedDateTime.now());
    }

    public void like() {
        if (this.likedAt == null) {
            this.likedAt = ZonedDateTime.now();
        }
    }

    public void unlike() {
        this.likedAt = null;
    }

    public boolean isLiked() {
        return this.likedAt != null;
    }
}
