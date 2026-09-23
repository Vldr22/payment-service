package org.resume.paymentservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.model.enums.Roles;
import org.resume.paymentservice.properties.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.resume.paymentservice.contants.SecurityConstants.CLAIM_ROLE;

@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties jwtProperties) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecretKey());
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = jwtProperties.getExpirationSeconds();
    }

    public String generateToken(String subject, Roles role) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public long calculateTtl(String token) {
        long expMillis = extractExpiration(token).getTime();
        long nowMillis = System.currentTimeMillis();
        return Math.max((expMillis - nowMillis) / 1000, 0);
    }

    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    public Roles extractRole(String token) {
        String role = extractClaims(token).get(CLAIM_ROLE, String.class);
        return Roles.valueOf(role);
    }

    public String extractTokenId(String token) {
        return extractClaims(token).getId();
    }

    private Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
