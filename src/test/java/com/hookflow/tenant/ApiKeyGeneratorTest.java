package com.hookflow.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

	private final ApiKeyGenerator generator = new ApiKeyGenerator();

	@Test
	void generatesPrefixedKeysOfTheExpectedLength() {
		String key = generator.generate();

		// "hf_" plus 32 random bytes in unpadded base64url, which is 43 characters.
		assertThat(key).startsWith("hf_").hasSize(3 + 43);
	}

	@Test
	void generatesADifferentKeyEveryTime() {
		assertThat(generator.generate()).isNotEqualTo(generator.generate());
	}

	@Test
	void hashesAreStableAndDoNotContainTheKey() {
		String key = generator.generate();
		String hash = ApiKeyGenerator.hash(key);

		assertThat(hash).isEqualTo(ApiKeyGenerator.hash(key));
		assertThat(hash).hasSize(64).doesNotContain(key);
	}

	@Test
	void hashMatchesAKnownSha256Value() {
		assertThat(ApiKeyGenerator.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
