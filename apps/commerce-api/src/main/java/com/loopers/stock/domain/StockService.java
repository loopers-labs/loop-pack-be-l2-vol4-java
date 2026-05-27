package com.loopers.stock.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StockService {

    public StockModel getOrThrow(Optional<StockModel> stock) {
        return stock.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보가 존재하지 않습니다."));
    }
}
