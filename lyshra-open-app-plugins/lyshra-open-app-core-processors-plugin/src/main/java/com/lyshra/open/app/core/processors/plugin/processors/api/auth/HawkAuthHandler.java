package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.HawkAuthConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Auth handler for Hawk Authentication.
 * A HTTP authentication scheme using a message authentication code (MAC).
 */
public class HawkAuthHandler implements IAuthHandler {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.HAWK;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (hawkAuthId, hawkAuthKey, algorithm) are handled by Jakarta annotations
        if (!(config instanceof HawkAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for Hawk authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        HawkAuthConfig hawkConfig = (HawkAuthConfig) config;

        try {
            long timestamp = hawkConfig.getTimestamp() != null ?
                    hawkConfig.getTimestamp() : System.currentTimeMillis() / 1000;
            String nonce = hawkConfig.getNonce() != null ?
                    hawkConfig.getNonce() : generateNonce();

            URI uri = URI.create(context.getUrl());
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
            String resource = uri.getPath();
            if (uri.getQuery() != null) {
                resource += "?" + uri.getQuery();
            }

            // Generate payload hash if needed
            String payloadHash = "";
            if (hawkConfig.isIncludePayloadHash() && context.getBodyAsString() != null) {
                payloadHash = generatePayloadHash(
                        context.getBodyAsString(),
                        context.getContentType(),
                        hawkConfig.getAlgorithm()
                );
            }

            // Build normalized string
            StringBuilder normalized = new StringBuilder();
            normalized.append("hawk.1.header\n");
            normalized.append(timestamp).append("\n");
            normalized.append(nonce).append("\n");
            normalized.append(context.getMethod().name().toUpperCase()).append("\n");
            normalized.append(resource).append("\n");
            normalized.append(host.toLowerCase()).append("\n");
            normalized.append(port).append("\n");
            normalized.append(payloadHash).append("\n");
            if (hawkConfig.getExtraData() != null) {
                normalized.append(hawkConfig.getExtraData());
            }
            normalized.append("\n");

            // Calculate MAC
            String algorithm = hawkConfig.getAlgorithm() == HawkAuthConfig.HawkAlgorithm.SHA1 ?
                    "HmacSHA1" : "HmacSHA256";
            String mac = calculateMac(normalized.toString(), hawkConfig.getHawkAuthKey(), algorithm);

            // Build Authorization header
            StringBuilder authHeader = new StringBuilder();
            authHeader.append("Hawk id=\"").append(hawkConfig.getHawkAuthId()).append("\"");
            authHeader.append(", ts=\"").append(timestamp).append("\"");
            authHeader.append(", nonce=\"").append(nonce).append("\"");
            if (!payloadHash.isEmpty()) {
                authHeader.append(", hash=\"").append(payloadHash).append("\"");
            }
            if (hawkConfig.getExtraData() != null) {
                authHeader.append(", ext=\"").append(hawkConfig.getExtraData()).append("\"");
            }
            authHeader.append(", mac=\"").append(mac).append("\"");
            if (hawkConfig.getAppId() != null) {
                authHeader.append(", app=\"").append(hawkConfig.getAppId()).append("\"");
            }
            if (hawkConfig.getDelegation() != null) {
                authHeader.append(", dlg=\"").append(hawkConfig.getDelegation()).append("\"");
            }

            return requestSpec.header(HttpHeaders.AUTHORIZATION, authHeader.toString());

        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Failed to generate Hawk authentication: " + e.getMessage())
            );
        }
    }

    private String generatePayloadHash(String payload, String contentType,
                                       HawkAuthConfig.HawkAlgorithm algorithm) throws Exception {
        String normalizedPayload = "hawk.1.payload\n" +
                (contentType != null ? contentType : "") + "\n" +
                payload + "\n";

        String hashAlgorithm = algorithm == HawkAuthConfig.HawkAlgorithm.SHA1 ? "SHA-1" : "SHA-256";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance(hashAlgorithm);
        byte[] digest = md.digest(normalizedPayload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private String calculateMac(String data, String key, String algorithm) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(keySpec);
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(result);
    }

    private String generateNonce() {
        byte[] nonceBytes = new byte[8];
        RANDOM.nextBytes(nonceBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : nonceBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
