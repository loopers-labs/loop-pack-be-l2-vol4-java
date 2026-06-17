package com.loopers.application.productlike;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final UserService userService;
    private final ProductService productService;
    private final ProductLikeService productLikeService;

    /**
     * 상품 좋아요 등록. 멱등 동작: 이미 좋아요 상태이거나 동시 요청으로 unique 제약을 위반해도 성공 처리한다.
     * <p>
     * 이 메서드는 의도적으로 {@code @Transactional}을 붙이지 않는다. insert+count 증가의 원자적 단위는
     * {@link ProductLikeService#like}의 트랜잭션이며, 그 트랜잭션 밖에서 {@code DataIntegrityViolationException}을
     * 흡수해야 rollback-only 오염 없이 멱등 처리가 가능하다.
     */
    public void like(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getUser(loginId, loginPw);
        productService.getProduct(productId); // 존재 확인 (없으면 NOT_FOUND)
        try {
            productLikeService.like(user.getId(), productId);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 좋아요로 unique 제약 위반: 이미 좋아요 상태로 간주하고 성공 처리(멱등).
        }
    }

    /**
     * 상품 좋아요 취소. 멱등 동작: 좋아요 상태가 아니어도 성공 처리한다.
     */
    public void unlike(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getUser(loginId, loginPw);
        productService.getProduct(productId); // 존재 확인 (없으면 NOT_FOUND)
        productLikeService.unlike(user.getId(), productId);
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
