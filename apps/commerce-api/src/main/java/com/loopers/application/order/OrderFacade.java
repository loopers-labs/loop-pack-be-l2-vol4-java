package com.loopers.application.order;

import com.loopers.application.queue.EntryTokenGate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntryTokenGate entryTokenGate;
    private final TransactionTemplate transactionTemplate;

    public OrderInfo createOrder(String loginId, PlaceOrderCommand command) {
        return createOrder(loginId, command, null);
    }

    /**
     * 대기열 게이트(행사 스위치, 기본 off) 검증은 트랜잭션 "밖"에서 수행한다.
     * 이 프로젝트 설정(jpa.yml — auto-commit/지연 획득 설정 없음)에선 트랜잭션 시작 시점에 DB 커넥션이
     * 즉시 획득되므로, 검증을 @Transactional 안에 두면 거부될 폭증 트래픽이 Redis 왕복 동안 커넥션 풀을
     * 점유해 back-pressure 전제가 무너진다. @Transactional 분리는 self-invocation(this 호출은 프록시를
     * 안 탐) 함정이 있어 TransactionTemplate 로 경계를 명시한다 — 전파/롤백 의미는 @Transactional 기본과 동일.
     */
    public OrderInfo createOrder(String loginId, PlaceOrderCommand command, String entryToken) {
        entryTokenGate.verify(loginId, entryToken); // 커넥션 획득 전에 무자격 요청 차단
        return transactionTemplate.execute(status -> placeOrder(loginId, command));
    }

    private OrderInfo placeOrder(String loginId, PlaceOrderCommand command) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // 쿠폰 조회는 상품 락 획득 "이전"에 — FOR UPDATE 보유 시간을 줄이고, 무효 쿠폰이면 락 없이 조기 실패
        UserCoupon userCoupon = (command.couponId() != null)
            ? userCouponRepository.find(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."))
            : null;

        // 비관적 락 배치 조회 — productId 오름차순으로 잠가 다중 상품 주문 간 교차 데드락 방지
        List<Long> productIds = command.items().stream()
            .map(PlaceOrderCommand.Item::productId)
            .distinct()
            .sorted()
            .toList();
        Map<Long, Product> products = productRepository.findAllForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderLine> lines = command.items().stream()
            .map(item -> {
                Product product = products.get(item.productId());
                if (product == null) {
                    throw new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + item.productId() + "] 상품을 찾을 수 없습니다.");
                }
                return new OrderLine(product, item.quantity());
            })
            .toList();

        Order order = orderService.place(user.getId(), lines, userCoupon, ZonedDateTime.now());

        lines.forEach(line -> productRepository.save(line.product())); // 재고 차감 반영
        if (userCoupon != null) {
            userCouponRepository.save(userCoupon); // USED 반영 — 커밋 시점 @Version 검증
        }
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlaced(
            saved.getId(), saved.getUserId(), saved.getFinalAmount(),
            saved.getItems().stream()
                .map(i -> new OrderPlaced.Line(i.getProductId(), i.getQuantity()))
                .toList(),
            ZonedDateTime.now()));
        // 주문 성공 시 1회용 입장 토큰 소진. 이 뒤 커밋이 실패하면 "토큰 소진 + 주문 롤백"이 남는데,
        // 쿠폰 @Version 은 커밋 시점 검증이라 이 조합은 반복 발생 가능하다. 다만 fail-safe 방향
        // (무자격 통과·중복 판매 없음, 유저 불편만 존재)이라 수용 — afterCommit 이동은 백로그.
        entryTokenGate.consume(loginId);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String loginId, Long orderId) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        Order order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(user.getId())) {
            // 타 유저 주문은 존재를 드러내지 않고 NOT_FOUND
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }
}
