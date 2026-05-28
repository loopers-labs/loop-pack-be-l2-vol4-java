package com.loopers.order.domain;

import com.loopers.brand.domain.BrandModel;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Map;

/**
 * 주문 생성을 담당하는 무상태 도메인 서비스.
 *
 * <p>Repository 에 의존하지 않는다. 호출자(Application)가 이미 로드한 상품/브랜드를 받아 주문 항목 스냅샷 생성, 총액 계산, 재고
 * 차감(도메인 객체 협력)만 수행한다.
 */
public class OrderCreationService {

    public OrderModel create(
        Long memberId,
        List<OrderLine> lines,
        Map<Long, ProductModel> productMap,
        Map<Long, BrandModel> brandMap) {

        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        OrderModel order = OrderModel.create(memberId);
        for (OrderLine line : lines) {
            ProductModel product = productMap.get(line.productId());
            if (product == null) {
                throw new CoreException(
                    ErrorType.NOT_FOUND, "[id = " + line.productId() + "] 상품을 찾을 수 없습니다.");
            }

            BrandModel brand = brandMap.get(product.getBrandId());
            product.deductStock(line.quantity());

            OrderItemSnapshot snapshot =
                new OrderItemSnapshot(
                    product.getId(),
                    product.getName(),
                    brand != null ? brand.getName() : null,
                    product.getPrice());
            order.addItem(snapshot, line.quantity());
        }
        return order;
    }
}
