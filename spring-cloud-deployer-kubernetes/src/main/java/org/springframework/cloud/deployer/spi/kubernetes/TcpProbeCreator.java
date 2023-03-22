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
import io.fabric8.kubernetes.api.model.ProbeBuilder;

/**
 * Base class for TCP based probe creators
 *
 * @author Chris Schaefer
 * @since 2.5
 */
abstract class TcpProbeCreator extends ProbeCreator {
    TcpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                    ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    abstract Integer getPort();

    protected abstract int getTimeout();

    protected Probe create() {
        return new ProbeBuilder()
                .withNewTcpSocket()
                .withNewPort(getPort())
                .endTcpSocket()
                .withInitialDelaySeconds(getInitialDelay())
                .withPeriodSeconds(getPeriod())
                .withFailureThreshold(getFailure())
                .withSuccessThreshold(getSuccess())
                .build();
    }
}
