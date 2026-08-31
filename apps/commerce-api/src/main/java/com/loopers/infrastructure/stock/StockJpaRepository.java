package com.loopers.infrastructure.stock;
import com.loopers.domain.stock.StockModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface StockJpaRepository extends JpaRepository<StockModel,Long>{@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StockModel s where s.id=:id") Optional<StockModel> findLocked(@Param("id") long id);}
