package com.nn.walletapp_api.shared.config;

import com.nn.walletapp_api.shared.security.ratelimit.RateLimitInterceptor;
import com.nn.walletapp_api.shared.security.ratelimit.RateLimitingService;
import com.nn.walletapp_api.shared.security.ratelimit.config.RateLimitProperties;
import com.nn.walletapp_api.shared.security.resolver.CurrentRequesterArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentRequesterArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimitingService, objectMapper, clock))
                .addPathPatterns("/api/**");
    }
}
