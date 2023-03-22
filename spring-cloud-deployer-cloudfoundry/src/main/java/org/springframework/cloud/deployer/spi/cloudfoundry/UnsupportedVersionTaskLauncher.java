/*
 * Copyright 2017 the original author or authors.
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

import com.github.zafarkhaja.semver.Version;
import org.cloudfoundry.operations.CloudFoundryOperations;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;

/**
 * A failing implementation of {@link org.springframework.cloud.deployer.spi.task.TaskLauncher} for versions of the
 * Cloud Controller API below {@link #MINIMUM_SUPPORTED_VERSION}.
 *
 * @author Eric Bottard
 * @see CloudFoundryDeployerAutoConfiguration
 */
public class UnsupportedVersionTaskLauncher implements TaskLauncher {

	public static final Version MINIMUM_SUPPORTED_VERSION = Version.forIntegers(2, 63, 0);

	private final Version actualVersion;

	private final RuntimeEnvironmentInfo info;

	public UnsupportedVersionTaskLauncher(Version actualVersion, RuntimeEnvironmentInfo info) {
		this.actualVersion = actualVersion;
		this.info = info;
	}

	@Override
	public String launch(AppDeploymentRequest request) {
		throw failure();
	}

	@Override
	public void cancel(String id) {
		throw failure();
	}

	@Override
	public TaskStatus status(String id) {
		throw failure();
	}

	@Override
	public void cleanup(String id) {
		throw failure();
	}

	@Override
	public void destroy(String appName) {
		throw failure();
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return info;
	}
	@Override
	public int getMaximumConcurrentTasks() {
		throw failure();
	}
	@Override
	public int getRunningTaskExecutionCount() {
		throw failure();
	}

	@Override
	public String getLog(String appName) {
		throw failure();
	}

	private UnsupportedOperationException failure() {
		return new UnsupportedOperationException("Cloud Foundry API version " + actualVersion + " is earlier than "
			+ MINIMUM_SUPPORTED_VERSION + " and is incompatible with cf-java-client " +
			RuntimeVersionUtils.getVersion(CloudFoundryOperations.class)+ ". It is thus unsupported");
	}

}
