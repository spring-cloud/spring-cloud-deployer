/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.kubernetes;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


/**
 * Test the {@link DeploymentPropertiesResolver} class
 * @author Glenn Renfro
 */
public class DeploymentPropertiesResolverTests {

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testRestartPolicy(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		DeploymentPropertiesResolver deploymentPropertiesResolver = getDeploymentPropertiesResolver(isDeprecated, kubernetesDeployerProperties);
		Map<String, String> properties = new HashMap<>();
		RestartPolicy restartPolicy = deploymentPropertiesResolver.getRestartPolicy(properties);
		Assertions.assertThat(restartPolicy).isEqualTo(RestartPolicy.Always);
		if (isDeprecated) {
			properties.put(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX + ".restartPolicy", RestartPolicy.Never.name());
		}
		else {
			properties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".restartPolicy", RestartPolicy.Never.name());
		}
		restartPolicy = deploymentPropertiesResolver.getRestartPolicy(properties);
		Assertions.assertThat(restartPolicy).isEqualTo(RestartPolicy.Never);

	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testTaskServiceAccountName(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		DeploymentPropertiesResolver deploymentPropertiesResolver = getDeploymentPropertiesResolver(isDeprecated, kubernetesDeployerProperties);
		Map<String, String> properties = new HashMap<>();
		String taskServiceAccountName = deploymentPropertiesResolver.getTaskServiceAccountName(properties);
		Assertions.assertThat(taskServiceAccountName).isNull();
		if (isDeprecated) {
			properties.put(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX + ".taskServiceAccountName", "FOO");
		}
		else {
			properties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".taskServiceAccountName", "FOO");
		}
		taskServiceAccountName = deploymentPropertiesResolver.getTaskServiceAccountName(properties);
		Assertions.assertThat(taskServiceAccountName).isEqualTo("FOO");

	}

	private DeploymentPropertiesResolver getDeploymentPropertiesResolver(boolean isDeprecated, KubernetesDeployerProperties properties) {
		String propertiesPrefix = (isDeprecated) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;
		return new DeploymentPropertiesResolver(propertiesPrefix, properties);
	}
}
