package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponRepository couponRepository;

    public CouponTemplate createTemplate(CouponTemplate template) {
        return couponRepository.saveTemplate(template);
    }

    public CouponTemplate getTemplate(Long id) {
        return couponRepository.findTemplateById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));
    }

    public Page<CouponTemplate> getTemplates(Pageable pageable) {
        return couponRepository.findAllTemplates(pageable);
    }

    public CouponTemplate updateTemplate(Long id, CouponTemplate template) {
        CouponTemplate target = getTemplate(id);
        target.update(
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getMaxDiscountAmount(),
                template.getExpiredAt()
        );
        return couponRepository.saveTemplate(target);
    }

    public void deleteTemplate(Long id) {
        getTemplate(id); // 존재 확인용
        couponRepository.deleteTemplate(id);
    }

    public Page<CouponIssue> getIssues(Long templateId, Pageable pageable) {
        getTemplate(templateId); // 존재 확인용
        return couponRepository.findAllIssuesByTemplateId(templateId, pageable);
    }
}
