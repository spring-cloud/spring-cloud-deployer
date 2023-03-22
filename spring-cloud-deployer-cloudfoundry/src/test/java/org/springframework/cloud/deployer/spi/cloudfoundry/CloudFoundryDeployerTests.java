/*
 * Copyright 2020 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

public class CloudFoundryDeployerTests {

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private RuntimeEnvironmentInfo runtimeEnvironmentInfo;

	private Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");
	private AppDefinition definition = new AppDefinition("test-application", Collections.emptyMap());

	@Test
	public void testBuildpacksDefault() {
		CloudFoundryDeploymentProperties props = new CloudFoundryDeploymentProperties();
		TestCloudFoundryDeployer deployer = new TestCloudFoundryDeployer(props, runtimeEnvironmentInfo);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		Set<String> buildpacks = deployer.buildpacks(request);
		assertThat(buildpacks).hasSize(1);
	}

	@Test
	public void testBuildpacksSingleMultiLogic() {
		CloudFoundryDeploymentProperties props = new CloudFoundryDeploymentProperties();
		props.setBuildpack("buildpack1");
		TestCloudFoundryDeployer deployer = new TestCloudFoundryDeployer(props, runtimeEnvironmentInfo);
		Map<String, String> deploymentProperties = new HashMap<>();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties, null);


		Set<String> buildpacks = deployer.buildpacks(request);
		assertThat(buildpacks).hasSize(1);
		assertThat(buildpacks).contains("buildpack1");

		props.setBuildpacks(new HashSet<>(Arrays.asList("buildpack2", "buildpack3")));
		buildpacks = deployer.buildpacks(request);
		assertThat(buildpacks).hasSize(2);
		assertThat(buildpacks).contains("buildpack2", "buildpack3");

		deploymentProperties.put(CloudFoundryDeploymentProperties.BUILDPACK_PROPERTY_KEY, "buildpack4");
		buildpacks = deployer.buildpacks(request);
		assertThat(buildpacks).hasSize(1);
		assertThat(buildpacks).contains("buildpack4");

		deploymentProperties.put(CloudFoundryDeploymentProperties.BUILDPACKS_PROPERTY_KEY, "buildpack5,buildpack6");
		buildpacks = deployer.buildpacks(request);
		assertThat(buildpacks).hasSize(2);
		assertThat(buildpacks).contains("buildpack5", "buildpack6");
	}

	private static class TestCloudFoundryDeployer extends AbstractCloudFoundryDeployer {

		TestCloudFoundryDeployer(CloudFoundryDeploymentProperties deploymentProperties,
				RuntimeEnvironmentInfo runtimeEnvironmentInfo) {
			super(deploymentProperties, runtimeEnvironmentInfo);
		}
	}
}
