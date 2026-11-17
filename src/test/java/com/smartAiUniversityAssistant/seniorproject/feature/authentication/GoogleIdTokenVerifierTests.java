package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.GoogleIdTokenVerifier;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl.GoogleIdTokenVerifierImpl;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Spec file 61: the Google ID token check against a local key server that stands in for Google's
 * (https://www.googleapis.com/oauth2/v3/certs). Tokens are signed here exactly as Google signs them (RS256, kid).
 */
class GoogleIdTokenVerifierTests {
    private static final String CLIENT_ID = "1234-abc.apps.googleusercontent.com";
    private static HttpServer keyServer;
    private static RSAKey googleKey;
    private static RSAKey strangerKey;
    private static volatile boolean keyServerBroken;
    private GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void startKeyServer() throws Exception {
        googleKey = new RSAKeyGenerator(2048).keyID("google-key-1").generate();
        strangerKey = new RSAKeyGenerator(2048).keyID("google-key-1").generate();
        keyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keyServer.createContext("/certs", exchange -> {
            byte[] body = new JWKSet(googleKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (keyServerBroken) { exchange.sendResponseHeaders(500, -1); exchange.close(); return; }
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        keyServer.start();
    }

    @AfterAll
    static void stopKeyServer() { keyServer.stop(0); }

    @BeforeEach
    void verifier() {
        keyServerBroken = false;
        verifier = new GoogleIdTokenVerifierImpl(properties(CLIENT_ID));
    }

    @Test
    void aGoogleTokenForThisApplicationIsAcceptedAndRead() throws Exception {
        var identity = verifier.verify(token(googleKey, claims -> { }));
        assertThat(identity.subject()).isEqualTo("108234567890123456789");
        assertThat(identity.email()).isEqualTo("somchai.j@rsu.ac.th");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.hostedDomain()).isEqualTo("rsu.ac.th");
        assertThat(identity.givenName()).isEqualTo("Somchai");
        assertThat(identity.familyName()).isEqualTo("Jaidee");
        assertThat(identity.fullName()).isEqualTo("Somchai Jaidee");
        assertThat(identity.toString()).doesNotContain("somchai", "108234567890123456789");

        // Both issuer spellings Google uses, an unverified email, and an account without a Workspace domain.
        var other = verifier.verify(token(googleKey, claims -> claims.issuer("accounts.google.com")
                .claim("email_verified", false).claim("hd", null)));
        assertThat(other.emailVerified()).isFalse();
        assertThat(other.hostedDomain()).isNull();
        assertThat(verifier.verify(token(googleKey, claims -> claims.claim("email_verified", "true"))).emailVerified()).isTrue();
    }

    @Test
    void tokensThatAreNotGoogleTokensForThisApplicationAreRejected() throws Exception {
        // Signed by somebody else with the same key id.
        rejected(token(strangerKey, claims -> { }));
        // A real Google token, but issued for another website's client ID.
        rejected(token(googleKey, claims -> claims.audience("9999-other.apps.googleusercontent.com")));
        rejected(token(googleKey, claims -> claims.issuer("https://accounts.example.test")));
        rejected(token(googleKey, claims -> claims.expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(10))))));
        rejected(token(googleKey, claims -> claims.expirationTime(null)));
        rejected(token(googleKey, claims -> claims.subject(null)));
        rejected(token(googleKey, claims -> claims.claim("email", null)));
        // A changed payload under the original signature.
        String valid = token(googleKey, claims -> { });
        String[] parts = valid.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("somchai.j@rsu.ac.th", "attacker@rsu.ac.th").getBytes(StandardCharsets.UTF_8));
        rejected(parts[0] + "." + forgedPayload + "." + parts[2]);
        // An unsigned token and plain garbage.
        rejected(parts[0] + "." + parts[1] + ".");
        rejected("not-a-jwt");
        rejected(" ");
        rejected(null);
    }

    @Test
    void anotherClientIdIsNeverAccepted() throws Exception {
        String token = token(googleKey, claims -> { });
        assertThatThrownBy(() -> new GoogleIdTokenVerifierImpl(properties("another-client-id")).verify(token))
                .isInstanceOfSatisfying(AuthenticationFailure.class, e -> assertThat(e.code()).isEqualTo("INVALID_GOOGLE_TOKEN"));
    }

    @Test
    void unreachableSigningKeysAreAServiceProblemNotAnInvalidToken() throws Exception {
        keyServerBroken = true;
        String token = token(googleKey, claims -> { });
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthenticationFailure.class, e -> {
            assertThat(e.status()).isEqualTo(503);
            assertThat(e.code()).isEqualTo("GOOGLE_SIGN_IN_UNAVAILABLE");
        });
        // The next sign-in works again once the keys can be read.
        keyServerBroken = false;
        assertThat(verifier.verify(token).email()).isEqualTo("somchai.j@rsu.ac.th");
    }

    private void rejected(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthenticationFailure.class, e -> {
            assertThat(e.status()).isEqualTo(401);
            assertThat(e.code()).isEqualTo("INVALID_GOOGLE_TOKEN");
        });
    }

    private static String token(RSAKey key, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENT_ID)
                .subject("108234567890123456789").issueTime(Date.from(now)).expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .claim("azp", CLIENT_ID).claim("email", "somchai.j@rsu.ac.th").claim("email_verified", true).claim("hd", "rsu.ac.th")
                .claim("name", "Somchai Jaidee").claim("given_name", "Somchai").claim("family_name", "Jaidee");
        change.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static AdminRegistrationProperties properties(String clientId) {
        return new AdminRegistrationProperties(true, "rsu.ac.th", "ADMIN", Duration.ofHours(24),
                "http://localhost:3000/verify-email", "http://localhost:3000/login", 3,
                new AdminRegistrationProperties.Google(clientId, "http://127.0.0.1:" + keyServer.getAddress().getPort() + "/certs", ""));
    }
}
