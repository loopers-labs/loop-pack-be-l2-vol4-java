package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

/**
 * 주문 한 줄의 문맥을 묶은 도메인 객체.
 *
 * <p>주문 생성 시 항목 단위로 {@code (Product, Stock, quantity)} 가 함께 다뤄지는데,
 * 이를 세 개의 별도 리스트로 들고 다니면 인덱스 매칭 규칙(같은 인덱스가 같은 항목)을
 * 호출자가 암묵적으로 지켜야 한다. 이후 리팩토링(정렬·필터링·중복제거)에서
 * 한 리스트만 변형되면 그 즉시 깨지는 약한 결합이다.
 *
 * <p>이 record 로 한 줄 단위 문맥을 명시적으로 묶어 그 위험을 제거한다.
 * 호출 측({@link com.loopers.application.order.OrderApplicationService})은 OrderLine 리스트를 만들어
 * {@link OrderService} 에 넘긴다.
 */
public record OrderLine(
    ProductModel product,
    StockModel stock,
    int quantity
) {}
