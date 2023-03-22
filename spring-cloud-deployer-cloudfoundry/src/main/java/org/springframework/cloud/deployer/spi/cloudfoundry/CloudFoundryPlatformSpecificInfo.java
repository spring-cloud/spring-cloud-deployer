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

import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.util.Assert;

/**
 * A Provides required platform specific values to {@link RuntimeEnvironmentInfo}.
 *
 * @author David Turanski
 */
public class CloudFoundryPlatformSpecificInfo {
	static final String API_ENDPOINT = "API Endpoint";

	static final String ORG = "Organization";

	static final String SPACE = "Space";

	private final RuntimeEnvironmentInfo.Builder runtimeEnvironmentInfo;

	private String apiEndpoint;

	private String org;

	private String space;

	public CloudFoundryPlatformSpecificInfo(RuntimeEnvironmentInfo.Builder runtimeEnvironmentInfo) {
		this.runtimeEnvironmentInfo = runtimeEnvironmentInfo;
	}

	public CloudFoundryPlatformSpecificInfo apiEndpoint(String apiEndpoint) {
		this.apiEndpoint = apiEndpoint;
		return this;
	}

	public CloudFoundryPlatformSpecificInfo org(String org) {
		this.org = org;
		return this;
	}

	public CloudFoundryPlatformSpecificInfo space(String space) {
		this.space = space;
		return this;
	}

	public RuntimeEnvironmentInfo.Builder builder() {
		Assert.hasText(apiEndpoint, "'apiEndpoint' must contain text");
		Assert.hasText(org, "'org' must contain text");
		Assert.hasText(space, "'space' must contain text");
		runtimeEnvironmentInfo.addPlatformSpecificInfo(API_ENDPOINT, apiEndpoint);
		runtimeEnvironmentInfo.addPlatformSpecificInfo(ORG, org);
		runtimeEnvironmentInfo.addPlatformSpecificInfo(SPACE, space);
		return runtimeEnvironmentInfo;
	}
}
