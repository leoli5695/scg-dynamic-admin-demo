package com.example.gateway.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.HashMap;

/**
 * Custom header configuration for a route.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomHeaderConfig {

    /**
     * Headers to add to the request.
     * Key: header name, Value: header value (supports placeholders)
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * Whether this configuration is enabled.
     */
    private boolean enabled = true;

    public CustomHeaderConfig(Map<String, String> headers) {
        this.headers = headers;
    }
}
