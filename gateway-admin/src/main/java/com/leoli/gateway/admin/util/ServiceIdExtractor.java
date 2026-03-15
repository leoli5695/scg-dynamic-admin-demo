package com.leoli.gateway.admin.util;

/**
 * Utility class for extracting service ID from URIs.
 * Supports lb:// and static:// protocols.
 * 
 * @author leoli
 */
public class ServiceIdExtractor {
    
    /**
     * Extract service ID from URI.
     * 
     * @param uri The URI string (e.g., "lb://a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" or "static://service-id")
     * @return The extracted service ID, or null if not a valid protocol
     */
    public static String extract(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        
        // Support lb:// and static:// protocols
        if (uri.startsWith("lb://")) {
            return uri.substring(5).trim();
        } else if (uri.startsWith("static://")) {
            return uri.substring(9).trim();
        }
        
        return null;
    }
    
    /**
     * Build a URI with service ID.
     * 
     * @param protocol The protocol ("lb" or "static")
     * @param serviceId The service ID (UUID)
     * @return The complete URI (e.g., "lb://a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
     */
    public static String buildUri(String protocol, String serviceId) {
        if (protocol == null || serviceId == null) {
            return null;
        }
        
        return protocol + "://" + serviceId.trim();
    }
}
