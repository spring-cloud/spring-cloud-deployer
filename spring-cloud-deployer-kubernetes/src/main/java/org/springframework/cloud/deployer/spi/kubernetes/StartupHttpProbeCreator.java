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
 * Creates an HTTP Startup Probe.
 *
 * @author Corneil du Plessis
 */
class StartupHttpProbeCreator extends HttpProbeCreator {
    StartupHttpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                            ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    @Override
    public Integer getPort() {
        String probePortValue = getProbeProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePort");

        if (StringUtils.hasText(probePortValue)) {
            if (!probePortValue.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("StartupHttpProbeCreator must contain all digits");
            }

            return Integer.parseInt(probePortValue);
        }

        if (getKubernetesDeployerProperties().getStartupHttpProbePort() != null) {
            return getKubernetesDeployerProperties().getStartupHttpProbePort();
        }

        if (getDefaultPort() != null) {
            return getDefaultPort();
        }

        return null;
    }

    @Override
    protected String getProbePath() {
        String probePathValue = getProbeProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePath");

        if (StringUtils.hasText(probePathValue)) {
            return probePathValue;
        }

        if (getKubernetesDeployerProperties().getStartupHttpProbePath() != null) {
            return getKubernetesDeployerProperties().getStartupHttpProbePath();
        }

        if (useBoot1ProbePath()) {
            return BOOT_1_READINESS_PROBE_PATH;
        }

        return BOOT_2_READINESS_PROBE_PATH;
    }

    @Override
    protected String getScheme() {
        String probeSchemeValue = getProbeProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeScheme");

        if (StringUtils.hasText(probeSchemeValue)) {
            return probeSchemeValue;
        }

        if (getKubernetesDeployerProperties().getStartupProbeScheme() != null) {
            return getKubernetesDeployerProperties().getStartupProbeScheme();
        }

        return DEFAULT_PROBE_SCHEME;
    }

    @Override
    protected int getTimeout() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeTimeout",
                getKubernetesDeployerProperties().getStartupHttpProbeTimeout());
    }

    @Override
    protected int getInitialDelay() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeDelay",
                getKubernetesDeployerProperties().getStartupHttpProbeDelay());
    }

    @Override
    protected int getPeriod() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePeriod",
                getKubernetesDeployerProperties().getStartupHttpProbePeriod());
    }

    @Override
    int getFailure() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeFailure",
                getKubernetesDeployerProperties().getStartupHttpProbeFailure());
    }

    @Override
    int getSuccess() {
        return getProbeIntProperty(STARTUP_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeSuccess",
                getKubernetesDeployerProperties().getStartupHttpProbeSuccess());
    }
}
