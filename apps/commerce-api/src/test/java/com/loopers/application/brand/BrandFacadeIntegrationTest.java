package com.loopers.application.brand;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class BrandFacadeIntegrationTest {

    @Autowired BrandFacade brandFacade;
    @Autowired BrandService brandService;
    @Autowired ProductFacade productFacade;
    @Autowired LikeService likeService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("브랜드를 삭제할 때 (Brand→Product→Like cascade soft delete — 01 §7.5)")
    class DeleteBrand {

        @DisplayName("브랜드를 삭제하면, 브랜드·하위 상품·상품의 좋아요가 모두 비활성화된다.")
        @Test
        void given_brandWithProductsAndLikes_when_delete_then_allCascadeDeactivated() {
            BrandModel brand = brandService.register("나이키", "스포츠");
            ProductInfo p1 = productFacade.createProduct(brand.getId(), "에어맥스", "러닝화", null, 139000L, 10);
            ProductInfo p2 = productFacade.createProduct(brand.getId(), "조던", "농구화", null, 199000L, 5);
            likeService.like(1L, p1.id());
            likeService.like(2L, p2.id());

            brandFacade.deleteBrand(brand.getId());

            assertAll(
                    () -> assertThat(((CoreException) catchThrowable(() -> brandService.getActiveBrand(brand.getId()))).getErrorType())
                            .isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(((CoreException) catchThrowable(() -> productFacade.getProductDetail(p1.id(), null))).getErrorType())
                            .isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(((CoreException) catchThrowable(() -> productFacade.getProductDetail(p2.id(), null))).getErrorType())
                            .isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(likeService.isLiked(1L, p1.id())).isFalse(),
                    () -> assertThat(likeService.isLiked(2L, p2.id())).isFalse()
            );
        }

        @DisplayName("브랜드를 삭제해도, 다른 브랜드와 그 상품·좋아요는 영향받지 않는다.")
        @Test
        void given_twoBrands_when_deleteOne_then_otherIntact() {
            BrandModel target = brandService.register("나이키", "스포츠");
            BrandModel other = brandService.register("아디다스", "스포츠");
            ProductInfo targetProduct = productFacade.createProduct(target.getId(), "에어맥스", "러닝화", null, 139000L, 10);
            ProductInfo otherProduct = productFacade.createProduct(other.getId(), "울트라부스트", "러닝화", null, 159000L, 8);
            likeService.like(1L, targetProduct.id());
            likeService.like(1L, otherProduct.id());

            brandFacade.deleteBrand(target.getId());

            assertAll(
                    () -> assertThat(brandService.getActiveBrand(other.getId()).getId()).isEqualTo(other.getId()),
                    () -> assertThatCode(() -> productFacade.getProductDetail(otherProduct.id(), null)).doesNotThrowAnyException(),
                    () -> assertThat(likeService.isLiked(1L, otherProduct.id())).isTrue(),
                    () -> assertThat(likeService.isLiked(1L, targetProduct.id())).isFalse()
            );
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingBrand_when_delete_then_throwsNotFound() {
            Throwable thrown = catchThrowable(() -> brandFacade.deleteBrand(9999L));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
