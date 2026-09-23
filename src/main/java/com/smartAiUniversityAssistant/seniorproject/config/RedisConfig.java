package com.smartAiUniversityAssistant.seniorproject.config;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
@Configuration(proxyBeanMethods=false)
public class RedisConfig {
    @Bean public org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer authenticationRedisOptions() {
        return builder -> builder.autoReconnect(false).requestQueueSize(256)
                .disconnectedBehavior(io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
    @Bean public static org.springframework.beans.factory.config.BeanPostProcessor redisConnectionValidation() {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean,String name) {
                if (bean instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory)
                    factory.setValidateConnection(true);
                return bean;
            }
        };
    }
    @Bean public DefaultRedisScript<Long> createSessionScript() {
        return script("create-session.lua",Long.class);
    }
    @Bean public DefaultRedisScript<String> rotateRefreshScript() {
        return script("rotate-refresh.lua",String.class);
    }
    @Bean public DefaultRedisScript<java.util.List> readSessionScript() {
        return script("read-session.lua",java.util.List.class);
    }
    @Bean public DefaultRedisScript<Long> throttleScript() {
        return script("throttle.lua",Long.class);
    }
    private <T> DefaultRedisScript<T> script(String name,Class<T> type) {
        var script=new DefaultRedisScript<T>();
        script.setLocation(new ClassPathResource("redis/auth/"+name)); script.setResultType(type); return script;
    }
}
