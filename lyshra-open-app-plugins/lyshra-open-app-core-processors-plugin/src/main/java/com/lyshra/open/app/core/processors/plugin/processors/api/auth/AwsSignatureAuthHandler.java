package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.AwsSignatureAuthConfig;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Auth handler for AWS Signature Version 4 authentication.
 * Used for authenticating requests to AWS services.
 */
public class AwsSignatureAuthHandler implements IAuthHandler {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.AWS_SIGNATURE;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (accessKey, secretKey, region, serviceName) are handled by Jakarta annotations
        if (!(config instanceof AwsSignatureAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for AWS Signature authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        AwsSignatureAuthConfig awsConfig = (AwsSignatureAuthConfig) config;

        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(ISO_DATE_TIME);
            String dateStamp = now.format(ISO_DATE);

            URI uri = URI.create(context.getUrl());
            String host = uri.getHost();
            String canonicalUri = uri.getPath().isEmpty() ? "/" : uri.getPath();

            // Canonical query string
            String canonicalQueryString = "";
            if (context.getQueryParams() != null && !context.getQueryParams().isEmpty()) {
                canonicalQueryString = context.getQueryParams().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                        .collect(Collectors.joining("&"));
            }

            // Payload hash
            String payloadHash = sha256Hash(context.getBodyAsString() != null ? context.getBodyAsString() : "");

            // Canonical headers
            TreeMap<String, String> headersToSign = new TreeMap<>();
            headersToSign.put("host", host);
            headersToSign.put("x-amz-date", amzDate);
            if (awsConfig.getSessionToken() != null && !awsConfig.getSessionToken().isBlank()) {
                headersToSign.put("x-amz-security-token", awsConfig.getSessionToken());
            }
            headersToSign.put("x-amz-content-sha256", payloadHash);

            String canonicalHeaders = headersToSign.entrySet().stream()
                    .map(e -> e.getKey().toLowerCase() + ":" + e.getValue().trim())
                    .collect(Collectors.joining("\n")) + "\n";

            String signedHeaders = String.join(";", headersToSign.keySet());

            // Canonical request
            String canonicalRequest = context.getMethod().name() + "\n" +
                    canonicalUri + "\n" +
                    canonicalQueryString + "\n" +
                    canonicalHeaders + "\n" +
                    signedHeaders + "\n" +
                    payloadHash;

            // String to sign
            String credentialScope = dateStamp + "/" + awsConfig.getRegion() + "/" +
                    awsConfig.getServiceName() + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" +
                    amzDate + "\n" +
                    credentialScope + "\n" +
                    sha256Hash(canonicalRequest);

            // Signing key
            byte[] kDate = hmacSha256(("AWS4" + awsConfig.getSecretKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
            byte[] kRegion = hmacSha256(kDate, awsConfig.getRegion());
            byte[] kService = hmacSha256(kRegion, awsConfig.getServiceName());
            byte[] kSigning = hmacSha256(kService, "aws4_request");

            // Signature
            String signature = bytesToHex(hmacSha256(kSigning, stringToSign));

            // Authorization header
            String authorization = "AWS4-HMAC-SHA256 Credential=" + awsConfig.getAccessKey() + "/" + credentialScope +
                    ", SignedHeaders=" + signedHeaders +
                    ", Signature=" + signature;

            WebClient.RequestHeadersSpec<?> spec = requestSpec
                    .header("Authorization", authorization)
                    .header("x-amz-date", amzDate)
                    .header("x-amz-content-sha256", payloadHash);

            if (awsConfig.getSessionToken() != null && !awsConfig.getSessionToken().isBlank()) {
                spec = spec.header("x-amz-security-token", awsConfig.getSessionToken());
            }

            return spec;

        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Failed to generate AWS Signature: " + e.getMessage())
            );
        }
    }

    private String sha256Hash(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
