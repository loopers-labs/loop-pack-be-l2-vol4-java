package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;

public class OrderCommand {

    public record OrderLine(Long productId, Integer quantity) {
        public OrderLine {
            if (productId == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
            }
            if (quantity == null || quantity < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
            }
        }

        public static OrderLine of(Long productId, Integer quantity) {
            return new OrderLine(productId, quantity);
        }
    }

    public record MySearch(Long userId, LocalDate from, LocalDate to, int page, int size) {
        public MySearch {
            if (userId == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 비어있을 수 없습니다.");
            }
            if (from != null && to != null && from.isAfter(to)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "기간 시작일은 종료일 이전이어야 합니다.");
            }
            if (page < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지는 0 이상이어야 합니다.");
            }
            if (size < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
            }
        }
    }

    public record AdminSearch(int page, int size) {
        public AdminSearch {
            if (page < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지는 0 이상이어야 합니다.");
            }
            if (size < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
            }
        }
    }
}
