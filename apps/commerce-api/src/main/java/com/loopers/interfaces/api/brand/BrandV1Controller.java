package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandFacade brandFacade;

    @GetMapping("/{id}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> getActive(@PathVariable UUID id) {
        BrandInfo info = brandFacade.getActive(id);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
