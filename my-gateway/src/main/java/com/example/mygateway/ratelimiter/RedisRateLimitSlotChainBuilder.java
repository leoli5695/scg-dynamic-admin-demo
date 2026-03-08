package com.example.mygateway.ratelimiter;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.*;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowSlot;
import com.alibaba.csp.sentinel.slots.logger.LogSlot;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlot;
import com.alibaba.csp.sentinel.slots.system.SystemSlot;
import com.example.mygateway.model.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sentinel Slot Chain Builder with Redis Rate Limiting
 * <p>
 * Flow:
 * 1. RedisRateLimitSlot - Redis global rate limiting (priority)
 * 2. FlowSlot - Sentinel local rate limiting (fallback)
 * 
 * @author leoli
 */
@Component
public class RedisRateLimitSlotChainBuilder implements SlotChainBuilder {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitSlotChainBuilder.class);

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Autowired
    private RateLimiterConfigManager configManager;

    @Override
    public ProcessorSlotChain build() {
        DefaultProcessorSlotChain chain = new DefaultProcessorSlotChain();

        // Sentinel default slots
        chain.addLast(new NodeSelectorSlot());
        chain.addLast(new LogSlot());
        chain.addLast(new StatisticSlot());
        // Redis rate limiting slot (priority higher than FlowSlot)
        chain.addLast(new RedisGlobalRateLimitSlot(redisRateLimiter, configManager));
        // Sentinel local rate limiting (fallback)
        chain.addLast(new FlowSlot());
        chain.addLast(new DegradeSlot());
        chain.addLast(new SystemSlot());

        return chain;
    }

    /**
     * Redis Global Rate Limit Slot
     * Priority higher than FlowSlot
     * 
     * @author leoli
     */
    public static class RedisGlobalRateLimitSlot extends AbstractLinkedProcessorSlot {

        private final RedisRateLimiter redisRateLimiter;
        private final RateLimiterConfigManager configManager;

        public RedisGlobalRateLimitSlot(RedisRateLimiter redisRateLimiter,
                                        RateLimiterConfigManager configManager) {
            this.redisRateLimiter = redisRateLimiter;
            this.configManager = configManager;
        }

        @Override
        public void entry(Context context, ResourceWrapper resourceWrapper, Object o, int i, boolean b, Object... objects) throws Throwable {
            String routeId = context.getName();

            // Get rate limiter config
            RateLimiterConfig config = configManager.getRateLimiterConfig(routeId);

            if (config == null || !config.isEnabled()) {
                // No config, skip and let next Slot handle
                fireNext(context, resourceWrapper, o, i, b, objects);
                return;
            }

            // Priority: Redis global rate limiting
            if (redisRateLimiter.isRedisAvailable() && config.getRedisQps() > 0) {
                String rateLimitKey = buildKey(routeId, context, config);

                RedisRateLimiter.RateLimitResult result = redisRateLimiter.tryAcquire(
                        rateLimitKey, config.getRedisQps(), config.getRedisBurstCapacity());

                if (result.isAllowed()) {
                    // Redis allowed, pass to next Slot for statistics
                    context.setOrigin(rateLimitKey);
                    fireNext(context, resourceWrapper, o, i, b, objects);
                    return;
                } else if (!result.isFallback()) {
                    // Redis rejected, throw FlowException
                    log.warn("Redis rate limit rejected: {}", rateLimitKey);
                    throw new FlowException("Redis rate limit exceeded: " + rateLimitKey);
                }
                // Fallback to Sentinel
            }

            // Fallback to Sentinel
            log.debug("Fallback to Sentinel for route: {}", routeId);
            
            try {
                // Use SphU.entry for Sentinel rate limiting
                com.alibaba.csp.sentinel.Entry entry = com.alibaba.csp.sentinel.SphU.entry(routeId);
                // Pass to next Slot for statistics
                fireNext(context, resourceWrapper, o, i, b, objects);
            } catch (BlockException e) {
                // Sentinel rejected
                log.warn("Sentinel rate limit rejected for route: {}", routeId);
                throw e;
            } catch (Exception e) {
                // Other exceptions, log and pass through
                fireNext(context, resourceWrapper, o, i, b, objects);
            }
        }

        @Override
        public void exit(Context context, ResourceWrapper resourceWrapper, int i, Object... objects) {
            // Sentinel auto handles exit
        }

        private String buildKey(String routeId, Context context, RateLimiterConfig config) {
            StringBuilder key = new StringBuilder(config.getKeyPrefix()).append(routeId);

            String keyType = config.getKeyType();
            if ("ip".equalsIgnoreCase(keyType)) {
                // Try X-Forwarded-For or X-Real-IP
                String clientIp = extractClientIp(context);
                key.append(":").append(clientIp);
            } else if ("user".equalsIgnoreCase(keyType)) {
                String userId = context.getOrigin();
                key.append(":").append(userId != null ? userId : "anonymous");
            }
            // route: use only routeId

            return key.toString();
        }

        private String extractClientIp(Context context) {
            // Try to get origin from context
            String origin = context.getOrigin();
            if (origin != null && !origin.isEmpty()) {
                return origin;
            }
            return "unknown";
        }

        private void fireNext(Context context, ResourceWrapper resourceWrapper, Object o, int i, boolean b, Object... objects) throws Throwable {
            // Call next Slot
            if (this.getNext() != null) {
                this.getNext().entry(context, resourceWrapper, o, i, b, objects);
            }
        }
    }
}