package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for authentication handlers using Bill Pugh Singleton pattern.
 * This factory maintains a registry of auth handlers and returns
 * the appropriate handler based on the authentication type.
 */
public class AuthHandlerFactory {

    private final Map<LyshraOpenAppApiAuthType, IAuthHandler> handlers;

    private AuthHandlerFactory() {
        handlers = new EnumMap<>(LyshraOpenAppApiAuthType.class);
        registerHandlers();
    }

    /**
     * Bill Pugh Singleton pattern - thread-safe lazy initialization.
     */
    private static final class SingletonHelper {
        private static final AuthHandlerFactory INSTANCE = new AuthHandlerFactory();
    }

    /**
     * Gets the singleton instance of the factory.
     *
     * @return the singleton instance
     */
    public static AuthHandlerFactory getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Register all available authentication handlers.
     */
    private void registerHandlers() {
        registerHandler(new NoAuthHandler());
        registerHandler(new ApiKeyAuthHandler());
        registerHandler(new BearerTokenAuthHandler());
        registerHandler(new BasicAuthHandler());
        registerHandler(new DigestAuthHandler());
        registerHandler(new OAuth1AuthHandler());
        registerHandler(new OAuth2AuthHandler());
        registerHandler(new HawkAuthHandler());
        registerHandler(new AwsSignatureAuthHandler());
        registerHandler(new NtlmAuthHandler());
        registerHandler(new AkamaiEdgeGridAuthHandler());
    }

    /**
     * Registers an authentication handler with the factory.
     *
     * @param handler the handler to register
     */
    public void registerHandler(IAuthHandler handler) {
        handlers.put(handler.getAuthType(), handler);
    }

    /**
     * Gets the appropriate auth handler for the given auth type.
     *
     * @param authType the authentication type
     * @return an Optional containing the handler if found, empty otherwise
     */
    public Optional<IAuthHandler> getHandler(LyshraOpenAppApiAuthType authType) {
        if (authType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(authType));
    }

    /**
     * Gets the auth handler for the given auth type, throwing an exception if not found.
     *
     * @param authType the authentication type
     * @return the handler
     * @throws IllegalArgumentException if no handler is found for the auth type
     */
    public IAuthHandler getHandlerOrThrow(LyshraOpenAppApiAuthType authType) {
        return getHandler(authType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No auth handler found for type: " + authType));
    }

    /**
     * Checks if a handler exists for the given auth type.
     *
     * @param authType the authentication type
     * @return true if a handler exists, false otherwise
     */
    public boolean hasHandler(LyshraOpenAppApiAuthType authType) {
        if (authType == null) {
            return false;
        }
        return handlers.containsKey(authType);
    }

    /**
     * Gets all registered auth types.
     *
     * @return an array of registered auth types
     */
    public LyshraOpenAppApiAuthType[] getRegisteredAuthTypes() {
        return handlers.keySet().toArray(new LyshraOpenAppApiAuthType[0]);
    }
}
