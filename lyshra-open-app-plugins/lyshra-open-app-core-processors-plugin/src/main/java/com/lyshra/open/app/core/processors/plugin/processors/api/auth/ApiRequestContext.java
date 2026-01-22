package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpMethod;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Context object containing information about the API request.
 * This is used by auth handlers that need request-specific information
 * to generate authentication (e.g., OAuth1, Hawk, AWS Signature).
 */
@Data
@Builder
public class ApiRequestContext {

    /**
     * The full URL of the request (including base URL and path).
     */
    private String url;

    /**
     * The HTTP method of the request.
     */
    private LyshraOpenAppHttpMethod method;

    /**
     * The request headers.
     */
    private Map<String, String> headers;

    /**
     * The request body (can be null for GET requests).
     */
    private Object body;

    /**
     * The request body as a string (for signature calculations).
     */
    private String bodyAsString;

    /**
     * The query parameters.
     */
    private Map<String, String> queryParams;

    /**
     * The content type of the request body.
     */
    private String contentType;

    /**
     * Timestamp for the request (used by some auth mechanisms).
     */
    private long timestamp;

    /**
     * Nonce for the request (used by some auth mechanisms).
     */
    private String nonce;
}
