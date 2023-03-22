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

package org.springframework.cloud.deployer.spi.kubernetes;

import org.springframework.cloud.deployer.spi.util.CommandLineTokenizer;
import org.springframework.util.StringUtils;

/**
 * Creates a command based startup probe
 *
 * @author Corneil du Plessis
 * @since 2.5
 */
class StartupCommandProbeCreator extends CommandProbeCreator {
    StartupCommandProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties, ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    @Override
    int getInitialDelay() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Command", "ProbeDelay",
                getKubernetesDeployerProperties().getStartupCommandProbeDelay());
    }

    @Override
    int getPeriod() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Command", "ProbePeriod",
                getKubernetesDeployerProperties().getStartupCommandProbePeriod());
    }

    @Override
    int getFailure() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Command", "ProbeFailure",
                getKubernetesDeployerProperties().getLivenessCommandProbeFailure());
    }

    @Override
    int getSuccess() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Command", "ProbeSuccess",
                getKubernetesDeployerProperties().getLivenessCommandProbeSuccess());
    }

    @Override
    String[] getCommand() {
        String probeCommandValue = getDeploymentPropertyValue(STARTUP_DEPLOYER_PROPERTY_PREFIX + "CommandProbeCommand");

        if (StringUtils.hasText(probeCommandValue)) {
            return new CommandLineTokenizer(probeCommandValue).getArgs().toArray(new String[0]);
        }

        if (getKubernetesDeployerProperties().getStartupCommandProbeCommand() != null) {
            return new CommandLineTokenizer(getKubernetesDeployerProperties().getStartupCommandProbeCommand())
                    .getArgs().toArray(new String[0]);
        }

        throw new IllegalArgumentException("The startupCommandProbeCommand property must be set.");
    }
}
