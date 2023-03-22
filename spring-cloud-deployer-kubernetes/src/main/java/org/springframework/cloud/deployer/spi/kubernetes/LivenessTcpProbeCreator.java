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

import org.springframework.util.StringUtils;

/**
 * Creates a TCP liveness probe
 *
 * @author Chris Schaefer
 * @since 2.5
 */
class LivenessTcpProbeCreator extends TcpProbeCreator {
	LivenessTcpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties, ContainerConfiguration containerConfiguration) {
		super(kubernetesDeployerProperties, containerConfiguration);
	}

	@Override
	int getInitialDelay() {
		String probeDelayValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbeDelay");

		if (StringUtils.hasText(probeDelayValue)) {
			return Integer.valueOf(probeDelayValue);
		}

		return getKubernetesDeployerProperties().getLivenessTcpProbeDelay();
	}

	@Override
	int getPeriod() {
		String probePeriodValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbePeriod");

		if (StringUtils.hasText(probePeriodValue)) {
			return Integer.valueOf(probePeriodValue);
		}

		return getKubernetesDeployerProperties().getLivenessTcpProbePeriod();
	}

	@Override
	Integer getPort() {
		String probePortValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbePort");

		if (StringUtils.hasText(probePortValue)) {
			if (!probePortValue.chars().allMatch(Character :: isDigit)) {
				throw new IllegalArgumentException("LivenessTcpProbePort must contain all digits");
			}

			return Integer.parseInt(probePortValue);
		}

		if (getKubernetesDeployerProperties().getLivenessTcpProbePort() != null) {
			return getKubernetesDeployerProperties().getLivenessTcpProbePort();
		}

		throw new IllegalArgumentException("The livenessTcpProbePort property must be set.");
	}
}
