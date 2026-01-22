package com.lyshra.open.app.integration.enumerations;

/**
 * Enumeration of supported API authentication types.
 * These types align with the authentication mechanisms supported by Postman.
 */
public enum LyshraOpenAppApiAuthType {
    /**
     * No authentication required.
     */
    NO_AUTH,

    /**
     * API Key authentication.
     * The API key can be sent as a header or query parameter.
     */
    API_KEY,

    /**
     * Bearer Token authentication.
     * A token is sent in the Authorization header as "Bearer {token}".
     */
    BEARER_TOKEN,

    /**
     * Basic Authentication.
     * Username and password are base64 encoded and sent in the Authorization header.
     */
    BASIC_AUTH,

    /**
     * Digest Authentication.
     * A more secure alternative to Basic Auth that doesn't send passwords in plain text.
     */
    DIGEST_AUTH,

    /**
     * OAuth 1.0 authentication.
     * Uses consumer key, consumer secret, access token, and token secret.
     */
    OAUTH1,

    /**
     * OAuth 2.0 authentication.
     * Supports various grant types: Authorization Code, Client Credentials,
     * Password Credentials, and Implicit.
     */
    OAUTH2,

    /**
     * Hawk Authentication.
     * A HTTP authentication scheme using a message authentication code (MAC).
     */
    HAWK,

    /**
     * AWS Signature authentication.
     * Used for authenticating requests to AWS services.
     */
    AWS_SIGNATURE,

    /**
     * NTLM Authentication.
     * Windows NT LAN Manager authentication protocol.
     */
    NTLM,

    /**
     * Akamai EdgeGrid authentication.
     * Used for authenticating requests to Akamai APIs.
     */
    AKAMAI_EDGEGRID
}
