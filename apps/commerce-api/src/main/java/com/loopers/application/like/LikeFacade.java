package com.loopers.application.like;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.like.model.Like;
import com.loopers.domain.like.service.LikeDomainService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final MemberService memberService;
    private final LikeApplicationService likeApplicationService;
    private final LikeDomainService likeDomainService;
    private final ProductDomainService productDomainService;
    private final BrandDomainService brandDomainService;

    public void addLike(String loginId, Long productId) {
        Member member = memberService.getMember(loginId);
        likeApplicationService.addLike(member.getId(), productId);
    }

    public void removeLike(String loginId, Long productId) {
        Member member = memberService.getMember(loginId);
        likeApplicationService.removeLike(member.getId(), productId);
    }

    public List<LikeInfo> getLikes(String loginId, Long targetUserId) {
        Member member = memberService.getMember(loginId);
        List<Like> likes = likeDomainService.getLikes(member.getId(), targetUserId);

        return likes.stream()
            .map(like -> {
                Product product = productDomainService.getProduct(like.getProductId());
                Brand brand = brandDomainService.getBrand(product.getBrandId());
                return new LikeInfo(product.getId(), product.getName(), product.getPrice(), brand.getName());
            })
            .toList();
    }
}
