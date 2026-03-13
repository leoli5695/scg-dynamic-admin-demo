package com.example.gatewayadmin.repository;

import com.example.gatewayadmin.model.StrategyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Strategy repository interface.
 *
 * @author leoli
 */
@Repository
public interface StrategyRepository extends JpaRepository<StrategyEntity, Long> {

    /**
     * Find strategy by business strategy name.
     */
    StrategyEntity findByStrategyName(String strategyName);

    List<StrategyEntity> findByEnabledTrue();
    
    /**
     * Find strategies by strategy ID (UUID).
     */
    List<StrategyEntity> findByStrategyId(String strategyId);
}
