# Step 1 — ApplicationEvent 경계 나누기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문/좋아요 유스케이스의 부가 후속 로직(좋아요 집계·데이터플랫폼 전송·행동 로깅)을 Spring ApplicationEvent 로 분리한다.

**Architecture:** 도메인별 과거형 record 이벤트를 Facade 가 발행하고, 리스너가 구독한다. 리스너 실행 정책은 후속 성격에 맞춰 혼합(빠른 로컬 집계=동기 AFTER_COMMIT, 외부·부가=@Async). Kafka 없음(Step 2). 설계 근거: `docs/superpowers/specs/2026-07-01-step1-application-event-boundary-design.md`.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring `ApplicationEventPublisher` / `@TransactionalEventListener` / `@Async`, JPA, Testcontainers(MySQL), JUnit5 + Mockito + AssertJ + Awaitility(스타터 포함).

## Global Constraints

- Java 21. commerce-api 는 `modules:kafka` 미의존(Step 1 에서 추가 금지).
- 이벤트는 **원시값 스냅샷 record 만** — 엔티티/프록시 금지(AFTER_COMMIT 는 tx 종료 후 실행 → LazyInitializationException 방지).
- 커밋 prefix: `feat:` / `test:` / `refactor:`. **각 슬라이스 = 별도 커밋.**
- 커밋 메시지에 Claude 공동 저자 트레일러(Co-Authored-By) **절대 금지**.
- TDD: Red → Green → Refactor. 3A(Arrange-Act-Assert).
- 리스너 예외는 사용자 흐름(좋아요/주문)에 전파되지 않는다(동기=try/catch 삼킴, async=`AsyncUncaughtExceptionHandler` 로깅).
- `@Async` 리스너는 `@Async("eventExecutor")` 로 전용 풀 명시 참조.
- 테스트 명령: `./gradlew :apps:commerce-api:test --tests "<FQN>"`.

---

## File Structure

**신규(main):**
- `domain/like/event/LikeAdded.java`, `domain/like/event/LikeRemoved.java`
- `domain/order/event/OrderPlaced.java`
- `domain/product/event/ProductViewed.java`
- `config/AsyncConfig.java`
- `application/like/LikeCountListener.java`
- `domain/dataplatform/DataPlatformSender.java`, `domain/dataplatform/DataPlatformPayload.java`
- `infrastructure/dataplatform/LoggingDataPlatformSender.java`
- `application/dataplatform/DataPlatformEventListener.java`
- `application/useraction/UserActionLogListener.java`

**수정(main):**
- `application/like/LikeFacade.java` (LikeCountRepository 의존 제거 → publisher)
- `infrastructure/like/LikeCountRepositoryImpl.java` (increase/decrease `@Transactional`)
- `application/order/OrderFacade.java` (publisher 추가, OrderPlaced 발행)
- `application/product/ProductFacade.java` (publisher 추가, ProductViewed 발행)

**신규/수정(test):**
- 신규: `application/like/LikeCountListenerTest.java`, `application/like/LikeEventIntegrationTest.java`, `application/dataplatform/DataPlatformPayloadTest.java`, `application/dataplatform/DataPlatformEventIntegrationTest.java`
- 수정: `application/like/LikeFacadeTest.java`, `application/order/OrderFacadeTest.java`, `application/product/ProductFacadeTest.java`, `application/product/ProductFacadeCacheTest.java`, `application/product/ProductFacadeCursorTest.java`

---

## Task 1: 이벤트 인프라 (이벤트 record 4종 + AsyncConfig)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/like/event/LikeAdded.java`
- Create: `.../domain/like/event/LikeRemoved.java`
- Create: `.../domain/order/event/OrderPlaced.java`
- Create: `.../domain/product/event/ProductViewed.java`
- Create: `.../config/AsyncConfig.java`

