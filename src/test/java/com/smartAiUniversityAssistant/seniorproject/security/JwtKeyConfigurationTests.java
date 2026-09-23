package com.smartAiUniversityAssistant.seniorproject.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeyConfigurationTests {

	@TempDir
	Path directory;

	@Test
	void loadsMatchingKeysAtStartup() throws Exception {
		var pair = generatePair(2048);
		writePair(pair, pair);
		context().run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(KeyPair.class);
			assertThat(context.getBean(KeyPair.class).getPublic()).isEqualTo(pair.getPublic());
		});
	}

	@Test
	void rejectsMissingFilesAtStartup() {
		context().run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsMalformedKeysAtStartup() throws Exception {
		Files.writeString(directory.resolve("private.pem"), "invalid PEM");
		Files.writeString(directory.resolve("public.pem"), "invalid PEM");
		context().run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsMismatchedKeysAtStartup() throws Exception {
		writePair(generatePair(2048), generatePair(2048));
		context().run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("RSA private and public keys do not match"));
	}

	@Test
	void rejectsUndersizedKeysAtStartup() throws Exception {
		var pair = generatePair(1024);
		writePair(pair, pair);
		context().run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("RSA keys must be at least 2048 bits"));
	}

	private ApplicationContextRunner context() {
		return new ApplicationContextRunner().withUserConfiguration(JwtKeyConfiguration.class)
				.withPropertyValues(
						"app.security.jwt.private-key-path=" + directory.resolve("private.pem"),
						"app.security.jwt.public-key-path=" + directory.resolve("public.pem"));
	}

	private KeyPair generatePair(int bits) throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(bits);
		return generator.generateKeyPair();
	}

	private void writePair(KeyPair privatePair, KeyPair publicPair) throws Exception {
		writePem("private.pem", "PRIVATE KEY", privatePair.getPrivate().getEncoded());
		writePem("public.pem", "PUBLIC KEY", publicPair.getPublic().getEncoded());
	}

	private void writePem(String name, String type, byte[] encoded) throws Exception {
		Files.writeString(directory.resolve(name), "-----BEGIN " + type + "-----\n"
				+ Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
				+ "\n-----END " + type + "-----\n");
	}
}
