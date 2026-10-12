package com.codearena.codearena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CodearenaApplication {

	public static void main(String[] args) {
		if (isBlank(System.getenv("CODEARENA_DB_USERNAME"))
				|| isBlank(System.getenv("CODEARENA_DB_PASSWORD"))
				|| !hasValidJwtSecret()) {
			throw new IllegalStateException(
					"Set CODEARENA_DB_USERNAME, CODEARENA_DB_PASSWORD, and a CODEARENA_JWT_SECRET of at least 32 UTF-8 bytes before startup.");
		}
		SpringApplication.run(CodearenaApplication.class, args);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean hasValidJwtSecret() {
		String secret = System.getenv("CODEARENA_JWT_SECRET");
		return !isBlank(secret) && secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32;
	}

}
