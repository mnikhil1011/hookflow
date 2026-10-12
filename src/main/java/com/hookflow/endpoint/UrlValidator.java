package com.hookflow.endpoint;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import com.hookflow.common.InvalidRequestException;
import com.hookflow.config.HookflowProperties;
import org.springframework.stereotype.Component;

/**
 * Checks that an endpoint URL is safe to send requests to.
 * <p>
 * Without these checks a tenant could register a URL such as {@code http://10.0.0.5/admin} and make
 * Hookflow call services inside its own network. That attack is called server-side request forgery.
 */
@Component
public class UrlValidator {

	private final boolean allowInsecureUrls;

	public UrlValidator(HookflowProperties properties) {
		this.allowInsecureUrls = properties.allowInsecureUrls();
	}

	public void validate(String url) {
		URI uri = parse(url);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
			throw new InvalidRequestException("The URL must be an absolute http or https URL");
		}
		if (allowInsecureUrls) {
			return;
		}
		if (!"https".equalsIgnoreCase(scheme)) {
			throw new InvalidRequestException("The URL must use https");
		}
		if (pointsToPrivateAddress(uri.getHost())) {
			throw new InvalidRequestException("The URL must not point to a private or local address");
		}
	}

	private static URI parse(String url) {
		try {
			return new URI(url);
		}
		catch (URISyntaxException e) {
			throw new InvalidRequestException("The URL is not valid");
		}
	}

	private static boolean pointsToPrivateAddress(String host) {
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || isUniqueLocalIpv6(address)) {
					return true;
				}
			}
			return false;
		}
		catch (UnknownHostException e) {
			throw new InvalidRequestException("The URL's host cannot be resolved");
		}
	}

	/** IPv6 addresses in fc00::/7 are the IPv6 equivalent of private IPv4 ranges. */
	private static boolean isUniqueLocalIpv6(InetAddress address) {
		byte[] bytes = address.getAddress();
		return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
	}

}
