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

package org.springframework.cloud.deployer.spi.kubernetes;

import io.fabric8.kubernetes.api.model.Probe;

/**
 * Creates health check probes
 *
 * @author Chris Schaefer
 * @since 2.5
 */
class ProbeCreatorFactory {
	static Probe createReadinessProbe(ContainerConfiguration containerConfiguration,
									  KubernetesDeployerProperties kubernetesDeployerProperties, ProbeType probeType) {
		switch (probeType) {
			case HTTP:
				return new ReadinessHttpProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			case TCP:
				return new ReadinessTcpProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			case COMMAND:
				return new ReadinessCommandProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			default:
				throw new IllegalArgumentException("Unknown readiness probe type: " + probeType);
		}
	}

	static Probe createLivenessProbe(ContainerConfiguration containerConfiguration,
									 KubernetesDeployerProperties kubernetesDeployerProperties, ProbeType probeType) {
		switch (probeType) {
			case HTTP:
				return new LivenessHttpProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			case TCP:
				return new LivenessTcpProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			case COMMAND:
				return new LivenessCommandProbeCreator(kubernetesDeployerProperties, containerConfiguration).create();
			default:
				throw new IllegalArgumentException("Unknown liveness probe type: " + probeType);
		}
	}
}
