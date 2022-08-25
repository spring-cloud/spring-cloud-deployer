/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.app.observation;

import io.micrometer.observation.transport.SenderContext;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.lang.Nullable;

/**
 * A {@link SenderContext} for {@link AppDeployer}.
 *
 * @author Marcin Grzejszczak
 */
public class AppDeployerContext extends SenderContext<Object> {

	private final RuntimeEnvironmentInfo runtimeEnvironmentInfo;

	private AppDeploymentRequest request;

	private AppScaleRequest appScaleRequest;

	private String appId;

	public AppDeployerContext(RuntimeEnvironmentInfo runtimeEnvironmentInfo) {
		super((carrier, key, value) -> {
		});
		this.runtimeEnvironmentInfo = runtimeEnvironmentInfo;
		setRemoteServiceName(runtimeEnvironmentInfo.getPlatformType());
	}

	@Nullable
	public AppDeploymentRequest getRequest() {
		return request;
	}

	@Nullable
	public RuntimeEnvironmentInfo getRuntimeEnvironmentInfo() {
		return runtimeEnvironmentInfo;
	}

	public void setRequest(AppDeploymentRequest request) {
		this.request = request;
	}

	@Nullable
	public AppScaleRequest getAppScaleRequest() {
		return appScaleRequest;
	}

	public void setAppScaleRequest(AppScaleRequest appScaleRequest) {
		this.appScaleRequest = appScaleRequest;
	}

	@Nullable
	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}
}
