package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponAdminService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.interfaces.api.coupon.CouponAdminDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponAdminFacade {

    private final CouponAdminService couponAdminService;

    public CouponAdminDto.TemplateResponse registerTemplate(CouponAdminDto.RegisterTemplateRequest request) {
        CouponTemplate template = new CouponTemplate(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.expiredAt()
        );
        CouponTemplate saved = couponAdminService.createTemplate(template);
        return convertToResponse(saved);
    }

    public CouponAdminDto.TemplateResponse getTemplate(Long id) {
        CouponTemplate template = couponAdminService.getTemplate(id);
        return convertToResponse(template);
    }

    public Page<CouponAdminDto.TemplateResponse> getTemplates(Pageable pageable) {
        Page<CouponTemplate> templates = couponAdminService.getTemplates(pageable);
        return templates.map(this::convertToResponse);
    }

    public CouponAdminDto.TemplateResponse updateTemplate(Long id, CouponAdminDto.UpdateTemplateRequest request) {
        CouponTemplate template = new CouponTemplate(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.expiredAt()
        );
        CouponTemplate updated = couponAdminService.updateTemplate(id, template);
        return convertToResponse(updated);
    }

    public void deleteTemplate(Long id) {
        couponAdminService.deleteTemplate(id);
    }

    public Page<CouponAdminDto.IssueResponse> getIssues(Long templateId, Pageable pageable) {
        Page<CouponIssue> issues = couponAdminService.getIssues(templateId, pageable);
        return issues.map(issue -> new CouponAdminDto.IssueResponse(
                issue.getId(),
                issue.getUserId(),
                issue.getCouponTemplateId(),
                issue.getStatus().name(),
                issue.getCreatedAt()
        ));
    }

    private CouponAdminDto.TemplateResponse convertToResponse(CouponTemplate template) {
        return new CouponAdminDto.TemplateResponse(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getMaxDiscountAmount(),
                template.getExpiredAt()
        );
    }
}
