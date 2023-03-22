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

import org.springframework.util.StringUtils;

/**
 * Creates an HTTP Readiness Probe.
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
class ReadinessHttpProbeCreator extends HttpProbeCreator {
    ReadinessHttpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                              ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    @Override
    public Integer getPort() {
        String probePortValue = getProbeProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePort");

        if (StringUtils.hasText(probePortValue)) {
            if (!probePortValue.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("ReadinessHttpProbeCreator must contain all digits");
            }

            return Integer.parseInt(probePortValue);
        }

        if (getKubernetesDeployerProperties().getReadinessHttpProbePort() != null) {
            return getKubernetesDeployerProperties().getReadinessHttpProbePort();
        }

        if (getDefaultPort() != null) {
            return getDefaultPort();
        }

        return null;
    }

    @Override
    protected String getProbePath() {
        String probePathValue = getProbeProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePath");

        if (StringUtils.hasText(probePathValue)) {
            return probePathValue;
        }

        if (getKubernetesDeployerProperties().getReadinessHttpProbePath() != null) {
            return getKubernetesDeployerProperties().getReadinessHttpProbePath();
        }

        if (useBoot1ProbePath()) {
            return BOOT_1_READINESS_PROBE_PATH;
        }

        return BOOT_2_READINESS_PROBE_PATH;
    }

    @Override
    protected String getScheme() {
        String probeSchemeValue = getProbeProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeScheme");

        if (StringUtils.hasText(probeSchemeValue)) {
            return probeSchemeValue;
        }

        if (getKubernetesDeployerProperties().getReadinessHttpProbeScheme() != null) {
            return getKubernetesDeployerProperties().getReadinessHttpProbeScheme();
        }

        return DEFAULT_PROBE_SCHEME;
    }

    @Override
    protected int getTimeout() {
        return getProbeIntProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeTimeout",
                getKubernetesDeployerProperties().getReadinessHttpProbeTimeout());
    }

    @Override
    protected int getInitialDelay() {
        return getProbeIntProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeDelay",
                getKubernetesDeployerProperties().getReadinessHttpProbeDelay());
    }

    @Override
    protected int getPeriod() {
        return getProbeIntProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePeriod",
                getKubernetesDeployerProperties().getReadinessHttpProbePeriod());
    }

    @Override
    int getFailure() {
        return getProbeIntProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeFailure",
                getKubernetesDeployerProperties().getReadinessHttpProbeFailure());
    }

    @Override
    int getSuccess() {
        return getProbeIntProperty(READINESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeSuccess",
                getKubernetesDeployerProperties().getReadinessHttpProbeSuccess());
    }
}
