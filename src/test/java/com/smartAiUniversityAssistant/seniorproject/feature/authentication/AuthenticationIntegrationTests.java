package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AuthenticationIntegrationTests extends IntegrationSupport {
    private static final String AUTH="/api/v1/admin/auth";
    private static final String PASSWORD="legacy password";
    private static final String NEW_PASSWORD="a new long passphrase for university";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired JwtDecoder decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean RedisRefreshSessionRepository sessions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JwtTokenService tokenService;
    @Autowired com.smartAiUniversityAssistant.seniorproject.security.JwtAuthenticationConverter authenticationConverter;
    @Autowired CredentialVerificationService verifier;
    @Autowired AccountSecurityReader accounts;
    @Autowired PasswordPolicy policy;
    private long uid;

    @BeforeEach void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) c -> { c.serverCommands().flushDb(); return null; });
         db.update("DELETE FROM app_user_roles"); db.update("DELETE FROM appuser_credentials");
         db.update("UPDATE app_users SET department_id=NULL");
         db.update("DELETE FROM programs");
         db.update("DELETE FROM departments");
         db.update("DELETE FROM faculties");
         db.update("UPDATE app_users SET created_by=NULL,updated_by=NULL"); db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        uid=db.queryForObject("INSERT INTO app_users(employee_id,first_name,last_name,email,account_status) VALUES ('TEST-01','Test','User','Admin@example.test','ACTIVE') RETURNING id",Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",uid,encoder.encode(PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='ADMIN'",uid);
    }
    private ResultActions postJson(String path,Object body,String access) throws Exception {
        var request=post(path).contentType("application/json").content(json.writeValueAsBytes(body));
        if(access!=null) request.header("Authorization","Bearer "+access);
        return mvc.perform(request);
    }
    private JsonNode body(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsString()); }
    private JsonNode login() throws Exception {
        return body(postJson(AUTH+"/login",new LoginRequest(" Admin@example.test ",PASSWORD),"unrelated-expired-token")
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Pragma","no-cache")));
    }
    private ResultActions me(String token) throws Exception { return mvc.perform(get(AUTH+"/me").header("Authorization","Bearer "+token)); }
    private ResultActions refresh(String token) throws Exception { return postJson(AUTH+"/refresh",new RefreshTokenRequest(token),"unrelated-expired-token"); }
    private String access(JsonNode token) { return token.get("accessToken").asText(); }
    private String refreshToken(JsonNode token) { return token.get("refreshToken").asText(); }

    @Test void completeLoginRefreshMeLogoutAndSecretRedaction() throws Exception {
        var first=login();
        var jwt=decoder.decode(access(first));
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(uid));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getHeaders()).containsEntry("alg","RS256").containsEntry("kid","auth-key-1");
        var parsed=RefreshToken.parse(refreshToken(first));
        String key=sessions.key(uid,parsed.sessionId());
        assertThat(redis.getExpire(key)).isBetween(1L,604800L);
        var stored=redis.opsForHash().entries(key);
        assertThat(stored.toString()).doesNotContain(refreshToken(first),PASSWORD,"$2a$");
        assertThat(stored.get("currentHash")).isEqualTo(parsed.digest());
        String before=(String)stored.get("absoluteExpiresAt");
        me(access(first)).andExpect(status().isOk()).andExpect(jsonPath("id").value(uid))
                .andExpect(jsonPath("roles[0].code").value("ADMIN")).andExpect(jsonPath("passwordHash").doesNotExist());
        var rotated=body(refresh(refreshToken(first)).andExpect(status().isOk()));
        assertThat(refreshToken(rotated)).isNotEqualTo(refreshToken(first));
        assertThat(RefreshToken.parse(refreshToken(rotated)).sessionId()).isEqualTo(parsed.sessionId());
        assertThat(redis.opsForHash().get(key,"used:"+parsed.digest())).isEqualTo("1");
        assertThat(redis.opsForHash().get(key,"absoluteExpiresAt")).isEqualTo(before);
        me(access(first)).andExpect(status().isOk());
        postJson(AUTH+"/logout",Map.of(),access(rotated)).andExpect(status().isNoContent()).andExpect(content().string(""));
        me(access(first)).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
        refresh(refreshToken(rotated)).andExpect(status().isUnauthorized());
        postJson(AUTH+"/logout",Map.of(),access(rotated)).andExpect(status().isUnauthorized());
        assertThat(new LoginRequest("a@b.test",PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new RefreshTokenRequest(refreshToken(first)).toString()).doesNotContain(refreshToken(first));
    }

    @Test void consumedReplayRevokesButRandomSecretDoesNot() throws Exception {
        var first=login(); var parsed=RefreshToken.parse(refreshToken(first));
        refresh(RefreshToken.generate(uid,parsed.sessionId()).value()).andExpect(status().isUnauthorized());
        me(access(first)).andExpect(status().isOk());
        var next=body(refresh(refreshToken(first)).andExpect(status().isOk()));
        refresh(refreshToken(first)).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_REFRESH_TOKEN"));
        me(access(next)).andExpect(status().isUnauthorized());
        refresh(refreshToken(next)).andExpect(status().isUnauthorized());
    }

    @Test void concurrentRefreshHasAtMostOneSuccessAndRevokesSession() throws Exception {
        var token=login();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<Integer> attempt=() -> { barrier.await(); return refresh(refreshToken(token)).andReturn().getResponse().getStatus(); };
            var a=pool.submit(attempt); var b=pool.submit(attempt);
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,401);
        }
        me(access(token)).andExpect(status().isUnauthorized());
    }

    @Test void failureCountersCommitAndConcurrentLockoutIsExact() throws Exception {
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test","wrong"),null).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?",Integer.class,uid)).isEqualTo(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Callable<CredentialVerificationResult>>();
            for(int i=0;i<8;i++) calls.add(() -> verifier.verify("Admin@example.test","wrong"));
            for(var result:pool.invokeAll(calls)) assertThat(result.get().accepted()).isFalse();
        }
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?",Integer.class,uid)).isEqualTo(5);
        var locked=db.queryForObject("SELECT locked_until FROM appuser_credentials WHERE user_id=?",LocalDateTime.class,uid);
        assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isFalse();
        assertThat(db.queryForObject("SELECT locked_until FROM appuser_credentials WHERE user_id=?",LocalDateTime.class,uid)).isEqualTo(locked);
        clock.set(locked.toInstant(ZoneOffset.UTC));
        assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isTrue();
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?",Integer.class,uid)).isZero();
    }

    @Test void exactEmailAndAccountEligibility() throws Exception {
        postJson(AUTH+"/login",new LoginRequest("admin@example.test",PASSWORD),null).andExpect(status().isUnauthorized());
        for(String state:List.of("INACTIVE","LOCKED","SUSPENDED","UNKNOWN")) {
            db.update("UPDATE app_users SET account_status=? WHERE id=?",state,uid);
            assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isFalse();
        }
        db.update("UPDATE app_users SET account_status='ACTIVE',is_deleted=TRUE WHERE id=?",uid);
        assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isFalse();
        db.update("UPDATE app_users SET is_deleted=FALSE WHERE id=?",uid);
        db.update("UPDATE appuser_credentials SET password_hash='corrupt' WHERE user_id=?",uid);
        assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isFalse();
        db.update("DELETE FROM appuser_credentials WHERE user_id=?",uid);
        assertThat(verifier.verify("Admin@example.test",PASSWORD).accepted()).isFalse();
    }

    @Test void currentRolesStatusAndLockAreAppliedOnEveryRequest() throws Exception {
        var token=login();
        db.update("UPDATE app_roles SET is_active=FALSE WHERE role_code='ADMIN'");
        me(access(token)).andExpect(status().isOk()).andExpect(jsonPath("roles").isEmpty());
        assertThat(accounts.read(uid).orElseThrow().roles()).isEmpty();
        db.update("UPDATE app_users SET account_status='SUSPENDED' WHERE id=?",uid);
        me(access(token)).andExpect(status().isUnauthorized());
        db.update("UPDATE app_users SET account_status='ACTIVE' WHERE id=?",uid);
        db.update("UPDATE appuser_credentials SET locked_until=? WHERE user_id=?",LocalDateTime.ofInstant(clock.instant().plusSeconds(30),ZoneOffset.UTC),uid);
        me(access(token)).andExpect(status().isUnauthorized());
        clock.advance(Duration.ofSeconds(30));
        me(access(token)).andExpect(status().isOk());
        db.update("UPDATE app_users SET is_deleted=TRUE WHERE id=?",uid);
        me(access(token)).andExpect(status().isUnauthorized());
        refresh(refreshToken(token)).andExpect(status().isUnauthorized());
    }

    @Test void forcedChangeCompletesAndInvalidatesEveryDevice() throws Exception {
        var other=login();
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?",uid);
        var restricted=login();
        assertThat(restricted.get("forcePasswordChange").asBoolean()).isTrue();
        assertThat(restricted.get("expiresIn").asLong()).isBetween(1L,600L);
        assertThat(restricted.get("refreshExpiresIn").asLong()).isBetween(1L,600L);
        mvc.perform(get("/business").header("Authorization","Bearer "+access(other))).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        var renewed=body(refresh(refreshToken(restricted)).andExpect(status().isOk()));
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?",uid);
        mvc.perform(get("/business").header("Authorization","Bearer "+access(renewed))).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        postJson(AUTH+"/change-password",new ChangePasswordRequest(PASSWORD,"too short"),access(renewed))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PASSWORD_POLICY_VIOLATION"));
        postJson(AUTH+"/change-password",new ChangePasswordRequest("wrong",NEW_PASSWORD),access(renewed))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_CURRENT_PASSWORD"));
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?",Integer.class,uid)).isEqualTo(1);
        postJson(AUTH+"/change-password",new ChangePasswordRequest(PASSWORD,NEW_PASSWORD),access(renewed))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        me(access(other)).andExpect(status().isUnauthorized()); refresh(refreshToken(other)).andExpect(status().isUnauthorized());
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test",NEW_PASSWORD),null).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false));
    }

    @Test void logoutAffectsOnlyCurrentDeviceAndSessionLossRejectsJwt() throws Exception {
        var a=login();var b=login();
        postJson(AUTH+"/logout",Map.of(),access(a)).andExpect(status().isNoContent());
        me(access(b)).andExpect(status().isOk());
        var p=RefreshToken.parse(refreshToken(b)); redis.delete(sessions.key(uid,p.sessionId()));
        me(access(b)).andExpect(status().isUnauthorized());
    }

    @Test void absoluteExpiryRotationCapAndCreateCollision() throws Exception {
        var token=login();var parsed=RefreshToken.parse(refreshToken(token));
        var s=sessions.read(uid,parsed.sessionId()).orElseThrow();
        assertThat(sessions.create(s)).isFalse();
        assertThat(sessions.read(uid,parsed.sessionId()).orElseThrow()).isEqualTo(s);
        redis.opsForHash().put(sessions.key(uid,parsed.sessionId()),"rotationCount","2000");
        refresh(refreshToken(token)).andExpect(status().isUnauthorized());
        assertThat(sessions.read(uid,parsed.sessionId())).isEmpty();
        var restrictedToken=login();clock.advance(Duration.ofDays(7));
        refresh(refreshToken(restrictedToken)).andExpect(status().isUnauthorized());
        me(access(restrictedToken)).andExpect(status().isUnauthorized());
    }

    @Test void requestValidationCorsDefaultDenialAndNoSession() throws Exception {
        mvc.perform(get(AUTH+"/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
        mvc.perform(get(AUTH+"/login")).andExpect(status().isUnauthorized());
        postJson("/auth/login",new LoginRequest("Admin@example.test",PASSWORD),null).andExpect(status().isUnauthorized());
        var token=login();
        mvc.perform(get("/unknown").header("Authorization","Bearer "+access(token))).andExpect(status().isForbidden());
        var result=me(access(token)).andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get(AUTH+"/me").param("userId","999999").header("Authorization","Bearer "+access(token)))
                .andExpect(jsonPath("id").value(uid));
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test","é".repeat(37)),null).andExpect(status().isBadRequest());
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test","bad\0input"),null).andExpect(status().isBadRequest());
        postJson(AUTH+"/login",Map.of("email","Admin@example.test","password","secret".repeat(3000)),null)
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        mvc.perform(options(AUTH+"/login").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
        mvc.perform(options(AUTH+"/login").header("Origin","https://untrusted.example").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test void identifierThrottleReturnsRetryAfter() throws Exception {
        for(int i=0;i<10;i++) postJson(AUTH+"/login",new LoginRequest("nobody@example.test","wrong"),null).andExpect(status().isUnauthorized());
        postJson(AUTH+"/login",new LoginRequest("nobody@example.test","wrong"),null).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After","60"));
        assertThat(redis.keys("susa:test:auth:throttle:*")).allMatch(key -> !key.contains("@"));
    }

    @Test void redisOutageFailsClosedForLoginRefreshProtectedRequestsAndLogout() throws Exception {
        var token=login(); var docker=org.testcontainers.DockerClientFactory.instance().client();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            postJson(AUTH+"/login",new LoginRequest("Admin@example.test",PASSWORD),null).andExpect(status().isServiceUnavailable());
            refresh(refreshToken(token)).andExpect(status().isServiceUnavailable());
            me(access(token)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTH_SERVICE_UNAVAILABLE"));
            postJson(AUTH+"/logout",Map.of(),access(token)).andExpect(status().isServiceUnavailable());
        } finally { docker.unpauseContainerCmd(REDIS.getContainerId()).exec(); }
    }

    @Test void databaseOutageFailsClosed() throws Exception {
        var token=login(); var docker=org.testcontainers.DockerClientFactory.instance().client();
        docker.pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            me(access(token)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTH_SERVICE_UNAVAILABLE"));
            refresh(refreshToken(token)).andExpect(status().isServiceUnavailable());
            postJson(AUTH+"/login",new LoginRequest("Admin@example.test",PASSWORD),null).andExpect(status().isServiceUnavailable());
        }
        finally { docker.unpauseContainerCmd(POSTGRES.getContainerId()).exec(); }
    }

    @Test void passwordCommitInvalidatesOtherSessionsEvenWhenCleanupFails() throws Exception {
        var first=login();var second=login();
        var parsed=RefreshToken.parse(refreshToken(first));
        org.mockito.Mockito.doThrow(new com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException())
                .when(sessions).delete(uid,parsed.sessionId());
        postJson(AUTH+"/change-password",new ChangePasswordRequest(PASSWORD,NEW_PASSWORD),access(first)).andExpect(status().isNoContent());
        assertThat(redis.hasKey(sessions.key(uid,parsed.sessionId()))).isTrue();
        assertThat(encoder.matches(NEW_PASSWORD,db.queryForObject("SELECT password_hash FROM appuser_credentials WHERE user_id=?",String.class,uid))).isTrue();
        me(access(first)).andExpect(status().isUnauthorized());me(access(second)).andExpect(status().isUnauthorized());
        refresh(refreshToken(second)).andExpect(status().isUnauthorized());
    }

    @Test void ambiguousRotationResponseIsNotRetriedAndOldTokenIsReplay() throws Exception {
        var first=login();var parsed=RefreshToken.parse(refreshToken(first));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException();
        }).when(sessions).rotate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        refresh(refreshToken(first)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("accessToken").doesNotExist());
        assertThat(redis.opsForHash().get(sessions.key(uid,parsed.sessionId()),"rotationCount")).isEqualTo("1");
        org.mockito.Mockito.verify(sessions,org.mockito.Mockito.times(1)).rotate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.reset(sessions);
        refresh(refreshToken(first)).andExpect(status().isUnauthorized());
        me(access(first)).andExpect(status().isUnauthorized());
    }

    @Test void signingAndUnconfirmedCreateFailuresReturnNoTokensOrUsableSession() throws Exception {
        org.mockito.Mockito.doThrow(new com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException())
                .when(tokenService).issue(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test",PASSWORD),null).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("accessToken").doesNotExist());
        assertThat(redis.keys("susa:test:auth:session:*")).isEmpty();
        org.mockito.Mockito.reset(tokenService);
        org.mockito.Mockito.doThrow(new com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException()).when(sessions).create(org.mockito.ArgumentMatchers.any());
        postJson(AUTH+"/login",new LoginRequest("Admin@example.test",PASSWORD),null).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("refreshToken").doesNotExist());
        assertThat(redis.keys("susa:test:auth:session:*")).isEmpty();
    }

    @Test void corruptedOrPersistentSessionsAreRejectedAndCurrentRolesDetermineAuthorities() throws Exception {
        var first=login();var jwt=decoder.decode(access(first));
        assertThat(authenticationConverter.convert(jwt).getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        db.update("DELETE FROM app_user_roles WHERE user_id=?",uid);
        assertThat(authenticationConverter.convert(jwt).getAuthorities()).isEmpty();
        var parsed=RefreshToken.parse(refreshToken(first));String key=sessions.key(uid,parsed.sessionId());
        redis.opsForHash().put(key,"schemaVersion","unknown");
        refresh(refreshToken(first)).andExpect(status().isUnauthorized());
        me(access(first)).andExpect(status().isUnauthorized());
        redis.delete(key);redis.opsForValue().set(key,"wrong-type",Duration.ofMinutes(1));
        refresh(refreshToken(first)).andExpect(status().isUnauthorized());
        redis.delete(key);
        var second=login();var next=RefreshToken.parse(refreshToken(second));redis.persist(sessions.key(uid,next.sessionId()));
        me(access(second)).andExpect(status().isUnauthorized());
    }

    @Test void rotationMetadataConflictDoesNotMutateAndReplayHasPriority() throws Exception {
        var first=login();var token=RefreshToken.parse(refreshToken(first));
        var snapshot=sessions.read(uid,token.sessionId()).orElseThrow();String key=sessions.key(uid,token.sessionId());
        redis.opsForHash().put(key,"mode","PASSWORD_CHANGE_ONLY");
        var replacement=RefreshToken.generate(uid,token.sessionId());
        assertThat(sessions.rotate(snapshot,token.digest(),replacement.digest(),"FULL",clock.instant()))
                .isEqualTo(RefreshSessionRepository.Rotation.CONFLICT);
        assertThat(redis.opsForHash().get(key,"currentHash")).isEqualTo(token.digest());
        var current=sessions.read(uid,token.sessionId()).orElseThrow();
        assertThat(sessions.rotate(current,token.digest(),replacement.digest(),"PASSWORD_CHANGE_ONLY",clock.instant()))
                .isEqualTo(RefreshSessionRepository.Rotation.ROTATED);
        // Simulates a lost successful response; a stale expected snapshot cannot suppress replay detection.
        assertThat(sessions.rotate(snapshot,token.digest(),RefreshToken.generate(uid,token.sessionId()).digest(),"FULL",clock.instant()))
                .isEqualTo(RefreshSessionRepository.Rotation.REPLAY);
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test void migrationHistoryAndSchemaStayValid() {
        assertThat(db.queryForList("SELECT checksum FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6') ORDER BY installed_rank",Integer.class))
                .containsExactly(-1609524967,-715303110,750119622,111998084,-1083275929,347487127);
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history",Integer.class)).isEqualTo(11);
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='7'",String.class))
                .isEqualTo("seed super admin account");
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='8'",String.class))
                .isEqualTo("create faculties");
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='9'",String.class))
                .isEqualTo("create departments");
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='10'",String.class))
                .isEqualTo("add app users department foreign key");
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='11'",String.class))
                .isEqualTo("create programs");
        assertThat(db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'",Integer.class)).isEqualTo(8);
        assertThat(db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('app_roles','app_users','app_user_roles','appuser_credentials')",Integer.class)).isEqualTo(34);
    }
}
