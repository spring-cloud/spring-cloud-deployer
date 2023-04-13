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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Secret;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Base class for HTTP based probe creators
 *
 * @author Chris Schaefer
 * @author Corneil du Plessis
 * @since 2.5
 */
public abstract class HttpProbeCreator extends ProbeCreator {
    private static final int BOOT_1_MAJOR_VERSION = 1;

    static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    static final String PROBE_CREDENTIALS_SECRET_KEY_NAME = "credentials";
    static final String BOOT_1_READINESS_PROBE_PATH = "/info";
    static final String BOOT_1_LIVENESS_PROBE_PATH = "/health";
    static final String BOOT_2_READINESS_PROBE_PATH = "/actuator" + BOOT_1_READINESS_PROBE_PATH;
    static final String BOOT_2_LIVENESS_PROBE_PATH = "/actuator" + BOOT_1_LIVENESS_PROBE_PATH;
    static final String DEFAULT_PROBE_SCHEME = "HTTP";

    HttpProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
                     ContainerConfiguration containerConfiguration) {
        super(kubernetesDeployerProperties, containerConfiguration);
    }

    protected abstract String getProbePath();

    protected abstract Integer getPort();

    protected abstract String getScheme();

    protected abstract int getTimeout();

    protected Probe create() {
        HTTPGetActionBuilder httpGetActionBuilder = new HTTPGetActionBuilder()
                .withPath(getProbePath())
                .withNewPort(getPort())
                .withScheme(getScheme());

        List<HTTPHeader> httpHeaders = getHttpHeaders();

        if (!httpHeaders.isEmpty()) {
            httpGetActionBuilder.withHttpHeaders(httpHeaders);
        }

        return new ProbeBuilder()
                .withHttpGet(httpGetActionBuilder.build())
                .withTimeoutSeconds(getTimeout())
                .withInitialDelaySeconds(getInitialDelay())
                .withPeriodSeconds(getPeriod())
                .withFailureThreshold(getFailure())
                .withSuccessThreshold(getSuccess())
                .build();

    }

    private List<HTTPHeader> getHttpHeaders() {
        List<HTTPHeader> httpHeaders = new ArrayList<>();

        HTTPHeader authenticationHeader = getAuthorizationHeader();

        if (authenticationHeader != null) {
            httpHeaders.add(authenticationHeader);
        }

        return httpHeaders;
    }

    private HTTPHeader getAuthorizationHeader() {
        HTTPHeader httpHeader = null;

        Secret probeCredentialsSecret = getContainerConfiguration().getProbeCredentialsSecret();

        if (probeCredentialsSecret != null) {
            Assert.isTrue(probeCredentialsSecret.getData().containsKey(PROBE_CREDENTIALS_SECRET_KEY_NAME),
                    "Secret does not contain a key by the name of " + PROBE_CREDENTIALS_SECRET_KEY_NAME);

            httpHeader = new HTTPHeader();
            httpHeader.setName(AUTHORIZATION_HEADER_NAME);

            // only Basic auth is supported currently
            httpHeader.setValue(ProbeAuthenticationType.Basic.name() + " " +
                    probeCredentialsSecret.getData().get(PROBE_CREDENTIALS_SECRET_KEY_NAME));
        }

        return httpHeader;
    }

    Integer getDefaultPort() {
        return getContainerConfiguration().getExternalPort();
    }

    boolean useBoot1ProbePath() {
        String bootMajorVersionProperty = KUBERNETES_DEPLOYER_PREFIX + ".bootMajorVersion";
        String bootMajorVersionValue = getDeploymentPropertyValue(bootMajorVersionProperty);

        if (StringUtils.hasText(bootMajorVersionValue)) {
            return Integer.parseInt(bootMajorVersionValue) == BOOT_1_MAJOR_VERSION;
        }

        return false;
    }

}
