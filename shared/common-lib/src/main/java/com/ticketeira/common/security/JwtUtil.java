package com.ticketeira.common.security;

import com.ticketeira.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class JwtUtil {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_VERIFICADO = "verificado";

    private final SecretKey key;
    private final Duration expiration;
    private final String issuer;

    public JwtUtil(String secret, long expirationMs) {
        this(secret, expirationMs, "ticketeira");
    }

    public JwtUtil(String secret, long expirationMs, String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret deve ter pelo menos 32 bytes (HS256).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMillis(expirationMs);
        this.issuer = issuer;
    }

    public String generateToken(Long userId, String email, boolean verificado) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_VERIFICADO, verificado)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get(CLAIM_EMAIL, String.class);
            Boolean verificado = claims.get(CLAIM_VERIFICADO, Boolean.class);

            return new AuthenticatedUser(userId, email, Boolean.TRUE.equals(verificado));
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token expirado.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Token invalido.");
        }
    }

    public long getExpirationMs() {
        return expiration.toMillis();
    }
}
