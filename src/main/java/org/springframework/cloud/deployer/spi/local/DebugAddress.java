/*
 * Copyright 2020-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.local;

import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.StringUtils;

/**
 * Helper for parsing the Debugging address for both the legacy debug-port and the new debug-address properties.
 * The debug-port supports only Java 8 and is deprecated. The debug-address can be used for jdk 8 as well as
 * jdk 9 and newer.
 * When set the debug-address property has precedence over debug-port.
 *
 * @author Christian Tzolov
 */
public class DebugAddress {
	private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");
	private static final Pattern IP_PATTERN = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
	private static final Pattern PORT_PATTERN = Pattern.compile("^([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$");

	public final static Logger logger = LoggerFactory.getLogger(DebugAddress.class);

	private final String host;
	private final String port;
	private final String address;
	private final String suspend;

	private DebugAddress(String host, int port, String suspend) {
		this.host = host;
		this.port = "" + port;
		this.suspend = (StringUtils.hasText(suspend)) ? suspend.trim() : "y";
		this.address = (StringUtils.hasText(host)) ? String.format("%s:%s", host, port) : this.port;
	}

	public String getHost() {
		return host;
	}

	public String getPort() {
		return port;
	}

	public String getSuspend() {
		return suspend;
	}

	public String getAddress() {
		return this.address;
	}

	public static Optional<DebugAddress> from(LocalDeployerProperties deployerProperties, int instanceNumber) {

		if (!StringUtils.hasText(deployerProperties.getDebugAddress()) && deployerProperties.getDebugPort() == null) {
			return Optional.empty();
		}

		String debugHost = null;
		String debugPort = ("" + deployerProperties.getDebugPort()).trim();

		if (StringUtils.hasText(deployerProperties.getDebugAddress())) {
			String[] addressParts = deployerProperties.getDebugAddress().split(":");

			if (addressParts.length == 1) { // JDK 8 only
				debugPort = addressParts[0].trim();
			}
			else if (addressParts.length == 2) { // JDK 9+
				debugHost = addressParts[0].trim();
				debugPort = addressParts[1].trim();

				if (!("*".equals(debugHost)
						|| HOSTNAME_PATTERN.matcher(debugHost).matches()
						|| IP_PATTERN.matcher(debugHost).matches())) {
					logger.warn("Invalid debug Host: {}", deployerProperties.getDebugAddress());
					return Optional.empty();
				}
			}
			else {
				logger.warn("Invalid debug address: {}", deployerProperties.getDebugAddress());
				return Optional.empty();
			}
		}

		if (!PORT_PATTERN.matcher(debugPort).matches()) {
			logger.warn("Invalid debug port: {}", debugPort);
			return Optional.empty();
		}

		int portToUse = Integer.parseInt(debugPort) + instanceNumber;

		return Optional.of(new DebugAddress(debugHost, portToUse, deployerProperties.getDebugSuspend().toString()));
	}
}
