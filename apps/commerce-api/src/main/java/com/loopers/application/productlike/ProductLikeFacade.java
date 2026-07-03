package com.loopers.application.productlike;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.productlike.ProductLikedEvent;
import com.loopers.domain.productlike.ProductUnlikedEvent;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final UserService userService;
    private final ProductService productService;
    private final ProductLikeService productLikeService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 상품 좋아요 등록. 좋아요 insert가 실제로 일어난 경우에만 {@link ProductLikedEvent}를 발행한다.
     * <p>
     * 멱등 처리는 {@link ProductLikeService#like}의 insert IGNORE가 담당하므로 예외 흡수가 필요 없다.
     * like_count 집계는 이 트랜잭션이 커밋된 뒤 이벤트 리스너가 별도 트랜잭션에서 처리한다(경계 분리).
     */
    @Transactional
    public void like(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getUser(loginId, loginPw);
        productService.getProduct(productId); // 존재 확인 (없으면 NOT_FOUND)
        boolean liked = productLikeService.like(user.getId(), productId);
        if (liked) {
            eventPublisher.publishEvent(new ProductLikedEvent(productId, user.getId()));
        }
    }

    /**
     * 상품 좋아요 취소. 실제로 삭제가 일어난 경우에만 {@link ProductUnlikedEvent}를 발행한다(멱등).
     */
    @Transactional
    public void unlike(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getUser(loginId, loginPw);
        productService.getProduct(productId); // 존재 확인 (없으면 NOT_FOUND)
        boolean unliked = productLikeService.unlike(user.getId(), productId);
        if (unliked) {
            eventPublisher.publishEvent(new ProductUnlikedEvent(productId, user.getId()));
        }
    }

    /**
     * 내가 좋아요한 상품 목록 조회. path {userId}는 loginId에 대응하며 로그인 사용자와 일치해야 한다.
     */
    public List<LikedProductInfo> getLikedProducts(String loginId, String loginPw, String userId) {
        UserModel user = userService.getUser(loginId, loginPw);
        if (!loginId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        List<Long> productIds = productLikeService.getLikedProductIds(user.getId());
        return productIds.stream()
            .map(this::findProductOrNull)
            .filter(p -> p != null)
            .map(LikedProductInfo::from)
            .toList();
    }

    private ProductModel findProductOrNull(Long productId) {
        try {
            return productService.getProduct(productId);
        } catch (CoreException e) {
            // 좋아요한 상품이 이후 삭제된 경우 목록에서 제외한다.
            return null;
        }
    }
}
