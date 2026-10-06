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
                        .requestMatchers(HttpMethod.POST,STUDENT_PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.GET,"/actuator/health/liveness","/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/admin/auth/me").access((auth,context) -> admin(auth.get()))
                        .requestMatchers(HttpMethod.POST,"/api/v1/admin/auth/logout","/api/v1/admin/auth/change-password")
                                .access((auth,context) -> admin(auth.get()))
                        // A student who must change their password may still read /me, change it, and sign out.
                        .requestMatchers(HttpMethod.GET,"/api/v1/student/auth/me").access((auth,context) -> student(auth.get()))
                        .requestMatchers(HttpMethod.POST,"/api/v1/student/auth/logout","/api/v1/student/auth/change-password")
                                .access((auth,context) -> student(auth.get()))
                        .requestMatchers("/api/v1/student/**").access((auth,context) -> {
                            var current=auth.get();
                            if (current!=null && current.getPrincipal() instanceof AuthenticatedStudent s && s.forcePasswordChange())
                                throw new PasswordChangeRequiredException();
                            return student(current);
                        })
                        .requestMatchers("/api/v1/admin/users","/api/v1/admin/users/**").access((auth,context) -> {
                            var current=auth.get();
                            restricted.requireFull(current);
                            return new AuthorizationDecision(current.getAuthorities().stream()
                                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN")));
                        })
                        .requestMatchers("/api/v1/admin/faculties","/api/v1/admin/faculties/**",
                                "/api/v1/admin/departments","/api/v1/admin/departments/**",
                                "/api/v1/admin/programs","/api/v1/admin/programs/**",
                                "/api/v1/admin/semesters","/api/v1/admin/semesters/**",
                                "/api/v1/admin/lectures","/api/v1/admin/lectures/**",
                                "/api/v1/admin/courses","/api/v1/admin/courses/**",
                                "/api/v1/admin/course-sections","/api/v1/admin/course-sections/**",
                                "/api/v1/admin/students","/api/v1/admin/students/**").access((auth,context) -> {
                            var current=auth.get();
                            restricted.requireFull(current);
                            return new AuthorizationDecision(current.getAuthorities().stream().anyMatch(authority ->
                                    authority.getAuthority().equals("ROLE_SUPER_ADMIN")
                                    || authority.getAuthority().equals("ROLE_ADMIN")
                                    || authority.getAuthority().equals("ROLE_ACADEMIC_ADMIN")));
                        })
                        .anyRequest().access((auth,context) -> { restricted.requireFull(auth.get()); return new AuthorizationDecision(false); }))
                .addFilterBefore(new AuthenticationRequestFilter(throttle,properties,errors,metrics),SecurityContextHolderFilter.class);
        return http.build();
    }
    private static final String[] STUDENT_PUBLIC={"/api/v1/student/auth/register","/api/v1/student/auth/login","/api/v1/student/auth/refresh"};
    private boolean isPublic(String method,String path) {
        return method.equals("POST") && (path.equals("/api/v1/admin/auth/login") || path.equals("/api/v1/admin/auth/refresh")
                || java.util.Arrays.asList(STUDENT_PUBLIC).contains(path));
    }
    private static AuthorizationDecision admin(org.springframework.security.core.Authentication auth) {
        return new AuthorizationDecision(auth!=null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser);
    }
    private static AuthorizationDecision student(org.springframework.security.core.Authentication auth) {
        return new AuthorizationDecision(auth!=null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedStudent
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT")));
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
