package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandModel create(String name, String description) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "[name = " + name + "] 이미 존재하는 브랜드명입니다.");
        }
        return brandRepository.save(new BrandModel(name, description));
    }

    /** 어드민용 — 삭제된 브랜드도 조회 */
    public BrandModel get(UUID id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    /** 고객용 — 활성 브랜드만 조회 */
    public BrandModel getActive(UUID id) {
        return brandRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    public Page<BrandModel> getList(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    public BrandModel update(UUID id, String name, String description) {
        BrandModel brand = get(id);
        brand.update(name, description);
        return brand;
    }

    public void delete(UUID id) {
        BrandModel brand = get(id);
        brand.delete();
    }
}
