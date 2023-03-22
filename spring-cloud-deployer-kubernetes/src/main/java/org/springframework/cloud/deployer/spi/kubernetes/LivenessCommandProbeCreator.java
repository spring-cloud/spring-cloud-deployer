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

import org.springframework.cloud.deployer.spi.util.CommandLineTokenizer;
import org.springframework.util.StringUtils;

/**
 * Creates a command based liveness probe
 *
 * @author Chris Schaefer
 * @since 2.5
 */
class LivenessCommandProbeCreator extends CommandProbeCreator {
	LivenessCommandProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties, ContainerConfiguration containerConfiguration) {
		super(kubernetesDeployerProperties, containerConfiguration);
	}

	@Override
	int getInitialDelay() {
		String probeDelayValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "CommandProbeDelay");

		if (StringUtils.hasText(probeDelayValue)) {
			return Integer.valueOf(probeDelayValue);
		}

		return getKubernetesDeployerProperties().getLivenessCommandProbeDelay();
	}

	@Override
	int getPeriod() {
		String probePeriodValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "CommandProbePeriod");

		if (StringUtils.hasText(probePeriodValue)) {
			return Integer.valueOf(probePeriodValue);
		}

		return getKubernetesDeployerProperties().getLivenessCommandProbePeriod();
	}

	@Override
	String[] getCommand() {
		String probeCommandValue = getDeploymentPropertyValue(LIVENESS_DEPLOYER_PROPERTY_PREFIX + "CommandProbeCommand");

		if (StringUtils.hasText(probeCommandValue)) {
			return new CommandLineTokenizer(probeCommandValue).getArgs().toArray(new String[0]);
		}

		if (getKubernetesDeployerProperties().getLivenessCommandProbeCommand() != null) {
			return new CommandLineTokenizer(getKubernetesDeployerProperties().getLivenessCommandProbeCommand())
					.getArgs().toArray(new String[0]);
		}

		throw new IllegalArgumentException("The livenessCommandProbeCommand property must be set.");
	}
}
