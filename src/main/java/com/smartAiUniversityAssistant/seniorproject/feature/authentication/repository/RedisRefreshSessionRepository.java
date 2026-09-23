package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.RefreshSession;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshSessionRepository implements RefreshSessionRepository {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> createSessionScript;
    private final DefaultRedisScript<String> rotateRefreshScript;
    private final DefaultRedisScript<List> readSessionScript;
    private final AuthenticationProperties properties;
    private final Clock clock;
    public RedisRefreshSessionRepository(StringRedisTemplate redis, DefaultRedisScript<Long> createSessionScript,
            DefaultRedisScript<String> rotateRefreshScript, DefaultRedisScript<List> readSessionScript, AuthenticationProperties properties, Clock clock) {
        this.redis=redis; this.createSessionScript=createSessionScript; this.rotateRefreshScript=rotateRefreshScript;
        this.properties=properties; this.clock=clock; this.readSessionScript=readSessionScript;
    }
    public String key(long uid,String sid) {
        if (uid<=0 || !TokenSupport.canonical(sid,16)) throw new IllegalArgumentException("Invalid session identifiers");
        return properties.session().keyPrefix()+":session:{"+uid+"}:"+sid;
    }
    @Override public boolean create(RefreshSession s) {
        try {
            Long result=redis.execute(createSessionScript,List.of(key(s.userId(),s.sessionId())),
                    Long.toString(s.userId()),s.sessionId(),s.currentHash(),s.credentialRevision(),s.mode(),
                    Long.toString(s.createdAt().toEpochMilli()),Long.toString(s.absoluteExpiresAt().toEpochMilli()));
            if (result==null || result<0) throw new AuthenticationInfrastructureException();
            return result==1;
        } catch (DataAccessException e) { throw new AuthenticationInfrastructureException(); }
    }
    @Override public Optional<RefreshSession> read(long uid,String sid) {
        Map<String,Object> v=new HashMap<>();
        try {
            List<?> values=redis.execute(readSessionScript,List.of(key(uid,sid)));
            if (values==null) throw new AuthenticationInfrastructureException();
            if (values.size()!=10) return Optional.empty();
            var fields=List.of("schemaVersion","userId","sessionId","currentHash","credentialRevision","mode","createdAt","lastRotatedAt","absoluteExpiresAt","rotationCount");
            for(int i=0;i<fields.size();i++) v.put(fields.get(i),values.get(i));
        } catch (DataAccessException e) { throw new AuthenticationInfrastructureException(); }
        try {
            if (!"1".equals(v.get("schemaVersion"))
                    || !Long.toString(uid).equals(v.get("userId")) || !sid.equals(v.get("sessionId"))) return Optional.empty();
            String mode=(String)v.get("mode"),hash=(String)v.get("currentHash"),revision=(String)v.get("credentialRevision");
            if (!Set.of("FULL","PASSWORD_CHANGE_ONLY").contains(mode) || !hash.matches("[0-9a-f]{64}") || !revision.matches("[0-9a-f]{64}")) return Optional.empty();
            var created=Instant.ofEpochMilli(Long.parseLong((String)v.get("createdAt")));
            var rotated=Instant.ofEpochMilli(Long.parseLong((String)v.get("lastRotatedAt")));
            var expiry=Instant.ofEpochMilli(Long.parseLong((String)v.get("absoluteExpiresAt")));
            int count=Integer.parseInt((String)v.get("rotationCount"));
            if (created.isAfter(rotated) || !rotated.isBefore(expiry) || !clock.instant().isBefore(expiry)
                    || count<0 || count>2000) return Optional.empty();
            return Optional.of(new RefreshSession(uid,sid,hash,revision,mode,created,rotated,expiry,count));
        } catch (RuntimeException e) { return Optional.empty(); }
    }
    @Override public Rotation rotate(RefreshSession s,String old,String next,String mode,Instant now) {
        try {
            String result=redis.execute(rotateRefreshScript,List.of(key(s.userId(),s.sessionId())),old,next,s.credentialRevision(),
                    s.mode(),mode,Long.toString(now.toEpochMilli()),Integer.toString(properties.session().maxRotations()),
                    Long.toString(s.userId()),s.sessionId(),Long.toString(s.absoluteExpiresAt().toEpochMilli()));
            if (result==null) throw new AuthenticationInfrastructureException();
            return Rotation.valueOf(result);
        } catch (DataAccessException e) { throw new AuthenticationInfrastructureException(); }
    }
    @Override public void delete(long uid,String sid) {
        try { if (redis.delete(key(uid,sid))==null) throw new AuthenticationInfrastructureException(); }
        catch (DataAccessException e) { throw new AuthenticationInfrastructureException(); }
    }
}
