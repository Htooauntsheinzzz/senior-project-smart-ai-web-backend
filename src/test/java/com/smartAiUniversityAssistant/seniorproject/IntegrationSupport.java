package com.smartAiUniversityAssistant.seniorproject;

import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

@Import(IntegrationSupport.TimeConfiguration.class)
public abstract class IntegrationSupport {
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).withCommand("redis-server","--requirepass","test-only-redis-password","--maxmemory-policy","noeviction");
    private static final Path PRIVATE_KEY;
    private static final Path PUBLIC_KEY;
    static {
        try {
            var directory=Files.createTempDirectory("susa-auth-test-keys-");
            directory.toFile().deleteOnExit();
            var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            var pair=generator.generateKeyPair();
            PRIVATE_KEY=directory.resolve("private.pem"); PUBLIC_KEY=directory.resolve("public.pem");
            writeKey(PRIVATE_KEY,"PRIVATE KEY",pair.getPrivate().getEncoded());
            writeKey(PUBLIC_KEY,"PUBLIC KEY",pair.getPublic().getEncoded());
            Startables.deepStart(POSTGRES,REDIS).join();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static void writeKey(Path path,String label,byte[] value) throws Exception {
        Files.writeString(path,"-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(value)+"\n-----END "+label+"-----\n");
        path.toFile().deleteOnExit();
    }
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry r) {
        r.add("spring.config.import",() -> "");
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("spring.datasource.hikari.data-source-properties.socketTimeout",() -> "1");
        r.add("spring.data.redis.host",REDIS::getHost);
        r.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.username",() -> "default");
        r.add("spring.data.redis.password",() -> "test-only-redis-password");
        r.add("spring.data.redis.timeout",() -> "500ms");
        r.add("spring.data.redis.connect-timeout",() -> "500ms");
        r.add("app.security.jwt.private-key-path",() -> PRIVATE_KEY.toString());
        r.add("app.security.jwt.public-key-path",() -> PUBLIC_KEY.toString());
        r.add("app.auth.password.bcrypt-strength",() -> "4");
        r.add("app.auth.session.key-prefix",() -> "susa:test:auth");
    }
    @TestConfiguration(proxyBeanMethods=false) public static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    public static class MutableClock extends Clock {
        private volatile Instant now=Instant.now();
        public void set(Instant instant) { now=instant; }
        public void advance(Duration duration) { now=now.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
