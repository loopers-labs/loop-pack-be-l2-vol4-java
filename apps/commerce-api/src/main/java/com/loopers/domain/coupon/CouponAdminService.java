package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponRepository;

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
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "鈺곕똻???? ??낅뮉 ?묒쥚猷???쀫탣?깆슦???덈뼄."));
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
        getTemplate(id); // 鈺곕똻???類ㅼ뵥??
        couponRepository.deleteTemplate(id);
    }

    public Page<CouponIssue> getIssues(Long templateId, Pageable pageable) {
        getTemplate(templateId); // 鈺곕똻???類ㅼ뵥??
        return couponRepository.findAllIssuesByTemplateId(templateId, pageable);
    }
}
