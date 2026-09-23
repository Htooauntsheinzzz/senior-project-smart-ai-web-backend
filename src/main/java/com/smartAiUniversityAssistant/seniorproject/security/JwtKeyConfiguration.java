package com.smartAiUniversityAssistant.seniorproject.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.core.io.DefaultResourceLoader;
import java.io.InputStream;

@Configuration(proxyBeanMethods = false)
public class JwtKeyConfiguration {

	@Bean
	public KeyPair jwtKeyPair(
			@Value("${app.auth.jwt.private-key-path:${app.security.jwt.private-key-path}}") String privateKeyPath,
			@Value("${app.auth.jwt.public-key-path:${app.security.jwt.public-key-path}}") String publicKeyPath) {
		try (var privateInput = open(privateKeyPath);
				var publicInput = open(publicKeyPath)) {
			var privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
			var publicKey = RsaKeyConverters.x509().convert(publicInput);
			if (privateKey == null || publicKey == null) {
				throw new IllegalArgumentException("Both RSA keys are required");
			}
			if (privateKey.getModulus().bitLength() < 2048 || publicKey.getModulus().bitLength() < 2048) {
				throw new IllegalArgumentException("RSA keys must be at least 2048 bits");
			}

			var challenge = "RS256 startup key validation".getBytes(StandardCharsets.UTF_8);
			var signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(challenge);
			var signed = signature.sign();
			signature.initVerify(publicKey);
			signature.update(challenge);
			if (!signature.verify(signed)) {
				throw new IllegalArgumentException("RSA private and public keys do not match");
			}
			return new KeyPair(publicKey, privateKey);
		} catch (Exception exception) {
			throw new IllegalStateException(
					"Unable to load RS256 keys. Check JWT_PRIVATE_KEY_PATH and JWT_PUBLIC_KEY_PATH, "
							+ "file permissions, and matching PKCS#8 private / X.509 public PEM keys.", exception);
		}
	}

	private InputStream open(String path) throws java.io.IOException {
		return path.startsWith("file:") || path.startsWith("classpath:")
				? new DefaultResourceLoader().getResource(path).getInputStream()
				: Files.newInputStream(Path.of(path));
	}
}
