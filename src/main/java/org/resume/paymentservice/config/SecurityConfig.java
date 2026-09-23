package org.resume.paymentservice.config;

import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.exception.SecurityExceptionHandler;
import org.resume.paymentservice.security.JwtTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.resume.paymentservice.contants.ApiPaths.API_V1;
import static org.resume.paymentservice.contants.SecurityConstants.*;
import static org.resume.paymentservice.model.enums.Roles.*;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(API_V1 + "/payments/**").hasAuthority(ROLE_USER.getAuthority())
                        .requestMatchers(API_V1 + "/cards/**").hasAuthority(ROLE_USER.getAuthority())
                        .requestMatchers(API_V1 + "/subscriptions/**").hasAuthority(ROLE_USER.getAuthority())
                        .requestMatchers(API_V1 + "/support/**").hasAuthority(ROLE_EMPLOYEE.getAuthority())
                        .requestMatchers(API_V1 + "/staff/**")
                        .hasAnyAuthority(ROLE_EMPLOYEE.getAuthority(), ROLE_ADMIN.getAuthority())
                        .requestMatchers(API_V1 + "/admin/**").hasAuthority(ROLE_ADMIN.getAuthority())
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}