package org.resume.paymentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.resume.paymentservice.contants.SecurityConstants.PUBLIC_PATHS;
import static org.resume.paymentservice.utils.ErrorMessages.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final JwtBlacklistService jwtBlacklistService;
    private final JwtCookeService jwtCookeService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Arrays.stream(PUBLIC_PATHS).anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = jwtCookeService.extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tokenId = jwtService.extractTokenId(token);

            if (jwtBlacklistService.isBlacklisted(tokenId)) {
                sendError(response, TOKEN_REVOKED);
                return;
            }

            String subject = jwtService.extractSubject(token);
            String authority = jwtService.extractRole(token).getAuthority();
            setAuthentication(subject, authority);

        } catch (ExpiredJwtException e) {
            sendError(response, TOKEN_EXPIRED);
            return;

        } catch (Exception e) {
            sendError(response, TOKEN_INVALID);
            log.warn("Token validation failed: {}", e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(String subject, String authority) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        subject,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(authority))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated: subject={}, authority={}", subject, authority);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message);
        CommonResponse<?> errorResponse = CommonResponse.error(problemDetail);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
