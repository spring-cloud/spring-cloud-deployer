/*
 * Copyright 2018-2020 the original author or authors.
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

import java.util.Map;

import io.fabric8.kubernetes.api.model.Probe;

import org.springframework.cloud.deployer.spi.kubernetes.support.PropertyParserUtils;

/**
 * Base class for creating Probe's
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
abstract class ProbeCreator {
	static final String KUBERNETES_DEPLOYER_PREFIX = "spring.cloud.deployer.kubernetes";
	static final String LIVENESS_DEPLOYER_PROPERTY_PREFIX = KUBERNETES_DEPLOYER_PREFIX + ".liveness";
	static final String READINESS_DEPLOYER_PROPERTY_PREFIX = KUBERNETES_DEPLOYER_PREFIX + ".readiness";

	private ContainerConfiguration containerConfiguration;
	private KubernetesDeployerProperties kubernetesDeployerProperties;

	ProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
			ContainerConfiguration containerConfiguration) {
		this.containerConfiguration = containerConfiguration;
		this.kubernetesDeployerProperties = kubernetesDeployerProperties;
	}

	abstract Probe create();

	abstract int getInitialDelay();

	abstract int getPeriod();

	KubernetesDeployerProperties getKubernetesDeployerProperties() {
		return kubernetesDeployerProperties;
	}

	private Map<String, String> getDeploymentProperties() {
		return this.containerConfiguration.getAppDeploymentRequest().getDeploymentProperties();
	}

	String getDeploymentPropertyValue(String propertyName) {
		return PropertyParserUtils.getDeploymentPropertyValue(getDeploymentProperties(), propertyName);
	}

	ContainerConfiguration getContainerConfiguration() {
		return containerConfiguration;
	}
}
