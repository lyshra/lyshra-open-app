package com.lyshra.open.app.designer.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Service for JWT token generation and validation.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtService(
            @Value("${app.security.jwt.secret:default-secret-key-for-development-only-change-in-production}") String secret,
            @Value("${app.security.jwt.expiration-hours:24}") long expirationHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    /**
     * Generates a JWT token for a user.
     *
     * @param username the username
     * @param claims additional claims to include
     * @return the JWT token
     */
    public String generateToken(String username, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generates a JWT token for a user without additional claims.
     *
     * @param username the username
     * @return the JWT token
     */
    public String generateToken(String username) {
        return generateToken(username, Map.of());
    }

    /**
     * Extracts the username from a token.
     *
     * @param token the JWT token
     * @return Optional containing the username if valid
     */
    public Optional<String> extractUsername(String token) {
        try {
            return Optional.ofNullable(extractClaim(token, Claims::getSubject));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates a token for a given username.
     *
     * @param token the JWT token
     * @param username the expected username
     * @return true if the token is valid
     */
    public boolean validateToken(String token, String username) {
        return extractUsername(token)
                .map(extractedUsername -> extractedUsername.equals(username) && !isTokenExpired(token))
                .orElse(false);
    }

    /**
     * Checks if a token is valid without checking the username.
     *
     * @param token the JWT token
     * @return true if the token is valid and not expired
     */
    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration != null && expiration.before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
