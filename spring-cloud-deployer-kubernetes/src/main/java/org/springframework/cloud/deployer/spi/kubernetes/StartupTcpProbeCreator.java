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

import org.springframework.util.StringUtils;

/**
 * Creates a TCP startup probe
 *
 * @author Corneil du Plessis
 * @since 2.5
 */
class StartupTcpProbeCreator extends TcpProbeCreator {
    StartupTcpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties, ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    @Override
    int getInitialDelay() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbeDelay",
                getKubernetesDeployerProperties().getStartupTcpProbeDelay());
    }

    @Override
    int getPeriod() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbePeriod",
                getKubernetesDeployerProperties().getStartupTcpProbePeriod());
    }

    @Override
    protected int getTimeout() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbeTimeout",
                getKubernetesDeployerProperties().getStartupTcpProbeTimeout());
    }

    @Override
    Integer getPort() {
        String probePortValue = getProbeProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbePort");

        if (StringUtils.hasText(probePortValue)) {
            if (!probePortValue.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("StartupTcpProbePort must contain all digits");
            }

            return Integer.parseInt(probePortValue);
        }

        if (getKubernetesDeployerProperties().getStartupTcpProbePort() != null) {
            return getKubernetesDeployerProperties().getStartupTcpProbePort();
        }

        throw new IllegalArgumentException("A startupTcpProbePort property must be set.");
    }

    @Override
    int getFailure() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbeFailure",
                getKubernetesDeployerProperties().getStartupTcpProbeFailure());
    }

    @Override
    int getSuccess() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Tcp", "ProbeSuccess",
                getKubernetesDeployerProperties().getStartupTcpProbeSuccess());
    }
}
