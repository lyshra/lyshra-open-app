package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.NtlmAuthConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Auth handler for NTLM Authentication.
 * Note: Full NTLM authentication is a challenge-response protocol that requires
 * multiple round trips. This implementation provides basic support.
 * For full NTLM support, consider using a specialized HTTP client.
 */
public class NtlmAuthHandler implements IAuthHandler {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.NTLM;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (username, password) are handled by Jakarta annotations
        if (!(config instanceof NtlmAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for NTLM authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        // Note: Full NTLM is a multi-step challenge-response protocol.
        // WebClient doesn't natively support NTLM.
        // For production use, consider:
        // 1. Using Apache HttpClient with NTLM support
        // 2. Using a specialized NTLM library
        // 3. Using Reactor Netty with custom handlers

        throw new LyshraOpenAppProcessorRuntimeException(
                ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                Map.of("message", "NTLM authentication requires a specialized HTTP client. " +
                        "Please use an HTTP client that supports NTLM challenge-response protocol.")
        );
    }
}
