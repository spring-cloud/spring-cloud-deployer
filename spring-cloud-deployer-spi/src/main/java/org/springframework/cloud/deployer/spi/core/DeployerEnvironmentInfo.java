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

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.util.DeployerVersionUtils;
import org.springframework.core.SpringVersion;
import org.springframework.util.Assert;

/**
 * Class used to communicate the deployer environment info.
 *
 * @author Thomas Risberg
 */
public class DeployerEnvironmentInfo {

	/**
	 * The SPI version used by this deployer.
	 */
	private String deployerSpiVersion;

	/**
	 * The name of this deployer (could be simple class name).
	 */
	private String deployerName;

	/**
	 * The implementation version of this deployer.
	 */
	private String deployerImplementationVersion;

	/**
	 * The deployment platform for this deployer.
	 */
	private String platformType;

	/**
	 * The deployment platform API for this deployer.
	 */
	private String platformApiVersion;

	/**
	 * The client library version used by this deployer.
	 */
	private String platformClientVersion;

	/**
	 * The version running on the host of the platform used by this deployer.
	 */
	private String platformHostVersion;

	/**
	 * The Java version used by this deployer.
	 */
	private String javaVersion;

	/**
	 * The Spring Framework version used by this deployer.
	 */
	private String springVersion;

	/**
	 * The Spring Boot version used by this deployer.
	 */
	private String springBootVersion;

	/**
	 * Platform specific properties
	 */
	private Map<String, String> platformSpecificInfo = new HashMap<>();

	private DeployerEnvironmentInfo(String deployerName, String deployerImplementationVersion,
			String platformType, String platformApiVersion, String platformClientVersion,
            String platformHostVersion, Map<String, String> platformSpecificInfo) {
		Assert.notNull(deployerName, "deployerName is required");
		Assert.notNull(deployerImplementationVersion, "deployerImplementationVersion is required");
		Assert.notNull(platformType, "platformType is required");
		Assert.notNull(platformApiVersion, "platformApiVersion is required");
		Assert.notNull(platformClientVersion, "platformClientVersion is required");
		Assert.notNull(platformHostVersion, "platformHostVersion is required");
		this.deployerSpiVersion = DeployerVersionUtils.getVersion(AppDeployer.class);
		this.deployerName = deployerName;
		this.deployerImplementationVersion = deployerImplementationVersion;
		this.platformType = platformType;
		this.platformApiVersion = platformApiVersion;
		this.platformClientVersion = platformClientVersion;
		this.platformHostVersion = platformHostVersion;
		this.javaVersion = System.getProperty("java.version");
		this.springVersion = SpringVersion.getVersion();
		this.springBootVersion = DeployerVersionUtils.getSpringBootVersion();
		this.platformSpecificInfo.putAll(platformSpecificInfo);
	}

	public String getDeployerSpiVersion() {
		return deployerSpiVersion;
	}

	public String getDeployerName() {
		return deployerName;
	}

	public String getDeployerImplementationVersion() {
		return deployerImplementationVersion;
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

		private String deployerName;

		private String deployerImplementationVersion;

		private String platformType;

		private String platformApiVersion;

		private String platformClientVersion;

		private String platformHostVersion;

		private Map<String, String> platformSpecificInfo = new HashMap<>();

		public Builder() {
		}

		public Builder deployerName(String deployerName) {
			this.deployerName = deployerName;
			return this;
		}

		public Builder deployerImplementationVersion(String deployerImplementationVersion) {
			this.deployerImplementationVersion = deployerImplementationVersion;
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

		public DeployerEnvironmentInfo build() {
			return new DeployerEnvironmentInfo(deployerName, deployerImplementationVersion, platformType,
					platformApiVersion, platformClientVersion, platformHostVersion, platformSpecificInfo);
		}
	}
}
