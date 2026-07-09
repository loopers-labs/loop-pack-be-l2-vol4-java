package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.ZonedDateTime;

/**
 * 카탈로그 이벤트를 소비해 만든 상품 지표 read model. commerce-api 의 {@code Product.likeCount}(목록 정렬용,
 * 동기·즉시정확)와 <b>의도적으로 중복</b>된다 — 소비처가 다르기 때문이다: likeCount 는 읽기 경로에서 즉시 정확해야 하고,
 * 이쪽은 조회수·판매량과 나란히 놓이는 분석/대시보드용 집계라 eventual 로 충분하다(CQRS read model 분리).
 *
 * <p>도메인 애그리거트가 아니라 이벤트 투영이므로 {@code product_id} 를 자연 PK 로 쓰고 BaseEntity 를 상속하지 않는다.
 * <b>동시성</b>: relay 가 key=productId 로 발행 → 같은 상품 이벤트는 항상 같은 파티션→같은 consumer 스레드로만
 * 처리된다(단일 writer). 그래서 find→증감→save(RMW)에 낙관/비관 락이 필요 없다(파티션 직렬화가 곧 동시성 보장).</p>
 *
 * <p><b>단, 카운터마다 writer 가 다르다</b>: like/view 는 catalog-events collector 가, sales 는 order-events
 * collector 가 갱신한다 → 같은 행을 <b>서로 다른 스레드</b>가 동시에 건드릴 수 있다. 각 <i>카운터</i>는 여전히 파티션
 * 직렬화로 단일 writer 지만, 행 전체로 보면 다중 writer 다. {@link DynamicUpdate} 로 변경된 컬럼만 UPDATE 하게 해
 * (like 증분은 {@code like_count} 만, sales 증분은 {@code sales_count} 만 SET) 서로의 컬럼을 덮어쓰지 않게 한다
 * — @Version 낙관락 대신 컬럼 단위 쓰기로 교차 lost update 를 막는 선택(Inventory 와 같은 패턴).</p>
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "product_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    private ProductMetrics(Long productId) {
        this.productId = productId;
    }

    public static ProductMetrics of(Long productId) {
        return new ProductMetrics(productId);
    }

    public void increaseLike() {
        this.likeCount++;
    }

    /** 이벤트 순서가 어긋나 unlike 가 먼저 도달하는 극단(콜렉터 중간 합류 등)에 대비해 0 미만으로 내려가지 않게 막는다. */
    public void decreaseLike() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /** 결제 성공 1건에 담긴 특정 상품의 판매 수량만큼 누적한다(주문 라인아이템 단위). */
    public void increaseSales(int quantity) {
        this.salesCount += quantity;
    }

    public void increaseView() {
        this.viewCount++;
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = ZonedDateTime.now();
    }
}
