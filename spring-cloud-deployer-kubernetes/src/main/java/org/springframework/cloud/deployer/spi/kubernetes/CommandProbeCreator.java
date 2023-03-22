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

import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;

/**
 * Base class for command based probe creators
 *
 * @author Chris Schaefer
 * @author Corneil du Plessis
 * @since 2.5
 */
abstract class CommandProbeCreator extends ProbeCreator {
    CommandProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                        ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    abstract String[] getCommand();

    protected Probe create() {
        ExecActionBuilder execActionBuilder = new ExecActionBuilder()
                .withCommand(getCommand());

        return new ProbeBuilder()
                .withExec(execActionBuilder.build())
                .withInitialDelaySeconds(getInitialDelay())
                .withPeriodSeconds(getPeriod())
                .withSuccessThreshold(getSuccess())
                .withFailureThreshold(getFailure())
                .build();
    }
}
