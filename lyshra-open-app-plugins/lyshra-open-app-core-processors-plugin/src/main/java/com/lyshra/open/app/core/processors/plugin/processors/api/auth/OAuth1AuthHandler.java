package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.OAuth1AuthConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Auth handler for OAuth 1.0 authentication.
 * Uses consumer key, consumer secret, access token, and token secret.
 */
public class OAuth1AuthHandler implements IAuthHandler {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.OAUTH1;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (consumerKey, consumerSecret) are handled by Jakarta annotations
        if (!(config instanceof OAuth1AuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for OAuth 1.0 authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        OAuth1AuthConfig oauth1Config = (OAuth1AuthConfig) config;

        try {
            String timestamp = oauth1Config.getTimestamp() != null ?
                    oauth1Config.getTimestamp() : String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = oauth1Config.getNonce() != null ?
                    oauth1Config.getNonce() : generateNonce();

            Map<String, String> oauthParams = new TreeMap<>();
            oauthParams.put("oauth_consumer_key", oauth1Config.getConsumerKey());
            oauthParams.put("oauth_signature_method", getSignatureMethodString(oauth1Config.getSignatureMethod()));
            oauthParams.put("oauth_timestamp", timestamp);
            oauthParams.put("oauth_nonce", nonce);
            oauthParams.put("oauth_version", oauth1Config.getVersion() != null ? oauth1Config.getVersion() : "1.0");

            if (oauth1Config.getAccessToken() != null && !oauth1Config.getAccessToken().isBlank()) {
                oauthParams.put("oauth_token", oauth1Config.getAccessToken());
            }

            // Add query params to signature base string
            Map<String, String> allParams = new TreeMap<>(oauthParams);
            if (context.getQueryParams() != null) {
                allParams.putAll(context.getQueryParams());
            }

            // Generate signature
            URI uri = URI.create(context.getUrl());
            String baseUrl = uri.getScheme() + "://" + uri.getHost() +
                    (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443 ? ":" + uri.getPort() : "") +
                    uri.getPath();

            String paramString = buildParamString(allParams);
            String signatureBaseString = context.getMethod().name() + "&" +
                    urlEncode(baseUrl) + "&" +
                    urlEncode(paramString);

            String signingKey = urlEncode(oauth1Config.getConsumerSecret()) + "&" +
                    (oauth1Config.getTokenSecret() != null ? urlEncode(oauth1Config.getTokenSecret()) : "");

            String signature = generateSignature(signatureBaseString, signingKey, oauth1Config.getSignatureMethod());
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            StringBuilder authHeader = new StringBuilder("OAuth ");
            if (oauth1Config.getRealm() != null && !oauth1Config.getRealm().isBlank()) {
                authHeader.append("realm=\"").append(oauth1Config.getRealm()).append("\", ");
            }

            boolean first = true;
            for (Map.Entry<String, String> entry : oauthParams.entrySet()) {
                if (!first) {
                    authHeader.append(", ");
                }
                authHeader.append(urlEncode(entry.getKey()))
                        .append("=\"")
                        .append(urlEncode(entry.getValue()))
                        .append("\"");
                first = false;
            }

            return requestSpec.header(HttpHeaders.AUTHORIZATION, authHeader.toString());

        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Failed to generate OAuth 1.0 signature: " + e.getMessage())
            );
        }
    }

    private String getSignatureMethodString(OAuth1AuthConfig.OAuth1SignatureMethod method) {
        if (method == null) {
            return "HMAC-SHA1";
        }
        return switch (method) {
            case HMAC_SHA1 -> "HMAC-SHA1";
            case HMAC_SHA256 -> "HMAC-SHA256";
            case RSA_SHA1 -> "RSA-SHA1";
            case PLAINTEXT -> "PLAINTEXT";
        };
    }

    private String generateSignature(String baseString, String signingKey,
                                     OAuth1AuthConfig.OAuth1SignatureMethod method) throws Exception {
        if (method == OAuth1AuthConfig.OAuth1SignatureMethod.PLAINTEXT) {
            return signingKey;
        }

        String algorithm = method == OAuth1AuthConfig.OAuth1SignatureMethod.HMAC_SHA256 ?
                "HmacSHA256" : "HmacSHA1";

        SecretKeySpec keySpec = new SecretKeySpec(
                signingKey.getBytes(StandardCharsets.UTF_8), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(keySpec);
        byte[] result = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(result);
    }

    private String buildParamString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String generateNonce() {
        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : nonceBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
