/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.core.SpringVersion;
import org.springframework.util.StringUtils;

/**
 * Utility class to be used for generating version info for various libraries.
 *
 * @author Thomas Risberg
 */
public class DeployerVersionUtils {

	public static Map<String, String> getVersionInfoMap() {
		Map<String, String> info = new HashMap<>();
		info.put("spring-version", getSpringVersion());
		info.put("spring-boot-version", getSpringBootVersion());
		info.put("deployer-spi-version", getDeployerSpiVersion());
		return info;
	}

	public static String getDeployerSpiVersion() {
		return getVersion(AppDeployer.class);
	}

	public static String getSpringVersion() {
		return SpringVersion.getVersion();
	}

	public static String getSpringBootVersion() {
		Class springApp;
		try {
			springApp = Class.forName("org.springframework.boot.SpringApplication");
		} catch (ClassNotFoundException e) {
			return "unknown";
		}
		return getVersion(springApp);
	}

	public static String getVersion(final Class<?> source) {
		String version = source.getPackage().getImplementationVersion();
		if (!StringUtils.hasText(version)) {
			return "unknown";
		}
		return version;
	}
}
