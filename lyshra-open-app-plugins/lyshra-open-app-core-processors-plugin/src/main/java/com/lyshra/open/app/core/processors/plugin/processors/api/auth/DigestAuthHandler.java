package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.DigestAuthConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Auth handler for Digest Authentication.
 * A more secure alternative to Basic Auth that doesn't send passwords in plain text.
 */
public class DigestAuthHandler implements IAuthHandler {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.DIGEST_AUTH;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (username, password) are handled by Jakarta annotations
        if (!(config instanceof DigestAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for Digest authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        DigestAuthConfig digestConfig = (DigestAuthConfig) config;

        String realm = digestConfig.getRealm() != null ? digestConfig.getRealm() : "";
        String nonce = digestConfig.getNonce() != null ? digestConfig.getNonce() : generateNonce();
        String algorithm = digestConfig.getAlgorithm() != null ? digestConfig.getAlgorithm() : "MD5";
        String qop = digestConfig.getQop();
        String nc = digestConfig.getNonceCount() != null ? digestConfig.getNonceCount() : "00000001";
        String cnonce = digestConfig.getClientNonce() != null ? digestConfig.getClientNonce() : generateNonce();
        String opaque = digestConfig.getOpaque();

        try {
            URI uri = URI.create(context.getUrl());
            String digestUri = uri.getPath();
            if (uri.getQuery() != null) {
                digestUri += "?" + uri.getQuery();
            }

            String ha1 = md5Hash(digestConfig.getUsername() + ":" + realm + ":" + digestConfig.getPassword(), algorithm);
            String ha2 = md5Hash(context.getMethod().name() + ":" + digestUri, algorithm);

            String response;
            if (qop != null && !qop.isEmpty()) {
                response = md5Hash(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2, algorithm);
            } else {
                response = md5Hash(ha1 + ":" + nonce + ":" + ha2, algorithm);
            }

            StringBuilder authHeader = new StringBuilder();
            authHeader.append("Digest ");
            authHeader.append("username=\"").append(digestConfig.getUsername()).append("\"");
            authHeader.append(", realm=\"").append(realm).append("\"");
            authHeader.append(", nonce=\"").append(nonce).append("\"");
            authHeader.append(", uri=\"").append(digestUri).append("\"");
            if (qop != null && !qop.isEmpty()) {
                authHeader.append(", qop=").append(qop);
                authHeader.append(", nc=").append(nc);
                authHeader.append(", cnonce=\"").append(cnonce).append("\"");
            }
            authHeader.append(", response=\"").append(response).append("\"");
            if (opaque != null) {
                authHeader.append(", opaque=\"").append(opaque).append("\"");
            }
            authHeader.append(", algorithm=").append(algorithm);

            return requestSpec.header(HttpHeaders.AUTHORIZATION, authHeader.toString());

        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Failed to generate Digest authentication: " + e.getMessage())
            );
        }
    }

    private String md5Hash(String input, String algorithm) throws NoSuchAlgorithmException {
        String digestAlgorithm = algorithm.toUpperCase().replace("-SESS", "");
        if ("MD5".equals(digestAlgorithm)) {
            digestAlgorithm = "MD5";
        } else if ("SHA-256".equals(digestAlgorithm) || "SHA256".equals(digestAlgorithm)) {
            digestAlgorithm = "SHA-256";
        }

        MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
