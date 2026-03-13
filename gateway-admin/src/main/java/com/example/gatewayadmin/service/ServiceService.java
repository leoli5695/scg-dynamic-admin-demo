package com.example.gatewayadmin.service;

import com.example.gatewayadmin.center.ConfigCenterService;
import com.example.gatewayadmin.model.RouteEntity;
import com.example.gatewayadmin.model.ServiceDefinition;
import com.example.gatewayadmin.model.ServiceEntity;
import com.example.gatewayadmin.repository.RouteRepository;
import com.example.gatewayadmin.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service configuration management service with per-service incremental format.
 * Each service is stored in Nacos as an independent key: config.gateway.services.service-{serviceName}
 *
 * @author leoli
 */
@Slf4j
@Service
public class ServiceService {

  private static final String SERVICE_PREFIX = "config.gateway.services-";
  private static final String SERVICES_INDEX = "config.gateway.metadata.services-index";
  private static final String GROUP = "DEFAULT_GROUP";

  @Autowired
  private ConfigCenterService configCenterService;

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private RouteRepository routeRepository;

  @Autowired
  private ObjectMapper objectMapper;

  // Local cache: serviceName -> ServiceDefinition
  private final ConcurrentHashMap<String, ServiceDefinition> serviceCache = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    // Load services from database to cache
    loadServicesFromDatabase();
    // Rebuild services index in Nacos
    rebuildServicesIndex();
    log.info("ServiceService initialized with per-service incremental format");
  }

  /**
   * Get all services from cache.
   */
  public List<ServiceDefinition> getAllServices() {
    return new ArrayList<>(serviceCache.values());
  }

  /**
   * Get service by name.
   */
  public ServiceDefinition getServiceByName(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    return serviceCache.get(name);
  }

  /**
   * Create service with dual-write to database and Nacos (per-service format).
   */
  @Transactional(rollbackFor = Exception.class)
  public ServiceEntity createService(ServiceDefinition service) {
    if (service == null || service.getName() == null || service.getName().isEmpty()) {
      throw new IllegalArgumentException("Invalid service definition");
    }

    String serviceName = service.getName();
    
    if (serviceCache.containsKey(serviceName)) {
      throw new IllegalArgumentException("Service already exists: " + serviceName);
    }

    // 1. Convert to entity and save to H2 database
    log.info("Saving service to database: {}", serviceName);
    ServiceEntity entity = toEntity(service);
    entity.setServiceName(serviceName);
    entity.setServiceId(java.util.UUID.randomUUID().toString());
    entity = serviceRepository.save(entity);
    
    log.info("Service saved with DB id={}, service_name={}", entity.getId(), entity.getServiceName());

    // 2. Update memory cache
    serviceCache.put(serviceName, service);
    log.info("Service cached in memory: {}", serviceName);

    // 3. Push to Nacos (per-service format using service_id UUID)
    String serviceDataId = SERVICE_PREFIX + entity.getServiceId();
    configCenterService.publishConfig(serviceDataId, service);
    log.info("Service pushed to Nacos: {}", serviceDataId);

    // 4. Update services index
    rebuildServicesIndex();
    
    log.info("Service created successfully: {} (Database + Cache + Nacos)", serviceName);
    return entity;
  }

  /**
   * Update service with dual-write to database and Nacos (per-service format).
   */
  @Transactional(rollbackFor = Exception.class)
  public ServiceEntity updateService(String serviceName, ServiceDefinition service) {
    if (service == null || serviceName == null || serviceName.isEmpty()) {
      throw new IllegalArgumentException("Invalid service name or definition");
    }

    // Find entity by serviceName
    ServiceEntity entity = serviceRepository.findByServiceName(serviceName);
    if (entity == null) {
      throw new IllegalArgumentException("Service not found: " + serviceName);
    }
    
    // 1. Update database fields
    log.info("Updating service in database: {}", serviceName);
    // entity.setName(...) - removed, name field deleted
    entity = serviceRepository.save(entity);

    // 2. Update memory cache
    serviceCache.put(serviceName, service);
    log.info("Service updated in cache: {}", serviceName);

    // 3. Push to Nacos (overwrite per-service key using service_id UUID)
    String serviceDataId = SERVICE_PREFIX + entity.getServiceId();
    
    // First remove old config (in case it was double-serialized), then publish new one
    configCenterService.removeConfig(serviceDataId);
    log.info("Removed old config from Nacos: {}", serviceDataId);
    
    configCenterService.publishConfig(serviceDataId, service);  // ✅ Pass object directly
    log.info("Service updated in Nacos: {}", serviceDataId);

    log.info("Service updated successfully: {} (Database + Cache + Nacos)", serviceName);
    return entity;
  }

  /**
   * Check if service is referenced by any route.
   * @return list of route names that reference this service
   */
  public List<String> checkServiceUsage(String serviceName) {
    // Query all routes from database
    List<RouteEntity> routes = routeRepository.findAll();
    
    // Find routes that reference this service
    return routes.stream()
        .filter(route -> {
          // Check if route's metadata contains this service name
          String metadata = route.getMetadata();
          if (metadata == null || metadata.isEmpty()) {
            return false;
          }
          
          try {
            // Parse metadata JSON and check uri field
            JsonNode rootNode = objectMapper.readTree(metadata);
            String uri = rootNode.path("uri").asText();
            
            // Check if URI references this service (lb://serviceName or static://serviceName)
            return uri != null && (uri.contains("lb://" + serviceName) || uri.contains("static://" + serviceName));
          } catch (Exception e) {
            log.warn("Failed to parse route metadata: {}", metadata, e);
            return false;
          }
        })
        .map(RouteEntity::getRouteName)
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * Delete service from database and Nacos (per-service format).
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteService(String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) {
      throw new IllegalArgumentException("Invalid service name");
    }

    ServiceEntity entity = serviceRepository.findByServiceName(serviceName);
    if (entity == null) {
      throw new IllegalArgumentException("Service not found: " + serviceName);
    }
    
    log.info("Deleting service: {} (DB id: {})", serviceName, entity.getId());

    // 1. Remove from cache first
    serviceCache.remove(serviceName);
    log.info("Service removed from cache: {}", serviceName);

    // 2. Delete from Nacos (remove config using service_id UUID)
    String serviceDataId = SERVICE_PREFIX + entity.getServiceId();
    configCenterService.removeConfig(serviceDataId);
    log.info("Service removed from Nacos: {}", serviceDataId);

    // 3. Delete from database
    serviceRepository.delete(entity);
    log.info("Service deleted from database: {}", entity.getId());

    // 4. Update services index
    rebuildServicesIndex();
    
    log.info("Service deleted successfully: {} (Database + Cache + Nacos)", serviceName);
  }

  /**
   * Load all services from database to cache.
   */
  private void loadServicesFromDatabase() {
    log.info("Loading services from database...");
    List<ServiceEntity> entities = serviceRepository.findAll();
    for (ServiceEntity entity : entities) {
      try {
        ServiceDefinition service = toDefinition(entity);
        serviceCache.put(entity.getServiceName(), service);
        log.debug("Loaded service: {}", entity.getServiceName());
      } catch (Exception e) {
        log.error("Failed to convert service entity: {}", entity.getId(), e);
      }
    }
    log.info("Loaded {} services from database", entities.size());
  }

  /**
   * Rebuild services index from database.
   */
  private void rebuildServicesIndex() {
    try {
      // Use service_id (UUID) instead of serviceName for consistency
      List<String> serviceIds = serviceRepository.findAll().stream()
          .map(ServiceEntity::getServiceId)  // Use serviceId, not serviceName
          .collect(Collectors.toList());
      
      // Publish as JSON array directly, not stringified JSON
      configCenterService.publishConfig(SERVICES_INDEX, serviceIds);
      log.debug("Services index rebuilt with {} services", serviceIds.size());
    } catch (Exception e) {
      log.error("Failed to rebuild services index", e);
    }
  }

  /**
   * Convert ServiceDefinition to ServiceEntity.
   * Stores complete configuration as JSON for backup purposes.
   */
  private ServiceEntity toEntity(ServiceDefinition service) {
    ServiceEntity entity = new ServiceEntity();
    // Don't set name field, use service_name instead
    // entity.setName(...) - removed
    
    // Store complete configuration as JSON in metadata field for backup
    try {
      String configJson = objectMapper.writeValueAsString(service);
      entity.setMetadata(configJson);
    } catch (Exception e) {
      log.warn("Failed to serialize service config to JSON", e);
    }
    
    return entity;
  }

  /**
   * Convert ServiceEntity to ServiceDefinition.
   * Restores complete configuration from JSON backup.
   */
  private ServiceDefinition toDefinition(ServiceEntity entity) {
    // Try to restore from JSON backup first
    if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
      try {
        ServiceDefinition service = objectMapper.readValue(entity.getMetadata(), ServiceDefinition.class);
        if (service != null) {
          return service;
        }
      } catch (Exception e) {
        log.warn("Failed to deserialize service config from JSON, using fallback", e);
      }
    }
    
    // Fallback: create minimal definition
    ServiceDefinition service = new ServiceDefinition();
    service.setName(entity.getServiceName());
    return service;
  }

  /**
   * Convert object to JSON string.
   */
  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.error("Error converting object to JSON", e);
      return null;
    }
  }
}