**Interfaces:**
- Produces:
  - `LikeAdded(Long userId, Long productId, ZonedDateTime occurredAt)`
  - `LikeRemoved(Long userId, Long productId, ZonedDateTime occurredAt)`
  - `OrderPlaced(Long orderId, Long userId, long finalAmount, List<OrderPlaced.Line> lines, ZonedDateTime occurredAt)` / `OrderPlaced.Line(Long productId, int quantity)`
  - `ProductViewed(Long productId, Long userId, ZonedDateTime occurredAt)`
  - `@Bean("eventExecutor") Executor` + `@EnableAsync` 활성화

- [ ] **Step 1: 이벤트 record 4종 작성**

`domain/like/event/LikeAdded.java`:
```java
package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

public record LikeAdded(Long userId, Long productId, ZonedDateTime occurredAt) {}
```

`domain/like/event/LikeRemoved.java`:
```java
package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

public record LikeRemoved(Long userId, Long productId, ZonedDateTime occurredAt) {}
```

`domain/order/event/OrderPlaced.java`:
```java
package com.loopers.domain.order.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderPlaced(Long orderId, Long userId, long finalAmount, List<Line> lines, ZonedDateTime occurredAt) {
    public record Line(Long productId, int quantity) {}
}
```

`domain/product/event/ProductViewed.java`:
```java
package com.loopers.domain.product.event;

import java.time.ZonedDateTime;

public record ProductViewed(Long productId, Long userId, ZonedDateTime occurredAt) {}
```

- [ ] **Step 2: AsyncConfig 작성** (`@EnableAsync` 를 여기 두어 main class 는 건드리지 않음)

`config/AsyncConfig.java`:
```java
package com.loopers.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // @Async void 리스너의 예외를 조용히 삼키지 않고 로깅("예외 은닉" 대응)
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("async 이벤트 리스너 실패: {}", method.getName(), ex);
    }
}
```

- [ ] **Step 3: 컨텍스트 로드 검증(컴파일 + 빈 등록)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.CommerceApiContextTest"`
Expected: PASS (신규 config/record 로 인한 컨텍스트 파괴 없음)

- [ ] **Step 4: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/like/event/ \
        apps/commerce-api/src/main/java/com/loopers/domain/order/event/ \
        apps/commerce-api/src/main/java/com/loopers/domain/product/event/ \
        apps/commerce-api/src/main/java/com/loopers/config/AsyncConfig.java
