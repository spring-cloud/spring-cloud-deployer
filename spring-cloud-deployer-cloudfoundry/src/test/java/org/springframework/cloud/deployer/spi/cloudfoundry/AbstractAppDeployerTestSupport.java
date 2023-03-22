/*
 * Copyright 2022 the original author or authors.
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

import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.Applications;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.applications.ScaleApplicationRequest;
import org.cloudfoundry.operations.services.Services;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * @author David Turanski
 */
public abstract class AbstractAppDeployerTestSupport {
	protected final CloudFoundryDeploymentProperties deploymentProperties = new CloudFoundryDeploymentProperties();

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	protected AppNameGenerator applicationNameGenerator;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	protected Applications applications;

	protected CloudFoundryAppDeployer deployer;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	protected CloudFoundryOperations operations;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	protected Services services;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	protected RuntimeEnvironmentInfo runtimeEnvironmentInfo;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		given(this.operations.applications()).willReturn(this.applications);
		given(this.operations.services()).willReturn(this.services);
		this.deployer = new CloudFoundryAppDeployer(this.applicationNameGenerator, this.deploymentProperties,
				this.operations, this.runtimeEnvironmentInfo);
		postSetUp();
	}

	protected abstract void postSetUp();

	protected void givenRequestScaleApplication(String id, Integer count, int memoryLimit, int diskLimit,
			Mono<Void> response) {
		given(this.operations.applications()
				.scale(ScaleApplicationRequest.builder().name(id).instances(count).memoryLimit(memoryLimit)
						.diskLimit(diskLimit)
						.startupTimeout(this.deploymentProperties.getStartupTimeout())
						.stagingTimeout(this.deploymentProperties.getStagingTimeout()).build())).willReturn(response);
	}

	protected void givenRequestDeleteApplication(String id, Mono<Void> response) {
		given(this.operations.applications()
				.delete(DeleteApplicationRequest.builder().deleteRoutes(true).name(id).build())).willReturn(response);
	}

	@SuppressWarnings("unchecked")
	protected void givenRequestGetApplication(String id, Mono<ApplicationDetail> response,
			Mono<ApplicationDetail>... responses) {
		given(this.operations.applications().get(GetApplicationRequest.builder().name(id).build())).willReturn(response,
				responses);
	}

	protected void givenRequestPushApplication(PushApplicationManifestRequest request, Mono<Void> response) {
		given(this.operations.applications()
				.pushManifest(any(PushApplicationManifestRequest.class)))
				.willReturn(response);
	}

	protected Map<String, String> defaultEnvironmentVariables() {
		Map<String, String> environmentVariables = new HashMap<>();
		environmentVariables.put("SPRING_APPLICATION_JSON", "{}");
		addGuidAndIndex(environmentVariables);
		return environmentVariables;
	}

	protected void addGuidAndIndex(Map<String, String> environmentVariables) {
		environmentVariables.put("SPRING_APPLICATION_INDEX", "${vcap.application.instance_index}");
		environmentVariables.put("SPRING_CLOUD_APPLICATION_GUID",
				"${vcap.application.name}:${vcap.application.instance_index}");
	}
}
