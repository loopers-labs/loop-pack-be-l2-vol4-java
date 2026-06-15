package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 생성 Domain Service.
 * 여러 도메인(Product 재고 · Brand 이름 · Order 조립)의 협력을 조율한다.
 * 상태 없음. 호출 흐름은 트랜잭션 안(OrderFacade)에서 일어난다.
 */
@Component
@RequiredArgsConstructor
public class OrderCreationService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public OrderModel create(Long userId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        List<OrderItemModel> items = new ArrayList<>();
        for (OrderLine line : lines) {
            // ① 상품 존재 확인 (없으면 NOT_FOUND → 트랜잭션 롤백)
            ProductModel product = productRepository.findByIdForUpdate(line.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "[id = " + line.productId() + "] 상품을 찾을 수 없습니다."));

            // ② 브랜드 조회 (스냅샷의 brandName 용)
            BrandModel brand = brandRepository.findById(product.getBrandId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));

            // ③ 재고 차감 (부족하면 BAD_REQUEST → 트랜잭션 롤백 = All-or-Nothing)
            Quantity orderQty = Quantity.of(line.quantity());
            product.decreaseStock(orderQty);
            // save() 불필요: product 는 findById 로 조회한 영속 상태라
            // dirty checking 이 트랜잭션 커밋 시점에 UPDATE 를 자동 생성한다.

            // ④ 스냅샷으로 OrderItem 생성 (주문 시점 값 박제)
            items.add(OrderItemModel.of(
                    product.getId(),
                    orderQty,
                    product.getPrice(),     // 단가 스냅샷
                    product.getName(),      // 상품명 스냅샷
                    brand.getName(),        // 브랜드명 스냅샷
                    product.getImageUrl()   // 이미지 스냅샷
            ));
        }

        // ⑤ Aggregate 조립 (총액 계산 + PENDING 상태)
        return OrderModel.create(userId, items);
    }

    /** 주문 요청 한 줄 = (상품ID, 수량) */
    public record OrderLine(Long productId, int quantity) {}
}
