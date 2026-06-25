package com.loopers.product.application;

import com.loopers.product.domain.ProductSort;
import com.loopers.product.infrastructure.ProductDetailQueryDsl;
import com.loopers.product.infrastructure.ProductListQueryDsl;
import com.loopers.shared.pagination.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductReadTransactionBoundaryTest {

    @DisplayName("상품 목록 캐시 조회 경로는 Facade가 아니라 DB 조회 구현체에서 읽기 트랜잭션을 시작한다.")
    @Test
    void productListReadTransactionStartsAtQueryDsl() throws NoSuchMethodException {
        // arrange
        Method facadeMethod = ProductFacade.class.getMethod("getProducts", int.class, int.class, Long.class, String.class);
        Method queryMethod = ProductListQueryDsl.class.getMethod(
            "findVisibleProducts",
            PageQuery.class,
            Long.class,
            ProductSort.class
        );

        // act
        Transactional facadeTransaction = facadeMethod.getAnnotation(Transactional.class);
        Transactional queryTransaction = queryMethod.getAnnotation(Transactional.class);

        // assert
        assertThat(facadeTransaction).isNull();
        assertThat(queryTransaction).isNotNull();
        assertThat(queryTransaction.readOnly()).isTrue();
    }

    @DisplayName("상품 상세 캐시 조회 경로는 Facade가 아니라 DB 조회 구현체에서 읽기 트랜잭션을 시작한다.")
    @Test
    void productDetailReadTransactionStartsAtQueryDsl() throws NoSuchMethodException {
        // arrange
        Method facadeMethod = ProductFacade.class.getMethod("getProduct", Long.class);
        Method queryMethod = ProductDetailQueryDsl.class.getMethod("findVisibleProduct", Long.class);

        // act
        Transactional facadeTransaction = facadeMethod.getAnnotation(Transactional.class);
        Transactional queryTransaction = queryMethod.getAnnotation(Transactional.class);

        // assert
        assertThat(facadeTransaction).isNull();
        assertThat(queryTransaction).isNotNull();
        assertThat(queryTransaction.readOnly()).isTrue();
        assertThat(queryMethod.getReturnType()).isEqualTo(Optional.class);
    }
}
