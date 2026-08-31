package com.loopers.domain.stock;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.support.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
// Hides: row-lock acquisition and the atomic stock transaction.
@Component @RequiredArgsConstructor public class StockService{private final StockJpaRepository repository;@Transactional public void decrease(long id,int amount){StockModel s=repository.findLocked(id).orElseThrow(()->new CoreException(ErrorType.NOT_FOUND));s.decrease(amount);}}
