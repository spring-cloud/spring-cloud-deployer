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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;

/**
 * Provides methods to configure environment, application properties, and command line
 * args if the deployed artifact uses java-cfenv.
 *
 * @author David Turanski
 * @since 2.4
 */
class CfEnvConfigurer {

	private static final Log log = LogFactory.getLog(CfEnvConfigurer.class);

	static final String SPRING_PROFILES_ACTIVE = "SPRING_PROFILES_ACTIVE";

	static final String SPRING_PROFILES_ACTIVE_FQN = "spring.profiles.active";

	static final String SPRING_PROFILES_ACTIVE_HYPHENATED = "spring-profiles-active";

	static final String CLOUD_PROFILE_NAME = "cloud";

	static final String JBP_CONFIG_SPRING_AUTO_RECONFIGURATION = "JBP_CONFIG_SPRING_AUTO_RECONFIGURATION";

	static final String ENABLED_FALSE = "{ enabled: false }";

	/**
	 * Disable Java Buildpack Spring Auto-reconfiguration.
	 *
	 * @param environment a map containing environment variables
	 * @return an copy of the map setting the environment variable needed to disable
	 * auto-reconfiguration
	 */
	static Map<String, String> disableJavaBuildPackAutoReconfiguration(Map<String, String> environment) {
		log.debug("Disabling 'JBP_CONFIG_SPRING_AUTO_RECONFIGURATION'");
		Map<String, String> updatedEnvironment = new HashMap<>(environment);
		updatedEnvironment.putIfAbsent(JBP_CONFIG_SPRING_AUTO_RECONFIGURATION, ENABLED_FALSE);
		return updatedEnvironment;
	}

	/**
	 * Activate the <i>cloud</i> profile. Add to a key that binds to
	 * <i>spring.profiles.active</i> if one exists.
	 * @param environment a map containing environment variables or application properties
	 * @param keyToCreate create a new entry using a preferred key if none exists.
	 * @return the updated map
	 */
	static Map<String, String> activateCloudProfile(Map<String, String> environment, String keyToCreate) {
		log.debug("Activating cloud profile");
		Map<String, String> updatedEnvironment = new HashMap<>(environment);

		if (appendToExistingEntry(updatedEnvironment, SPRING_PROFILES_ACTIVE, CLOUD_PROFILE_NAME)) {
			return updatedEnvironment;
		}
		else if (appendToExistingEntry(updatedEnvironment, SPRING_PROFILES_ACTIVE_FQN, CLOUD_PROFILE_NAME)) {
			return updatedEnvironment;
		}
		else if (appendToExistingEntry(updatedEnvironment, SPRING_PROFILES_ACTIVE_HYPHENATED, CLOUD_PROFILE_NAME)) {
			return updatedEnvironment;
		}
		// If Key provided, create new spring profiles active entry.
		if (StringUtils.hasText(keyToCreate)) {
			updatedEnvironment.put(keyToCreate, CLOUD_PROFILE_NAME);
		}

		return updatedEnvironment;
	}

	/**
	 * Process a command line argument to append the <i>cloud</i> profile if it binds to
	 * <i>spring.profiles.active</i>.
	 *
	 * @param arg the current value
	 * @return the updated value
	 */
	static String appendCloudProfileToSpringProfilesActiveArg(String arg) {
		if ((arg.contains(SPRING_PROFILES_ACTIVE_FQN) ||
				arg.contains(SPRING_PROFILES_ACTIVE_HYPHENATED) ||
				arg.contains(SPRING_PROFILES_ACTIVE)) && arg.contains("=")) {
			String[] tokens = arg.split("=");
			arg = String.join("=", tokens[0], appendToValueIfPresent(tokens[1], CLOUD_PROFILE_NAME));
		}
		return arg;
	}

	private static boolean appendToExistingEntry(Map<String, String> environment, String key, String value) {
		if (environment.containsKey(key)) {
			String current = environment.get(key);
			environment.put(key, appendToValueIfPresent(current, value));
			return true;
		}
		return false;
	}

	private static String appendToValueIfPresent(String current, String value) {
		if (StringUtils.hasText(current)) {
			if (!Stream.of(current.split(",")).filter(s -> s.trim().equals(value)).findFirst().isPresent()) {
				return current.join(",", current, value);
			}
			return current;
		}
		return value;
	}
}