git commit -m "feat: 애플리케이션 이벤트 인프라 — 도메인 이벤트 record 4종 + AsyncConfig"
```

---

> **구현 개정 (Option A):** 아래 원안(likeCount 를 AFTER_COMMIT 리스너로 이전)은 `LikeConcurrencyTest`(즉시·정확 카운트)와 충돌 + 동기 REQUIRES_NEW 커넥션 풀 고갈로 폐기. **최종 구현:** `LikeFacade.like/unlike` 가 `likeCountRepository.increase/decrease` 를 **트랜잭션 안에서 그대로** 호출(즉시·정확) **+** `LikeAdded/LikeRemoved` 도 발행. `LikeCountListener` 는 **생성하지 않음**(이벤트는 `UserActionLogListener` 로깅과 Step2 metrics 가 소비). `LikeCountRepositoryImpl` 는 원상(@Transactional 없음). 단위 테스트는 increase + publishEvent 둘 다 검증.

## Task 2: Slice A — 좋아요–집계 분리 (동기 AFTER_COMMIT)

**Files:**
- Modify: `application/like/LikeFacade.java`
- Modify: `infrastructure/like/LikeCountRepositoryImpl.java:17-25`
- Create: `application/like/LikeCountListener.java`
- Modify(test): `application/like/LikeFacadeTest.java`
- Create(test): `application/like/LikeCountListenerTest.java`
- Create(test): `application/like/LikeEventIntegrationTest.java`

**Interfaces:**
- Consumes: `LikeAdded`, `LikeRemoved` (Task 1), `LikeCountRepository.increase(Long)/decrease(Long)` (기존).
- Produces: `LikeCountListener.onLikeAdded(LikeAdded)`, `onLikeRemoved(LikeRemoved)`.

- [ ] **Step 1: LikeFacade 단위 테스트를 새 계약으로 수정(실패)**

`application/like/LikeFacadeTest.java` — 생성자/검증을 이벤트 발행 기준으로 교체. 전체 파일 교체:
```java
package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeFacadeTest {

    private static final String LOGIN_ID = "tester01";
    private static final Long PRODUCT_ID = 100L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LikeFacade likeFacade =
        new LikeFacade(likeRepository, productRepository, userRepository, eventPublisher);

    private void givenUser(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
    }

    private Product product() {
        return new Product(1L, "에어맥스", "운동화", 1000L, 10);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Liking {

        @DisplayName("아직 좋아요하지 않았으면, Like 저장 후 LikeAdded 이벤트를 발행한다.")
        @Test
        void savesAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product()));

            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).save(any(Like.class));
            ArgumentCaptor<LikeAdded> captor = ArgumentCaptor.forClass(LikeAdded.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(7L);
            assertThat(captor.getValue().productId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("이미 좋아요한 경우, 저장/발행 모두 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);

            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("상품이 없으면 NOT_FOUND 이고 저장·발행도 하지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> likeFacade.like(LOGIN_ID, PRODUCT_ID));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상태면, Like 삭제 후 LikeRemoved 이벤트를 발행한다.")
        @Test
        void deletesAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);

            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).deleteBy(7L, PRODUCT_ID);
            verify(eventPublisher).publishEvent(any(LikeRemoved.class));
        }

        @DisplayName("좋아요하지 않은 상태면, 삭제/발행 모두 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);

            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository, never()).deleteBy(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeTest"`
Expected: 컴파일 실패 (LikeFacade 생성자가 아직 LikeCountRepository 를 받음).

- [ ] **Step 3: LikeFacade 수정** (LikeCountRepository 제거 → publisher, 이벤트 발행)

`application/like/LikeFacade.java` 전체 교체:
```java
package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 이미 좋아요한 경우
        }
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.save(new Like(userId, productId));
        eventPublisher.publishEvent(new LikeAdded(userId, productId, ZonedDateTime.now()));
    }

    @Transactional
    public void unlike(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 좋아요하지 않은 경우
        }
        likeRepository.deleteBy(userId, productId);
        eventPublisher.publishEvent(new LikeRemoved(userId, productId, ZonedDateTime.now()));
    }

    private Long resolveUserId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."))
            .getId();
    }
}
```

- [ ] **Step 4: LikeCountRepositoryImpl.increase/decrease 에 `@Transactional` 추가**

`infrastructure/like/LikeCountRepositoryImpl.java` — import 추가 `import org.springframework.transaction.annotation.Propagation;` / `import org.springframework.transaction.annotation.Transactional;`, 그리고:
```java
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increase(Long productId) {
        jpaRepository.increase(productId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrease(Long productId) {
        jpaRepository.decrease(productId);
    }
```
**REQUIRES_NEW 필수** — AFTER_COMMIT 리스너는 원래 tx 가 *커밋된 뒤* 실행되므로 기본 REQUIRED 는 이미 끝나가는 tx 에 합류하려다 `TransactionRequiredException("Executing an update/delete query")` 로 실패한다. 새 물리 트랜잭션(REQUIRES_NEW)이 필요. 별도 빈의 트랜잭션이라 실패 시 독립 롤백되고 리스너 try/catch 로 깔끔히 삼켜짐(rollback-only 누수 없음). increase/decrease 는 이제 리스너에서만 호출되므로 REQUIRES_NEW 가 안전.

- [ ] **Step 5: LikeCountListener 작성**

`application/like/LikeCountListener.java`:
```java
package com.loopers.application.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountListener {

    private final LikeCountRepository likeCountRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeAdded(LikeAdded event) {
        try {
            likeCountRepository.increase(event.productId());
        } catch (Exception ex) {
            // fire-and-forget: 좋아요는 이미 커밋됨. 드리프트는 알려진 갭(Step 2에서 닫음).
            log.warn("likeCount 증가 실패 productId={}", event.productId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeRemoved(LikeRemoved event) {
        try {
            likeCountRepository.decrease(event.productId());
        } catch (Exception ex) {
            log.warn("likeCount 감소 실패 productId={}", event.productId(), ex);
        }
    }
}
```

- [ ] **Step 6: LikeCountListener 단위 테스트 작성**

`application/like/LikeCountListenerTest.java`:
```java
package com.loopers.application.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LikeCountListenerTest {

    private final LikeCountRepository likeCountRepository = mock(LikeCountRepository.class);
    private final LikeCountListener listener = new LikeCountListener(likeCountRepository);

    @DisplayName("LikeAdded 를 받으면 likeCount 를 증가시킨다.")
    @Test
    void increments() {
        listener.onLikeAdded(new LikeAdded(7L, 100L, ZonedDateTime.now()));
        verify(likeCountRepository).increase(100L);
    }

    @DisplayName("LikeRemoved 를 받으면 likeCount 를 감소시킨다.")
    @Test
    void decrements() {
        listener.onLikeRemoved(new LikeRemoved(7L, 100L, ZonedDateTime.now()));
        verify(likeCountRepository).decrease(100L);
    }

    @DisplayName("집계 실패해도 예외를 밖으로 던지지 않는다. (좋아요 성공 보장)")
    @Test
    void swallowsException() {
        doThrow(new RuntimeException("db down")).when(likeCountRepository).increase(100L);
        assertDoesNotThrow(() -> listener.onLikeAdded(new LikeAdded(7L, 100L, ZonedDateTime.now())));
    }
}
```

- [ ] **Step 7: 단위 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeFacadeTest" --tests "com.loopers.application.like.LikeCountListenerTest"`
Expected: PASS

- [ ] **Step 8: 통합 테스트 작성 (AFTER_COMMIT phase 증명)**

`application/like/LikeEventIntegrationTest.java`:
```java
package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeEventIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private LikeCountRepository likeCountRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", "운동화", 1000L, 10));
        this.productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private long currentCount() {
        return likeCountRepository.find(productId).map(ProductLikeCount::getCount).orElse(0L);
    }

    @DisplayName("커밋된 트랜잭션에서 LikeAdded 를 발행하면, 동기 AFTER_COMMIT 리스너가 likeCount 를 증가시킨다.")
    @Test
    void afterCommit_increments() {
        tx.executeWithoutResult(s ->
            eventPublisher.publishEvent(new LikeAdded(7L, productId, ZonedDateTime.now())));

        // 동기 AFTER_COMMIT 이라 tx 반환 시점엔 이미 반영됨
        assertThat(currentCount()).isEqualTo(1L);
    }

    @DisplayName("롤백된 트랜잭션에서 LikeAdded 를 발행하면, AFTER_COMMIT 리스너는 실행되지 않는다.")
    @Test
    void rollback_skipsListener() {
        tx.executeWithoutResult(s -> {
            eventPublisher.publishEvent(new LikeAdded(7L, productId, ZonedDateTime.now()));
            s.setRollbackOnly();
        });

        assertThat(currentCount()).isEqualTo(0L);
    }
}
```

- [ ] **Step 9: 통합 테스트 + 기존 E2E 회귀 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeEventIntegrationTest" --tests "com.loopers.interfaces.api.LikeV1ApiE2ETest"`
Expected: PASS. (slice A 는 동기라 E2E 의 `currentLikeCount()==1` 즉시 성립 — Awaitility 불필요.)

- [ ] **Step 10: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/application/like/LikeFacade.java \
        apps/commerce-api/src/main/java/com/loopers/application/like/LikeCountListener.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/like/LikeCountRepositoryImpl.java \
        apps/commerce-api/src/test/java/com/loopers/application/like/
git commit -m "feat: 좋아요-집계 이벤트 분리 — likeCount 갱신을 AFTER_COMMIT 리스너로"
```

---

## Task 3: Slice B — 주문 이벤트 → 데이터플랫폼 전송 (@Async AFTER_COMMIT)

**Files:**
- Create: `domain/dataplatform/DataPlatformSender.java`, `domain/dataplatform/DataPlatformPayload.java`
- Create: `infrastructure/dataplatform/LoggingDataPlatformSender.java`
- Create: `application/dataplatform/DataPlatformEventListener.java`
- Modify: `application/order/OrderFacade.java`
- Modify(test): `application/order/OrderFacadeTest.java`
- Create(test): `application/dataplatform/DataPlatformPayloadTest.java`
- Create(test): `application/dataplatform/DataPlatformEventIntegrationTest.java`

**Interfaces:**
- Consumes: `OrderPlaced` (Task 1).
- Produces:
  - `interface DataPlatformSender { void send(DataPlatformPayload payload); }`
  - `DataPlatformPayload(Long orderId, Long userId, long finalAmount, List<Item> items)` / `Item(Long productId, int quantity)` / `static DataPlatformPayload from(OrderPlaced)`
  - `DataPlatformEventListener.onOrderPlaced(OrderPlaced)`

- [ ] **Step 1: DataPlatformPayload 매핑 단위 테스트 작성(실패)**

`application/dataplatform/DataPlatformPayloadTest.java`:
```java
package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.order.event.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataPlatformPayloadTest {

    @DisplayName("OrderPlaced 를 payload 로 변환한다.")
    @Test
    void mapsFromOrderPlaced() {
        OrderPlaced event = new OrderPlaced(
            100L, 7L, 15000L,
            List.of(new OrderPlaced.Line(11L, 2), new OrderPlaced.Line(12L, 1)),
            ZonedDateTime.now());

        DataPlatformPayload payload = DataPlatformPayload.from(event);

        assertThat(payload.orderId()).isEqualTo(100L);
        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.finalAmount()).isEqualTo(15000L);
        assertThat(payload.items()).hasSize(2);
        assertThat(payload.items().get(0).productId()).isEqualTo(11L);
        assertThat(payload.items().get(0).quantity()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.dataplatform.DataPlatformPayloadTest"`
Expected: 컴파일 실패 (DataPlatformPayload 없음).

- [ ] **Step 3: DataPlatformPayload + DataPlatformSender 작성**

`domain/dataplatform/DataPlatformPayload.java`:
```java
package com.loopers.domain.dataplatform;

import com.loopers.domain.order.event.OrderPlaced;

import java.util.List;

public record DataPlatformPayload(Long orderId, Long userId, long finalAmount, List<Item> items) {

    public record Item(Long productId, int quantity) {}

    public static DataPlatformPayload from(OrderPlaced e) {
        List<Item> items = e.lines().stream()
            .map(l -> new Item(l.productId(), l.quantity()))
            .toList();
        return new DataPlatformPayload(e.orderId(), e.userId(), e.finalAmount(), items);
    }
}
```

`domain/dataplatform/DataPlatformSender.java`:
```java
package com.loopers.domain.dataplatform;

public interface DataPlatformSender {
    void send(DataPlatformPayload payload);
}
```

- [ ] **Step 4: 매핑 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.dataplatform.DataPlatformPayloadTest"`
Expected: PASS

- [ ] **Step 5: LoggingDataPlatformSender(스텁) + DataPlatformEventListener 작성**

`infrastructure/dataplatform/LoggingDataPlatformSender.java`:
```java
package com.loopers.infrastructure.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingDataPlatformSender implements DataPlatformSender {

    @Override
    public void send(DataPlatformPayload payload) {
        // Step 2 에서 Kafka/Outbox 구현체로 교체
        log.info("[DataPlatform] 주문 전송 orderId={} userId={} finalAmount={} itemCount={}",
            payload.orderId(), payload.userId(), payload.finalAmount(), payload.items().size());
    }
}
```

`application/dataplatform/DataPlatformEventListener.java`:
```java
package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.event.OrderPlaced;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class DataPlatformEventListener {

    private final DataPlatformSender dataPlatformSender;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        dataPlatformSender.send(DataPlatformPayload.from(event));
    }
}
```

- [ ] **Step 6: OrderFacadeTest 를 새 생성자 + 발행 검증으로 수정(실패)**

`application/order/OrderFacadeTest.java` 수정:
- import 추가:
```java
import com.loopers.domain.order.event.OrderPlaced;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
```
- 필드/생성자 교체 (line 43-48 영역):
```java
    private final OrderService orderService = new OrderService(); // 순수 도메인 서비스(실물 사용)
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderFacade orderFacade =
        new OrderFacade(orderService, orderRepository, productRepository, userRepository,
            userCouponRepository, eventPublisher);
```
- `CreateOrder` 안에 발행 검증 테스트 추가:
```java
        @DisplayName("주문 저장 후 OrderPlaced 이벤트를 발행한다.")
        @Test
        void publishesOrderPlaced() {
            // arrange
            Product product = productWithId(11L, 1000L, 10);
            givenUser(7L);
            when(productRepository.findAllForUpdate(List.of(11L))).thenReturn(List.of(product));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            orderFacade.createOrder(LOGIN_ID, command(11L, 2));

            // assert
            ArgumentCaptor<OrderPlaced> captor = ArgumentCaptor.forClass(OrderPlaced.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(7L);
            assertThat(captor.getValue().finalAmount()).isEqualTo(2000L);
            assertThat(captor.getValue().lines()).hasSize(1);
            assertThat(captor.getValue().lines().get(0).productId()).isEqualTo(11L);
            assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(2);
        }
```

- [ ] **Step 7: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderFacadeTest"`
Expected: 컴파일 실패 (OrderFacade 생성자에 publisher 없음).

- [ ] **Step 8: OrderFacade 수정** (publisher 주입 + OrderPlaced 발행)

`application/order/OrderFacade.java`:
- import 추가:
```java
import com.loopers.domain.order.event.OrderPlaced;
import org.springframework.context.ApplicationEventPublisher;
```
- 필드 추가(마지막 필드로):
```java
    private final UserCouponRepository userCouponRepository;
    private final ApplicationEventPublisher eventPublisher;
```
- `createOrder` 의 return 직전(현재 line 72-73) 교체:
```java
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlaced(
            saved.getId(), saved.getUserId(), saved.getFinalAmount(),
            saved.getItems().stream()
                .map(i -> new OrderPlaced.Line(i.getProductId(), i.getQuantity()))
                .toList(),
            ZonedDateTime.now()));
        return OrderInfo.from(saved);
```
(`ZonedDateTime`, `List` 는 이미 import 되어 있음.)

- [ ] **Step 9: 단위 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderFacadeTest"`
Expected: PASS

- [ ] **Step 10: 통합 테스트 작성 (@Async AFTER_COMMIT 배선 — Awaitility)**

`application/dataplatform/DataPlatformEventIntegrationTest.java`:
```java
package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@SpringBootTest
class DataPlatformEventIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager txManager;
    @MockitoBean private DataPlatformSender dataPlatformSender; // LoggingDataPlatformSender 를 mock 으로 대체
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("커밋 후 OrderPlaced 를 @Async 리스너가 받아 데이터플랫폼으로 전송한다.")
    @Test
    void sendsAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        OrderPlaced event = new OrderPlaced(100L, 7L, 2000L,
            List.of(new OrderPlaced.Line(11L, 2)), ZonedDateTime.now());

        tx.executeWithoutResult(s -> eventPublisher.publishEvent(event));

        ArgumentCaptor<DataPlatformPayload> captor = ArgumentCaptor.forClass(DataPlatformPayload.class);
        await().atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(dataPlatformSender).send(captor.capture()));
        assertThat(captor.getValue().orderId()).isEqualTo(100L);
    }
}
```

- [ ] **Step 11: 통합 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.dataplatform.*"`
Expected: PASS

- [ ] **Step 12: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/dataplatform/ \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/dataplatform/ \
        apps/commerce-api/src/main/java/com/loopers/application/dataplatform/ \
        apps/commerce-api/src/main/java/com/loopers/application/order/OrderFacade.java \
        apps/commerce-api/src/test/java/com/loopers/application/order/OrderFacadeTest.java \
        apps/commerce-api/src/test/java/com/loopers/application/dataplatform/
git commit -m "feat: 주문 이벤트 → 데이터플랫폼 전송 분리 (@Async AFTER_COMMIT + seam)"
```

---

## Task 4: Slice C — 유저 행동 로깅 (@Async)

**Files:**
- Create: `application/useraction/UserActionLogListener.java`
- Modify: `application/product/ProductFacade.java`
- Modify(test): `application/product/ProductFacadeTest.java`, `application/product/ProductFacadeCacheTest.java`, `application/product/ProductFacadeCursorTest.java`
- Create(test): `application/product/ProductFacadeViewEventTest.java`

**Interfaces:**
- Consumes: `LikeAdded`, `LikeRemoved`, `OrderPlaced`, `ProductViewed` (Task 1).
- Produces: `UserActionLogListener` (로깅 전용). `ProductFacade.getProductDetail` 이 `ProductViewed` 발행.

- [ ] **Step 1: UserActionLogListener 작성**

`application/useraction/UserActionLogListener.java`:
```java
package com.loopers.application.useraction;

import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.product.event.ProductViewed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActionLogListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeAdded(LikeAdded e) {
        log.info("[UserAction] LIKE_ADDED userId={} productId={}", e.userId(), e.productId());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeRemoved(LikeRemoved e) {
        log.info("[UserAction] LIKE_REMOVED userId={} productId={}", e.userId(), e.productId());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced e) {
        log.info("[UserAction] ORDER_PLACED userId={} orderId={} finalAmount={}",
            e.userId(), e.orderId(), e.finalAmount());
    }

    // 조회는 읽기 경로(트랜잭션 없음) → AFTER_COMMIT 아닌 일반 @EventListener
    @Async("eventExecutor")
    @EventListener
    public void onProductViewed(ProductViewed e) {
        log.info("[UserAction] PRODUCT_VIEWED userId={} productId={}", e.userId(), e.productId());
    }
}
```

- [ ] **Step 2: ProductFacade 발행 단위 테스트 작성(실패)**

`application/product/ProductFacadeViewEventTest.java`:
```java
package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.event.ProductViewed;
import com.loopers.domain.productrank.ProductRankRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductFacadeViewEventTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final LikeCountRepository likeCountRepository = mock(LikeCountRepository.class);
    private final ProductRankRepository productRankRepository = mock(ProductRankRepository.class);
    private final ProductCache productCache = mock(ProductCache.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProductFacade productFacade = new ProductFacade(
        productRepository, brandRepository, likeCountRepository, productRankRepository, productCache, eventPublisher);

    @DisplayName("상세조회 시(캐시 미스) ProductViewed 이벤트를 발행한다.")
    @Test
    void publishesProductViewed_onCacheMiss() {
        when(productCache.getDetail(1L)).thenReturn(Optional.empty());
        Product product = new Product(9L, "에어맥스", "운동화", 1000L, 10);
        when(productRepository.find(1L)).thenReturn(Optional.of(product));
        when(brandRepository.find(9L)).thenReturn(Optional.of(new Brand("나이키", "Just Do It")));
        when(likeCountRepository.find(1L)).thenReturn(Optional.empty());

        productFacade.getProductDetail(1L);

        verify(eventPublisher).publishEvent(any(ProductViewed.class));
    }

    @DisplayName("상세조회 시(캐시 히트) 에도 ProductViewed 이벤트를 발행한다.")
    @Test
    void publishesProductViewed_onCacheHit() {
        ProductDetailInfo cached = mock(ProductDetailInfo.class);
        when(productCache.getDetail(1L)).thenReturn(Optional.of(cached));

        productFacade.getProductDetail(1L);

        verify(eventPublisher).publishEvent(any(ProductViewed.class));
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.ProductFacadeViewEventTest"`
Expected: 컴파일 실패 (ProductFacade 생성자에 publisher 없음).

- [ ] **Step 4: ProductFacade 수정** (publisher 주입 + ProductViewed 발행)

`application/product/ProductFacade.java`:
- import 추가:
```java
import com.loopers.domain.product.event.ProductViewed;
import org.springframework.context.ApplicationEventPublisher;
import java.time.ZonedDateTime;
```
- 필드 추가(마지막 필드로, `productCache` 뒤):
```java
    private final ProductCache productCache;
    private final ApplicationEventPublisher eventPublisher;
```
- `getProductDetail` 교체(현재 line 52-65):
```java
    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long id) {
        Optional<ProductDetailInfo> cached = productCache.getDetail(id);
        if (cached.isPresent()) {
            eventPublisher.publishEvent(new ProductViewed(id, null, ZonedDateTime.now()));
            return cached.get(); // 캐시 히트
        }
        Product product = loadProduct(id);
        Brand brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        ProductDetailInfo info = ProductDetailInfo.from(product, brand, likeCountOf(id));
        productCache.putDetail(info, DETAIL_TTL); // read-through
        eventPublisher.publishEvent(new ProductViewed(id, null, ZonedDateTime.now()));
        return info;
    }
```
(참고: Step 1 은 읽기 경로에 유저 컨텍스트가 없어 userId=null. 유저 식별은 후속 슬라이스로 미룸 — 알려진 단순화.)

- [ ] **Step 5: 기존 ProductFacade 단위 테스트 생성자 수정**

`ProductFacadeTest.java`, `ProductFacadeCacheTest.java`, `ProductFacadeCursorTest.java` 세 파일에서 `new ProductFacade(...)` 호출에 **마지막 인자로** `mock(org.springframework.context.ApplicationEventPublisher.class)` 를 추가한다. (@RequiredArgsConstructor 가 필드 순서대로 생성자를 만들고, publisher 가 마지막 필드이므로 마지막 인자.) 각 파일에 필요 시 `import org.springframework.context.ApplicationEventPublisher;` 및 `import static org.mockito.Mockito.mock;` 확인.

- [ ] **Step 6: 관련 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.*"`
Expected: PASS (신규 ViewEventTest + 기존 Product 테스트 회귀 없음)

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/application/useraction/ \
        apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java \
        apps/commerce-api/src/test/java/com/loopers/application/product/
git commit -m "feat: 유저 행동 로깅 이벤트 — ProductViewed 발행 + UserActionLogListener(@Async)"
```

---

## Task 5: 전체 회귀 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew :apps:commerce-api:test`
Expected: BUILD SUCCESSFUL. 실패 시 해당 테스트 로그로 원인 파악 후 수정(특히 ProductFacade 생성자 변경이 누락된 테스트가 없는지).

- [ ] **Step 2: 이벤트 흐름 수동 확인(선택)**

앱 기동 후 좋아요/주문/상세조회 호출 → 로그에서 `[UserAction] ...`, `[DataPlatform] ...` 출력 및 like_count 증가 확인.

---

## Self-Review (플랜 작성자 체크 결과)

- **Spec 커버리지**: 좋아요-집계 분리=Task2, 주문 부가로직(데이터플랫폼)=Task3, 유저 행동 로깅=Task4, 리스너 phase/@Async 혼합=각 Task, 이벤트 인프라=Task1. 알려진 갭(like_count 드리프트)·판매량 시점(OrderPaid Step2)·userId 단순화는 문서에 명시. 커버 완료.
- **Placeholder 스캔**: 모든 코드 스텝에 실제 코드 포함. TODO/TBD 없음.
- **타입 일관성**: `OrderPlaced.Line(Long productId, int quantity)`, `OrderItem.getProductId():Long`/`getQuantity():Integer`, `Order.getFinalAmount():Long`(→ long 오토언박싱), `DataPlatformPayload.from(OrderPlaced)`, `LikeCountRepository.increase/decrease(Long)` — 전 태스크에서 일관.
- **주의**: ProductFacade 생성자 변경은 3개 단위 테스트에 파급(Task4 Step5). @Async 통합 테스트는 `@MockitoBean` + Awaitility(스타터 포함). slice A(동기)는 기존 E2E 회귀 없음.
