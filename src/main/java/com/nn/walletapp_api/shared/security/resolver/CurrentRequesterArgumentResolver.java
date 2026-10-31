package com.nn.walletapp_api.shared.security.resolver;

import com.nn.walletapp_api.shared.security.annotation.CurrentRequester;
import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class CurrentRequesterArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentRequester.class)
                && RequesterContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return Optional.ofNullable(authentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(this::mapToRequesterContext)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Valid JWT authentication token required to resolve RequesterContext"));
    }

    private RequesterContext mapToRequesterContext(JwtAuthenticationToken jwtToken) {
        String subject = jwtToken.getToken().getSubject();
        UUID userId = parseUuid(subject);
        AccessLevel accessLevel = resolveAccessLevel(jwtToken.getAuthorities());
        return new RequesterContext(userId, accessLevel);
    }

    private UUID parseUuid(String subject) {
        return Optional.ofNullable(subject)
                .map(s -> {
                    try {
                        return UUID.fromString(s);
                    } catch (IllegalArgumentException e) {
                        throw new AuthenticationCredentialsNotFoundException(
                                "JWT subject claim is not a valid UUID: " + s, e);
                    }
                })
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "JWT subject claim ('sub') is missing"));
    }

    private AccessLevel resolveAccessLevel(Collection<GrantedAuthority> authorities) {
        boolean isAdmin = Optional.ofNullable(authorities)
                .orElseGet(java.util.Collections::emptyList)
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);

        return isAdmin ? AccessLevel.ADMIN : AccessLevel.STANDARD;
    }
}
