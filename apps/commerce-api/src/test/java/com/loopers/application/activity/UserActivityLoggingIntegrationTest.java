package com.loopers.application.activity;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserActivityLoggingIntegrationTest {

    private final LikeFacade likeFacade;
    private final OrderFacade orderFacade;
    private final UserFacade userFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    // 좋아요/주문 등 행동이 '관측 이벤트' 로 분리 발행되는지 검증하기 위해 핸들러를 spy.
    @MockitoSpyBean
    private UserActivityEventHandler activityEventHandler;

    private Long productId;

    @Autowired
    UserActivityLoggingIntegrationTest(
        LikeFacade likeFacade,
        OrderFacade orderFacade,
        UserFacade userFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.orderFacade = orderFacade;
        this.userFacade = userFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 누르면, 유저 행동 이벤트(PRODUCT_LIKED)가 발행되어 행동 로깅 핸들러가 수신한다.")
    @Test
    void publishesUserActivityEvent_whenUserLikesProduct() {
        // given
        Long userId = 1L;

        // when
        likeFacade.like(userId, productId);

        // then
        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventHandler).on(captor.capture());
        UserActivityEvent event = captor.getValue();
        assertAll(
            () -> assertThat(event.userId()).isEqualTo(userId),
            () -> assertThat(event.type()).isEqualTo(UserActivityEvent.Type.PRODUCT_LIKED),
            () -> assertThat(event.targetId()).isEqualTo(productId)
        );
    }

    @DisplayName("행동 로깅 핸들러가 실패해도, 좋아요(likes 행)는 커밋되어 살아남는다 — 로깅과 본 행위의 분리.")
    @Test
    void likeRowSurvives_whenActivityLoggingFails() {
        // given
        Long userId = 1L;
        doThrow(new RuntimeException("로깅 실패"))
            .when(activityEventHandler).on(any(UserActivityEvent.class));

        // when : 좋아요 커밋 후 AFTER_COMMIT 로깅 핸들러에서 예외가 난다 (전파될 수 있음)
        catchThrowable(() -> likeFacade.like(userId, productId));

        // then : 로깅은 실패했지만 좋아요 자체는 살아남고, 로깅은 '분리된 뒤' 분명히 시도되었다
        assertAll(
            () -> assertThat(likeJpaRepository.count()).isEqualTo(1L),
            () -> verify(activityEventHandler).on(any(UserActivityEvent.class))
        );
    }

    @DisplayName("주문이 완료되면, 유저 행동 이벤트(ORDER_PLACED)가 발행되어 행동 로깅 핸들러가 수신한다.")
    @Test
    void publishesUserActivityEvent_whenOrderIsPlaced() {
        // given
        Long userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com")).id();
        OrderCommand.Place command = new OrderCommand.Place(List.of(
            new OrderCommand.Line(productId, 1)
        ));

        // when
        OrderInfo order = orderFacade.placeOrder(userId, command);

        // then
        ArgumentCaptor<UserActivityEvent> captor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventHandler).on(captor.capture());
        UserActivityEvent event = captor.getValue();
        assertAll(
            () -> assertThat(event.userId()).isEqualTo(userId),
            () -> assertThat(event.type()).isEqualTo(UserActivityEvent.Type.ORDER_PLACED),
            () -> assertThat(event.targetId()).isEqualTo(order.id())
        );
    }
}
