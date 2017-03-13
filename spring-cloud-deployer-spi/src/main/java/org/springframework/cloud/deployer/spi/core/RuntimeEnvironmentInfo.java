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

package org.springframework.cloud.deployer.spi.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;
import org.springframework.core.SpringVersion;
import org.springframework.util.Assert;

/**
 * Class used to communicate the runtime environment info.
 *
 * @author Thomas Risberg
 */
public class RuntimeEnvironmentInfo {

	/**
	 * The SPI version used by this implementation.
	 */
	private String spiVersion;

	/**
	 * The name of this implementation (could be simple class name).
	 */
	private String implementationName;

	/**
	 * The version of this implementation.
	 */
	private String implementationVersion;

	/**
	 * The platform type for this implementation.
	 */
	private String platformType;

	/**
	 * The platform API version for this implementation.
	 */
	private String platformApiVersion;

	/**
	 * The client library version used by this implementation.
	 */
	private String platformClientVersion;

	/**
	 * The version running on the host of the platform used by this implementation.
	 */
	private String platformHostVersion;

	/**
	 * The Java version used by this implementation.
	 */
	private String javaVersion;

	/**
	 * The Spring Framework version used by this implementation.
	 */
	private String springVersion;

	/**
	 * The Spring Boot version used by this implementation.
	 */
	private String springBootVersion;

	/**
	 * Platform specific properties
	 */
	private Map<String, String> platformSpecificInfo = new HashMap<>();

	private RuntimeEnvironmentInfo(Class spiClass, String implementationName, String implementationVersion,
	                               String platformType, String platformApiVersion, String platformClientVersion,
	                               String platformHostVersion, Map<String, String> platformSpecificInfo) {
		Assert.notNull(spiClass, "spiClass is required");
		Assert.notNull(implementationName, "implementationName is required");
		Assert.notNull(implementationVersion, "implementationVersion is required");
		Assert.notNull(platformType, "platformType is required");
		Assert.notNull(platformApiVersion, "platformApiVersion is required");
		Assert.notNull(platformClientVersion, "platformClientVersion is required");
		Assert.notNull(platformHostVersion, "platformHostVersion is required");
		this.spiVersion = RuntimeVersionUtils.getVersion(spiClass);
		this.implementationName = implementationName;
		this.implementationVersion = implementationVersion;
		this.platformType = platformType;
		this.platformApiVersion = platformApiVersion;
		this.platformClientVersion = platformClientVersion;
		this.platformHostVersion = platformHostVersion;
		this.javaVersion = System.getProperty("java.version");
		this.springVersion = SpringVersion.getVersion();
		this.springBootVersion = RuntimeVersionUtils.getSpringBootVersion();
		this.platformSpecificInfo.putAll(platformSpecificInfo);
	}

	public String getSpiVersion() {
		return spiVersion;
	}

	public String getImplementationName() {
		return implementationName;
	}

	public String getImplementationVersion() {
		return implementationVersion;
	}

	public String getPlatformType() {
		return platformType;
	}

	public String getPlatformApiVersion() {
		return platformApiVersion;
	}

	public String getPlatformClientVersion() {
		return platformClientVersion;
	}

	public String getPlatformHostVersion() {
		return platformHostVersion;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public String getSpringVersion() {
		return springVersion;
	}

	public String getSpringBootVersion() {
		return springBootVersion;
	}

	public Map<String, String> getPlatformSpecificInfo() {
		return platformSpecificInfo;
	}

	public static class Builder {

		private Class spiClass;

		private String implementationName;

		private String implementationVersion;

		private String platformType;

		private String platformApiVersion;

		private String platformClientVersion;

		private String platformHostVersion;

		private Map<String, String> platformSpecificInfo = new HashMap<>();

		public Builder() {
		}

		public Builder spiClass(Class spiClass) {
			this.spiClass = spiClass;
			return this;
		}

		public Builder implementationName(String implementationName) {
			this.implementationName = implementationName;
			return this;
		}

		public Builder implementationVersion(String implementationVersion) {
			this.implementationVersion = implementationVersion;
			return this;
		}

		public Builder platformType(String platformType) {
			this.platformType = platformType;
			return this;
		}

		public Builder platformApiVersion(String platformApiVersion) {
			this.platformApiVersion = platformApiVersion;
			return this;
		}

		public Builder platformClientVersion(String platformClientVersion) {
			this.platformClientVersion = platformClientVersion;
			return this;
		}

		public Builder platformHostVersion(String platformHostVersion) {
			this.platformHostVersion = platformHostVersion;
			return this;
		}

		public Builder addPlatformSpecificInfo(String key, String value) {
			this.platformSpecificInfo.put(key, value);
			return this;
		}

		public RuntimeEnvironmentInfo build() {
			return new RuntimeEnvironmentInfo(spiClass, implementationName, implementationVersion, platformType,
					platformApiVersion, platformClientVersion, platformHostVersion, platformSpecificInfo);
		}
	}
}
