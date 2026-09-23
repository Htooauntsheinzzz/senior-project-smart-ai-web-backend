package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationTestProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtContractTests {
    static KeyPair pair;
    static final Instant NOW=Instant.parse("2026-09-18T08:00:00Z");
    JwtDecoder decoder;
    @BeforeAll static void key() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair=generator.generateKeyPair();
    }
    @BeforeEach void decoder() { decoder=new JwtConfig().jwtDecoder(pair,AuthenticationTestProperties.valid(),Clock.fixed(NOW,ZoneOffset.UTC)); }
    Map<String,Object> claims() {
        var map=new HashMap<String,Object>();
        map.put("iss","test-issuer");map.put("aud",List.of("test-audience"));map.put("sub","42");
        map.put("iat",NOW.getEpochSecond());map.put("nbf",NOW.getEpochSecond());map.put("exp",NOW.plusSeconds(900).getEpochSecond());
        map.put("jti",TokenSupport.random(16));map.put("sid",TokenSupport.random(16));map.put("token_use","access");
        map.put("roles",List.of("ADMIN"));map.put("force_password_change",false);map.put("session_mode","FULL");return map;
    }
    String sign(Map<String,Object> claims,String kid,JWSAlgorithm algorithm,KeyPair signingPair) throws Exception {
        var jws=new JWSObject(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).keyID(kid).build(),new Payload(claims));
        jws.sign(new RSASSASigner((RSAPrivateKey)signingPair.getPrivate())); return jws.serialize();
    }
    String sign(Map<String,Object> claims) throws Exception { return sign(claims,"test-key",JWSAlgorithm.RS256,pair); }
    void rejected(Map<String,Object> claims) throws Exception { rejected(claims,"invalid claims"); }
    void rejected(Map<String,Object> claims,String description) throws Exception {
        String value=sign(claims); assertThatThrownBy(() -> decoder.decode(value)).as(description).isInstanceOf(JwtException.class);
    }
    @Test void acceptsContractAndRestrictedTokens() throws Exception {
        assertThat(decoder.decode(sign(claims())).getSubject()).isEqualTo("42");
        var c=claims();c.put("session_mode","PASSWORD_CHANGE_ONLY");c.put("force_password_change",true);c.put("exp",NOW.plusSeconds(600).getEpochSecond());
        assertThat(decoder.decode(sign(c)).getClaimAsBoolean("force_password_change")).isTrue();
    }
    @Test void everyRequiredClaimIsRequired() throws Exception {
        for(String field:claims().keySet()) { var c=claims();c.remove(field);rejected(c); }
    }
    @Test void rejectsWrongIssuerAudienceAndClaimTypes() throws Exception {
        for(var entry:Map.<String,Object>of("iss","other","aud",List.of("other"),"sub","01","sid","bad/key",
                "token_use","refresh","force_password_change","false","session_mode","ADMIN","roles",List.of(1),"jti",42).entrySet()) {
            var c=claims();c.put(entry.getKey(),entry.getValue());rejected(c,entry.getKey()+"="+entry.getValue());
        }
        var c=claims();c.put("sub",42);rejected(c);
        c=claims();c.put("roles",List.of("ADMIN","ADMIN"));rejected(c);
    }
    @Test void rejectsAlgorithmConfusionAndUnknownKeyIds() throws Exception {
        String kid=sign(claims(),"unknown",JWSAlgorithm.RS256,pair);
        assertThatThrownBy(() -> decoder.decode(kid)).isInstanceOf(JwtException.class);
        String rs512=sign(claims(),"test-key",JWSAlgorithm.RS512,pair);
        assertThatThrownBy(() -> decoder.decode(rs512)).isInstanceOf(JwtException.class);
        var other=KeyPairGenerator.getInstance("RSA");other.initialize(2048);
        String wrongKey=sign(claims(),"test-key",JWSAlgorithm.RS256,other.generateKeyPair());
        assertThatThrownBy(() -> decoder.decode(wrongKey)).isInstanceOf(JwtException.class);
        var hmac=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test-key").build(),new Payload(claims()));
        hmac.sign(new MACSigner(new byte[32]));
        assertThatThrownBy(() -> decoder.decode(hmac.serialize())).isInstanceOf(JwtException.class);
        String unsigned=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes())+"."+sign(claims()).split("\\.")[1]+".";
        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
    }
    @Test void checksExpiryIssuedAtNotBeforeLifetimeAndExactSkewBoundary() throws Exception {
        var c=claims();c.put("iat",NOW.plusSeconds(31).getEpochSecond());c.put("nbf",c.get("iat"));rejected(c);
        c=claims();c.put("nbf",NOW.plusSeconds(31).getEpochSecond());rejected(c);
        c=claims();c.put("exp",NOW.plusSeconds(901).getEpochSecond());rejected(c);
        c=claims();c.put("exp",NOW.getEpochSecond());rejected(c);
        c=claims();c.put("iat",NOW.minusSeconds(900).getEpochSecond());c.put("nbf",c.get("iat"));c.put("exp",NOW.minusSeconds(30).getEpochSecond());rejected(c);
        c.put("exp",NOW.minusSeconds(29).getEpochSecond());assertThat(decoder.decode(sign(c))).isNotNull();
    }
    @Test void signerUsesMinimalClaimsAndCapsLifetimeAtSessionExpiry() {
        var config=new JwtConfig();var p=AuthenticationTestProperties.valid();
        var service=new JwtTokenService(config.jwtEncoder(pair,p),p,Clock.fixed(NOW,ZoneOffset.UTC));
        var account=new AccountSecuritySnapshot(42,true,"server-only-credential-revision",false,Set.of("ADMIN"));
        var token=service.issue(account,TokenSupport.random(16),"FULL",NOW.plusSeconds(70));
        var decoded=decoder.decode(token.value());
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(70));
        assertThat(decoded.getClaims()).doesNotContainKeys("email","password","password_hash","credentialRevision","refreshToken");
        assertThat(token.toString()).doesNotContain(token.value());
    }
}
