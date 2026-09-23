package com.smartAiUniversityAssistant.seniorproject.security;
import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
@Component
public class AuthenticationThrottle {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> throttleScript;
    private final AuthenticationProperties properties;
    public AuthenticationThrottle(StringRedisTemplate redis,DefaultRedisScript<Long> throttleScript,AuthenticationProperties properties) {
        this.redis=redis; this.throttleScript=throttleScript; this.properties=properties;
    }
    public void check(String kind,String identifier,int limit) {
        try {
            Long allowed=redis.execute(throttleScript,List.of(properties.session().keyPrefix()+":throttle:"+kind+":"+TokenSupport.digest(identifier)),Integer.toString(limit));
            if (allowed==null || allowed<0) throw new AuthenticationInfrastructureException();
            if (allowed==0) throw new AuthenticationFailure(429,"TOO_MANY_REQUESTS","Too many requests. Try again later.");
        } catch (DataAccessException e) { throw new AuthenticationInfrastructureException(); }
    }
}
