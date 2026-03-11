package com.example.gatewayadmin.repository;

import com.example.gatewayadmin.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Service repository interface.
 *
 * @author leoli
 */
@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

    List<ServiceEntity> findByEnabledTrue();

    ServiceEntity findByName(String name);
}
