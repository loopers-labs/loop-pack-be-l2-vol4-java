package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.FakeBrandRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.FakeCouponTemplateRepository;
import com.loopers.domain.coupon.FakeUserCouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.FakeProductRepository;

import java.time.LocalDateTime;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreationServiceTest {

    private OrderCreationService orderCreationService;
    private FakeProductRepository fakeProductRepository;
    private FakeBrandRepository fakeBrandRepository;
    private FakeOrderRepository fakeOrderRepository;
    private FakeUserCouponRepository fakeUserCouponRepository;
    private ProductService productService;
    private BrandService brandService;
    private CouponTemplateService couponTemplateService;
    private UserCouponService userCouponService;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeBrandRepository = new FakeBrandRepository();
        fakeOrderRepository = new FakeOrderRepository();
        fakeUserCouponRepository = new FakeUserCouponRepository();
        productService = new ProductService(fakeProductRepository);
        brandService = new BrandService(fakeBrandRepository);
        couponTemplateService = new CouponTemplateService(new FakeCouponTemplateRepository());
        userCouponService = new UserCouponService(fakeUserCouponRepository, couponTemplateService);
        orderCreationService = new OrderCreationService(productService, brandService, fakeOrderRepository, userCouponService);
    }

    private BrandModel newBrand() {
        return brandService.createBrand("나이키", "스포츠");
    }

    private ProductModel newProduct(Long brandId, String name, long price, int stock) {
        return productService.createProduct(brandId, name, "설명", price, stock, "https://img/" + name);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("정상 입력이면, 재고가 차감되고 PENDING 주문이 생성된다.")
        @Test
        void createsOrderAndDecreasesStock() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p1 = newProduct(brand.getId(), "A", 1000L, 10);
            ProductModel p2 = newProduct(brand.getId(), "B", 500L, 5);

            List<OrderLine> lines = List.of(
                new OrderLine(p1.getId(), 2),
                new OrderLine(p2.getId(), 3)
            );

            // act
            OrderModel order = orderCreationService.create(100L, lines, null);

            // assert
            assertAll(
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getOriginalPrice()).isEqualTo(1000L * 2 + 500L * 3),
                () -> assertThat(order.getFinalPrice()).isEqualTo(1000L * 2 + 500L * 3),
                () -> assertThat(order.getItems()).hasSize(2),
                () -> assertThat(fakeProductRepository.find(p1.getId()).orElseThrow().getStock()).isEqualTo(8),
                () -> assertThat(fakeProductRepository.find(p2.getId()).orElseThrow().getStock()).isEqualTo(2)
            );
        }

        @DisplayName("재고를 모두 소진하는 주문이면, 상품 상태가 SOLD_OUT 으로 전이된다.")
        @Test
        void transitionsToSoldOut() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 1000L, 3);

            // act
            orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 3)), null);

            // assert
            assertThat(fakeProductRepository.find(p.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.SOLD_OUT);
        }

        @DisplayName("존재하지 않는 상품 ID 가 섞이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 1000L, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(
                    new OrderLine(p.getId(), 1),
                    new OrderLine(999L, 1)
                ), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenStockInsufficient() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 1000L, 2);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 3)), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLinesEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("OrderItem 에 상품 스냅샷(이름, 가격, 브랜드명, 이미지)이 저장된다.")
        @Test
        void preservesSnapshot() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "에어맥스", 100_000L, 10);

            // act
            OrderModel order = orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 1)), null);

            // assert
            OrderItemModel item = order.getItems().get(0);
            assertAll(
                () -> assertThat(item.getProductNameSnapshot()).isEqualTo("에어맥스"),
                () -> assertThat(item.getBrandNameSnapshot()).isEqualTo("나이키"),
                () -> assertThat(item.getPriceSnapshot()).isEqualTo(100_000L),
                () -> assertThat(item.getImageUrlSnapshot()).isEqualTo("https://img/에어맥스")
            );
        }
    }

    @DisplayName("쿠폰을 적용한 주문은, ")
    @Nested
    class WithCoupon {
        private CouponTemplate fixedTemplate;

        @org.junit.jupiter.api.BeforeEach
        void prepareTemplate() {
            fixedTemplate = couponTemplateService.create(
                "1000원 할인", CouponType.FIXED, 1000L, 0L,
                LocalDateTime.of(2099, 12, 31, 23, 59));
        }

        @DisplayName("originalPrice/discountAmount/finalPrice 가 모두 보존되고, 쿠폰은 USED 로 전이된다.")
        @Test
        void appliesCoupon() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 5000L, 10);
            UserCoupon issued = userCouponService.issue(100L, fixedTemplate.getId());

            // act
            OrderModel order = orderCreationService.create(
                100L, List.of(new OrderLine(p.getId(), 1)), issued.getId());

            // assert
            assertAll(
                () -> assertThat(order.getOriginalPrice()).isEqualTo(5000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(1000L),
                () -> assertThat(order.getFinalPrice()).isEqualTo(4000L),
                () -> assertThat(order.getUserCouponId()).isEqualTo(issued.getId()),
                () -> assertThat(fakeUserCouponRepository.find(issued.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserCouponStatus.USED)
            );
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 CONFLICT 예외가 발생한다 — 재사용 불가 보장.")
        @Test
        void rejectsAlreadyUsedCoupon() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 5000L, 10);
            UserCoupon issued = userCouponService.issue(100L, fixedTemplate.getId());
            orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 1)), issued.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 1)), issued.getId())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("타 유저 소유 쿠폰으로 주문하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void rejectsForeignCoupon() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 5000L, 10);
            UserCoupon issued = userCouponService.issue(200L, fixedTemplate.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 1)), issued.getId())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 쿠폰 ID 면 NOT_FOUND 예외가 발생한다.")
        @Test
        void rejectsNonexistentCoupon() {
            // arrange
            BrandModel brand = newBrand();
            ProductModel p = newProduct(brand.getId(), "A", 5000L, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderCreationService.create(100L, List.of(new OrderLine(p.getId(), 1)), 999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
