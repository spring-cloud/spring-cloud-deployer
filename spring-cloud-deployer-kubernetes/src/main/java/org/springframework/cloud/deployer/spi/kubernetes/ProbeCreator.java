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

import java.util.Map;

import io.fabric8.kubernetes.api.model.Probe;

import org.springframework.cloud.deployer.spi.kubernetes.support.PropertyParserUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for creating Probe's
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
abstract class ProbeCreator {
    static final String KUBERNETES_DEPLOYER_PREFIX = "spring.cloud.deployer.kubernetes";
    static final String LIVENESS_DEPLOYER_PROPERTY_PREFIX = KUBERNETES_DEPLOYER_PREFIX + ".liveness";
    static final String READINESS_DEPLOYER_PROPERTY_PREFIX = KUBERNETES_DEPLOYER_PREFIX + ".readiness";
    static final String STARTUP_DEPLOYER_PROPERTY_PREFIX = KUBERNETES_DEPLOYER_PREFIX + ".startup";

    private ContainerConfiguration containerConfiguration;
    private KubernetesDeployerProperties kubernetesDeployerProperties;

    ProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                 ContainerConfiguration containerConfiguration) {
        this.containerConfiguration = containerConfiguration;
        this.kubernetesDeployerProperties = kubernetesDeployerProperties;
    }

    abstract Probe create();

    abstract int getInitialDelay();

    abstract int getPeriod();

    abstract int getFailure();

    abstract int getSuccess();

    KubernetesDeployerProperties getKubernetesDeployerProperties() {
        return kubernetesDeployerProperties;
    }

    private Map<String, String> getDeploymentProperties() {
        return this.containerConfiguration.getAppDeploymentRequest().getDeploymentProperties();
    }

    protected String getDeploymentPropertyValue(String propertyName) {
        return PropertyParserUtils.getDeploymentPropertyValue(getDeploymentProperties(), propertyName);
    }
    protected String getDeploymentPropertyValue(String propertyName, String defaultValue) {
        return PropertyParserUtils.getDeploymentPropertyValue(getDeploymentProperties(), propertyName, defaultValue);
    }

    ContainerConfiguration getContainerConfiguration() {
        return containerConfiguration;
    }

    // used to resolve deprecated HTTP probe property names that do not include "Http" in them
    // can be removed when deprecated HTTP probes without "Http" in them get removed
    String getProbeProperty(String propertyPrefix, String probeName, String propertySuffix) {
        String defaultValue = getDeploymentPropertyValue(propertyPrefix + probeName + propertySuffix);
        return StringUtils.hasText(defaultValue) ? defaultValue :
                getDeploymentPropertyValue(propertyPrefix + propertySuffix);
    }

    String getProbeProperty(String propertyPrefix, String probeName, String propertySuffix, String defaultValue) {
        return getDeploymentPropertyValue(propertyPrefix + probeName + propertySuffix,
                getDeploymentPropertyValue(propertyPrefix + propertySuffix, defaultValue)
        );
    }
    int getProbeIntProperty(String propertyPrefix, String probeName, String propertySuffix, int defaultValue) {
        String propertyValue = getDeploymentPropertyValue(propertyPrefix + probeName + propertySuffix,
                getDeploymentPropertyValue(propertyPrefix + propertySuffix)
        );
        return StringUtils.hasText(propertyValue) ? Integer.parseInt(propertyValue) : defaultValue;
    }
}
