package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.AkamaiEdgeGridAuthConfig;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Auth handler for Akamai EdgeGrid authentication.
 * Used for authenticating requests to Akamai APIs.
 */
public class AkamaiEdgeGridAuthHandler implements IAuthHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ssZ");

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.AKAMAI_EDGEGRID;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (clientToken, clientSecret, accessToken) are handled by Jakarta annotations
        if (!(config instanceof AkamaiEdgeGridAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for Akamai EdgeGrid authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        AkamaiEdgeGridAuthConfig akamaiConfig = (AkamaiEdgeGridAuthConfig) config;

        try {
            String timestamp = akamaiConfig.getTimestamp() != null ?
                    akamaiConfig.getTimestamp() :
                    ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FORMAT);

            String nonce = akamaiConfig.getNonce() != null ?
                    akamaiConfig.getNonce() : UUID.randomUUID().toString();

            URI uri = URI.create(context.getUrl());
            String path = uri.getPath();
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }

            // Content hash
            String contentHash = "";
            if (context.getBodyAsString() != null && !context.getBodyAsString().isEmpty()) {
                String bodyToHash = context.getBodyAsString();
                if (bodyToHash.length() > akamaiConfig.getMaxBodySize()) {
                    bodyToHash = bodyToHash.substring(0, akamaiConfig.getMaxBodySize());
                }
                contentHash = base64Sha256(bodyToHash);
            }

            // Build auth data
            String authData = "EG1-HMAC-SHA256 " +
                    "client_token=" + akamaiConfig.getClientToken() + ";" +
                    "access_token=" + akamaiConfig.getAccessToken() + ";" +
                    "timestamp=" + timestamp + ";" +
                    "nonce=" + nonce + ";";

            // Build data to sign
            StringBuilder dataToSign = new StringBuilder();
            dataToSign.append(context.getMethod().name().toUpperCase()).append("\t");
            dataToSign.append(uri.getScheme()).append("\t");
            dataToSign.append(uri.getHost()).append("\t");
            dataToSign.append(path).append("\t");

            // Headers to sign (if specified)
            if (akamaiConfig.getHeadersToSign() != null && !akamaiConfig.getHeadersToSign().isBlank()) {
                // Parse and include specified headers
                dataToSign.append(akamaiConfig.getHeadersToSign());
            }
            dataToSign.append("\t");
            dataToSign.append(contentHash).append("\t");
            dataToSign.append(authData);

            // Generate signature
            byte[] signingKey = hmacSha256(akamaiConfig.getClientSecret().getBytes(StandardCharsets.UTF_8), timestamp);
            String signature = base64HmacSha256(signingKey, dataToSign.toString());

            String authorization = authData + "signature=" + signature;

            return requestSpec.header("Authorization", authorization);

        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Failed to generate Akamai EdgeGrid signature: " + e.getMessage())
            );
        }
    }

    private String base64Sha256(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64HmacSha256(byte[] key, String data) throws Exception {
        return Base64.getEncoder().encodeToString(hmacSha256(key, data));
    }
}
