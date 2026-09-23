package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.ApiErrorWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.*;

@Configuration(proxyBeanMethods=false) @EnableMethodSecurity
public class SecurityConfig {
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationConverter converter,
            RestAuthenticationEntryPoint entry,RestAccessDeniedHandler denied,AuthenticationThrottle throttle,
            AuthenticationProperties properties,ApiErrorWriter errors,MeterRegistry metrics,PasswordChangeAuthorizationManager restricted) throws Exception {
        var resolver=new DefaultBearerTokenResolver();
        http.csrf(c -> c.disable()).cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable()).requestCache(c -> c.disable())
                .exceptionHandling(c -> c.authenticationEntryPoint(entry).accessDeniedHandler(denied))
                .oauth2ResourceServer(c -> c.authenticationEntryPoint(entry).accessDeniedHandler(denied)
                        .bearerTokenResolver(req -> isPublic(req.getMethod(),req.getRequestURI().substring(req.getContextPath().length()))?null:resolver.resolve(req))
                        .jwt(j -> j.jwtAuthenticationConverter(converter)))
                .authorizeHttpRequests(c -> c
                        .requestMatchers(HttpMethod.POST,"/api/v1/admin/auth/login","/api/v1/admin/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET,"/actuator/health/liveness","/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/admin/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/v1/admin/auth/logout","/api/v1/admin/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/admin/users","/api/v1/admin/users/**").access((auth,context) -> {
                            var current=auth.get();
                            restricted.requireFull(current);
                            return new AuthorizationDecision(current.getAuthorities().stream()
                                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN")));
                        })
                        .anyRequest().access((auth,context) -> { restricted.requireFull(auth.get()); return new AuthorizationDecision(false); }))
                .addFilterBefore(new AuthenticationRequestFilter(throttle,properties,errors,metrics),SecurityContextHolderFilter.class);
        return http.build();
    }
    private boolean isPublic(String method,String path) {
        return method.equals("POST") && (path.equals("/api/v1/admin/auth/login") || path.equals("/api/v1/admin/auth/refresh"));
    }
    private CorsConfigurationSource cors(AuthenticationProperties properties) {
        var c=new CorsConfiguration(); c.setAllowedOrigins(properties.cors().allowedOrigins());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type"));
        c.setAllowCredentials(false); c.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
    }
    @Bean public org.springframework.web.filter.CorsFilter corsFilter(AuthenticationProperties properties,ApiErrorWriter errors) {
        var filter=new org.springframework.web.filter.CorsFilter(cors(properties));
        var processor=new DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            }
        };
        filter.setCorsProcessor((configuration,request,response) -> {
            boolean allowed=processor.processRequest(configuration,request,response);
            if(!allowed) errors.write(request,response,403,"FORBIDDEN","Origin or CORS request is not allowed.");
            return allowed;
        });
        return filter;
    }
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> corsRegistration(
            org.springframework.web.filter.CorsFilter corsFilter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false); return registration;
    }
    // Central restriction also runs before method authorization on future business services.
    @Bean @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor forcedChangeBusinessServiceAdvisor() {
        var pointcut=new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method,Class<?> target) {
                String name=target.getPackageName();
                return name.startsWith("com.smartAiUniversityAssistant.seniorproject.feature.") && name.contains(".service")
                        && !name.startsWith("com.smartAiUniversityAssistant.seniorproject.feature.authentication.");
            }
        };
        var advisor=new DefaultPointcutAdvisor(pointcut,(MethodInterceptor) invocation -> {
            new PasswordChangeAuthorizationManager().requireFull(SecurityContextHolder.getContext().getAuthentication());
            return invocation.proceed();
        });
        advisor.setOrder(50); return advisor;
    }
}
