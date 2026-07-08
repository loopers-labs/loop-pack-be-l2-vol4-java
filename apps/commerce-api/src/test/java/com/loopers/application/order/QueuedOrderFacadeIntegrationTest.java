package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QueuedOrderFacadeIntegrationTest {

    private final QueuedOrderFacade queuedOrderFacade;
    private final EntryTokenRepository entryTokenRepository;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long userId;
    private Long productAId;

    @Autowired
    public QueuedOrderFacadeIntegrationTest(
        QueuedOrderFacade queuedOrderFacade,
        EntryTokenRepository entryTokenRepository,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.queuedOrderFacade = queuedOrderFacade;
        this.entryTokenRepository = entryTokenRepository;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01",
            "Abcd1234!",
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("입장 토큰이 없으면 주문이 ENTRY_TOKEN_INVALID 로 거부되고, 주문 행이 생성되지 않는다.")
    @Test
    void rejectsOrderWhenEntryTokenIsAbsent() {
        // given
        OrderCommand.Place command = new OrderCommand.Place(List.of(new OrderCommand.Line(productAId, 1)));

        // when
        CoreException exception = assertThrows(CoreException.class, () -> queuedOrderFacade.placeOrder(userId, command));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        assertThat(orderJpaRepository.count()).isZero();
    }

    @DisplayName("유효한 입장 토큰이 있으면 주문이 생성되고, 사용된 토큰은 소비되어 사라진다.")
    @Test
    void placesOrderAndConsumesTokenWhenEntryTokenIsValid() {
        // given
        entryTokenRepository.issue(userId, Duration.ofMinutes(5));
        OrderCommand.Place command = new OrderCommand.Place(List.of(new OrderCommand.Line(productAId, 2)));

        // when
        OrderInfo info = queuedOrderFacade.placeOrder(userId, command);

        // then
        assertThat(orderJpaRepository.count()).isEqualTo(1L);
        assertThat(info.totalAmount()).isEqualTo(200_000L);
        assertThat(entryTokenRepository.find(userId)).isEmpty();
    }
}
