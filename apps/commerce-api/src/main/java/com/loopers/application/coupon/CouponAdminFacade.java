package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.application.coupon.CouponRepository;
import com.loopers.interfaces.api.coupon.CouponAdminDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponAdminFacade {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponAdminDto.TemplateResponse registerTemplate(CouponAdminDto.RegisterTemplateRequest request) {
        CouponTemplate template = new CouponTemplate(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.expiredAt()
        );
        CouponTemplate saved = couponRepository.saveTemplate(template);
        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CouponAdminDto.TemplateResponse getTemplate(Long id) {
        CouponTemplate template = couponRepository.findTemplateById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰템플릿입니다."));
        return convertToResponse(template);
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminDto.TemplateResponse> getTemplates(Pageable pageable) {
        Page<CouponTemplate> templates = couponRepository.findAllTemplates(pageable);
        return templates.map(this::convertToResponse);
    }

    @Transactional
    public CouponAdminDto.TemplateResponse updateTemplate(Long id, CouponAdminDto.UpdateTemplateRequest request) {
        CouponTemplate target = couponRepository.findTemplateById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰템플릿입니다."));
        target.update(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.expiredAt()
        );
        CouponTemplate updated = couponRepository.saveTemplate(target);
        return convertToResponse(updated);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        couponRepository.findTemplateById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰템플릿입니다."));
        couponRepository.deleteTemplate(id);
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminDto.IssueResponse> getIssues(Long templateId, Pageable pageable) {
        couponRepository.findTemplateById(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰템플릿입니다."));
        Page<CouponIssue> issues = couponRepository.findAllIssuesByTemplateId(templateId, pageable);
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
