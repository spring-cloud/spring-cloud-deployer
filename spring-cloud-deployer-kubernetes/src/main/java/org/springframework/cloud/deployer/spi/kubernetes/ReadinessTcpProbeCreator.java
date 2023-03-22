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
 * Creates a TCP readiness probe
 *
 * @author Chris Schaefer
 * @since 2.5
 */
class ReadinessTcpProbeCreator extends TcpProbeCreator {
	ReadinessTcpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties, ContainerConfiguration containerConfiguration) {
		super(kubernetesDeployerProperties, containerConfiguration);
	}

	@Override
	int getInitialDelay() {
		String probeDelayValue = getDeploymentPropertyValue(READINESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbeDelay");

		if (StringUtils.hasText(probeDelayValue)) {
			return Integer.valueOf(probeDelayValue);
		}

		return getKubernetesDeployerProperties().getReadinessTcpProbeDelay();
	}

	@Override
	int getPeriod() {
		String probePeriodValue = getDeploymentPropertyValue(READINESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbePeriod");

		if (StringUtils.hasText(probePeriodValue)) {
			return Integer.valueOf(probePeriodValue);
		}

		return getKubernetesDeployerProperties().getReadinessTcpProbePeriod();
	}

	@Override
	Integer getPort() {
		String probePortValue = getDeploymentPropertyValue(READINESS_DEPLOYER_PROPERTY_PREFIX + "TcpProbePort");

		if (StringUtils.hasText(probePortValue)) {
			if (!probePortValue.chars().allMatch(Character :: isDigit)) {
				throw new IllegalArgumentException("ReadinessTcpProbePort must contain all digits");
			}

			return Integer.parseInt(probePortValue);
		}

		if (getKubernetesDeployerProperties().getReadinessTcpProbePort() != null) {
			return getKubernetesDeployerProperties().getReadinessTcpProbePort();
		}

		throw new IllegalArgumentException("A readinessTcpProbePort property must be set.");
	}
}
