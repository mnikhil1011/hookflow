package com.hookflow.endpoint;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hookflow.TestProperties;
import com.hookflow.common.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

	private final UrlValidator strict = new UrlValidator(TestProperties.withInsecureUrlsAllowed(false));
	private final UrlValidator lenient = new UrlValidator(TestProperties.withInsecureUrlsAllowed(true));

	@Test
	void acceptsAPublicHttpsUrl() {
		// An IP address is used so the test does not depend on DNS.
		assertThatCode(() -> strict.validate("https://93.184.216.34/webhooks")).doesNotThrowAnyException();
	}

	@Test
	void rejectsPlainHttp() {
		assertThatThrownBy(() -> strict.validate("http://93.184.216.34/webhooks"))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("https");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://127.0.0.1/hook",       // loopback
			"https://10.0.0.5/hook",        // private network
			"https://192.168.1.10/hook",    // private network
			"https://172.16.0.1/hook",      // private network
			"https://169.254.169.254/hook", // cloud metadata service
			"https://[::1]/hook",           // IPv6 loopback
			"https://[fd00::1]/hook" })     // IPv6 private network
	void rejectsPrivateAndLocalAddresses(String url) {
		assertThatThrownBy(() -> strict.validate(url))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("private or local");
	}

	@ParameterizedTest
	@ValueSource(strings = { "not a url", "ftp://example.com/file", "/just/a/path", "https://" })
	void rejectsAnythingThatIsNotAnAbsoluteHttpUrl(String url) {
		assertThatThrownBy(() -> strict.validate(url)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> lenient.validate(url)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void allowsLocalHttpUrlsWhenInsecureUrlsAreAllowed() {
		assertThatCode(() -> lenient.validate("http://localhost:9000/hook/1")).doesNotThrowAnyException();
	}

}
