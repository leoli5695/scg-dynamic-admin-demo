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
public interface StrategyRepository extends JpaRepository<StrategyEntity, String> {

    List<StrategyEntity> findByEnabledTrue();

    List<StrategyEntity> findByRouteId(String routeId);
}
