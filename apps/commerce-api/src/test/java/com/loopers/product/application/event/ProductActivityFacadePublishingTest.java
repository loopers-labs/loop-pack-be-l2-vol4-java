package com.loopers.product.application.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.Brand;
import com.loopers.inventory.application.InventoryService;
import com.loopers.inventory.domain.Inventory;
import com.loopers.like.application.LikeFacade;
import com.loopers.like.application.LikeService;
import com.loopers.member.application.MemberService;
import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductActivityFacadePublishingTest {

  @DisplayName("성공한 상품 상세 조회는 조회 이벤트를 발행한다.")
  @Test
  void publishesViewedEventOnProductDetail() {
    ProductService productService = mock(ProductService.class);
    BrandService brandService = mock(BrandService.class);
    LikeService likeService = mock(LikeService.class);
    InventoryService inventoryService = mock(InventoryService.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    ProductFacade productFacade =
        new ProductFacade(
            productService, brandService, likeService, inventoryService, eventPublisher);
    Long productId = 1L;
    Product product = new Product(2L, "상품", "설명", 1_000L);
    Brand brand = new Brand("브랜드", "설명");
    Inventory inventory = new Inventory(productId, 10);
    when(productService.get(productId)).thenReturn(product);
    when(brandService.get(2L)).thenReturn(brand);
    when(likeService.getLikeCount(productId)).thenReturn(3L);
    when(inventoryService.getByProductId(productId)).thenReturn(inventory);

    productFacade.getProductDetail(productId);

    verify(eventPublisher).publishViewed(productId);
  }

  @DisplayName("실제 신규 좋아요만 좋아요 이벤트를 발행한다.")
  @Test
  void publishesLikedEventOnlyWhenLikeIsNew() {
    LikeService likeService = mock(LikeService.class);
    MemberService memberService = mock(MemberService.class);
    ProductService productService = mock(ProductService.class);
    BrandService brandService = mock(BrandService.class);
    InventoryService inventoryService = mock(InventoryService.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    LikeFacade likeFacade =
        new LikeFacade(
            likeService,
            memberService,
            productService,
            brandService,
            inventoryService,
            eventPublisher);
    Long productId = 1L;
    when(likeService.like(1L, productId)).thenReturn(true, false);

    likeFacade.registerLike(1L, productId);
    likeFacade.registerLike(1L, productId);

    verify(eventPublisher).publishLiked(productId);
  }

  @DisplayName("좋아요 취소는 좋아요 이벤트를 발행하지 않는다.")
  @Test
  void doesNotPublishEventWhenLikeIsCancelled() {
    LikeService likeService = mock(LikeService.class);
    MemberService memberService = mock(MemberService.class);
    ProductService productService = mock(ProductService.class);
    BrandService brandService = mock(BrandService.class);
    InventoryService inventoryService = mock(InventoryService.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    LikeFacade likeFacade =
        new LikeFacade(
            likeService,
            memberService,
            productService,
            brandService,
            inventoryService,
            eventPublisher);
    Long productId = 1L;

    likeFacade.cancelLike(1L, productId);

    verify(eventPublisher, never()).publishLiked(productId);
  }
}
