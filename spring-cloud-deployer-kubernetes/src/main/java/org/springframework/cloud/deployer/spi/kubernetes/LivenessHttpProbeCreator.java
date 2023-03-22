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
 * Creates an HTTP Liveness probe
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
class LivenessHttpProbeCreator extends HttpProbeCreator {
    LivenessHttpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                             ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    @Override
    public Integer getPort() {
        String probePortValue = getProbeProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePort");

        if (StringUtils.hasText(probePortValue)) {
            return Integer.parseInt(probePortValue);
        }

        if (getKubernetesDeployerProperties().getLivenessHttpProbePort() != null) {
            return getKubernetesDeployerProperties().getLivenessHttpProbePort();
        }

        if (getDefaultPort() != null) {
            return getDefaultPort();
        }

        return null;
    }

    @Override
    protected String getProbePath() {
        String probePathValue = getProbeProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePath",
                getKubernetesDeployerProperties().getLivenessHttpProbePath());

        if (StringUtils.hasText(probePathValue)) {
            return probePathValue;
        }

        if (useBoot1ProbePath()) {
            return BOOT_1_LIVENESS_PROBE_PATH;
        }

        return BOOT_2_LIVENESS_PROBE_PATH;
    }

    @Override
    protected String getScheme() {
        String probeSchemeValue = getProbeProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeScheme",
                getKubernetesDeployerProperties().getLivenessHttpProbeScheme());

        if (StringUtils.hasText(probeSchemeValue)) {
            return probeSchemeValue;
        }

        return DEFAULT_PROBE_SCHEME;
    }

    @Override
    protected int getTimeout() {
        return getProbeIntProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeTimeout",
                getKubernetesDeployerProperties().getLivenessHttpProbeTimeout());
    }

    @Override
    protected int getInitialDelay() {
        return getProbeIntProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeDelay",
                getKubernetesDeployerProperties().getLivenessHttpProbeDelay());
    }

    @Override
    protected int getPeriod() {
        return getProbeIntProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbePeriod",
                getKubernetesDeployerProperties().getLivenessHttpProbePeriod());
    }

    @Override
    int getFailure() {
        return getProbeIntProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeFailure",
                getKubernetesDeployerProperties().getLivenessHttpProbeFailure());
    }

    @Override
    int getSuccess() {
        return getProbeIntProperty(LIVENESS_DEPLOYER_PROPERTY_PREFIX, "Http", "ProbeSuccess",
                getKubernetesDeployerProperties().getLivenessHttpProbeSuccess());
    }
}
