/*
 * Copyright 2016-2022 the original author or authors.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultContainerFactory}.
 *
 * @author Will Kennedy
 * @author Donovan Muller
 * @author Chris Schaefer
 * @author David Turanski
 * @author Ilayaperumal Gopinathan
 * @author Glenn Renfro
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {KubernetesAutoConfiguration.class})
public class DefaultContainerFactoryTests {

    @Test
    public void create() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.limits.memory", "128Mi");
        props.put("spring.cloud.deployer.kubernetes.environment-variables",
                "JAVA_OPTIONS=-Xmx64m,KUBERNETES_NAMESPACE=test-space");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();
        assertThat(container.getEnv().size()).isEqualTo(3);
        EnvVar envVar1 = container.getEnv().get(0);
        EnvVar envVar2 = container.getEnv().get(1);
        assertThat(envVar1.getName()).isEqualTo("JAVA_OPTIONS");
        assertThat(envVar1.getValue()).isEqualTo("-Xmx64m");
        assertThat(envVar2.getName()).isEqualTo("KUBERNETES_NAMESPACE");
        assertThat(envVar2.getValue()).isEqualTo("test-space");
    }

    @Test
    public void createWithContainerCommand() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.containerCommand",
                "echo arg1 'arg2'");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();
        assertThat(container.getCommand()).containsExactly("echo", "arg1", "arg2");
    }

    @Test
    public void createWithPorts() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.containerPorts",
                "8081, 8082, 65535");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();
        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();
        assertThat(containerPorts.size()).isEqualTo(3);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8081);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8082);
        assertThat(containerPorts.get(2).getContainerPort()).isEqualTo(65535);
    }

    @Test
    public void createWithInvalidPort() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.containerPorts",
                "8081, 8082, invalid, 9212");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        //Attempting to create with an invalid integer set for a port should cause an exception to bubble up.
        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void createWithPortAndHostNetwork() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.containerPorts",
                "8081, 8082, 65535");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();
        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();
        assertThat(containerPorts.size()).isEqualTo(3);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8081);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8081);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8082);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8082);
        assertThat(containerPorts.get(2).getContainerPort()).isEqualTo(65535);
        assertThat(containerPorts.get(2).getHostPort()).isEqualTo(65535);
    }

    @Test
    public void createWithEntryPointStyle() throws JsonProcessingException {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProps = new HashMap<>();
        appProps.put("foo.bar.baz", "test");
        AppDefinition definition = new AppDefinition("app-test", appProps);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();

        props.put("spring.cloud.deployer.kubernetes.entryPointStyle", "shell");
        AppDeploymentRequest appDeploymentRequestShell = new AppDeploymentRequest(definition,
                resource, props);
        ContainerConfiguration shellContainerConfiguration = new ContainerConfiguration("app-test",
                appDeploymentRequestShell);
        Container containerShell = defaultContainerFactory.create(shellContainerConfiguration);
        assertThat(containerShell).isNotNull();

        assertThat(containerShell.getEnv().get(0).getName()).isEqualTo("FOO_BAR_BAZ");
        assertThat(containerShell.getArgs()).isEmpty();

        List<String> cmdLineArgs = new ArrayList<>();
        cmdLineArgs.add("--foo.bar=value1");
        cmdLineArgs.add("--spring.cloud.task.executionid=1");
        cmdLineArgs.add("--spring.cloud.data.flow.platformname=platform1");
        cmdLineArgs.add("--spring.cloud.data.flow.taskappname==a1");
        cmdLineArgs.add("blah=chacha");
        appDeploymentRequestShell = new AppDeploymentRequest(definition,
                resource, props, cmdLineArgs);
        shellContainerConfiguration = new ContainerConfiguration("app-test",
                appDeploymentRequestShell);
        containerShell = defaultContainerFactory.create(shellContainerConfiguration);
        assertThat(containerShell).isNotNull();
        assertThat(containerShell.getEnv()).hasSize(7);
        assertThat(containerShell.getArgs()).isEmpty();
        String envVarString = containerShell.getEnv().toString();
        assertThat(envVarString.contains("name=FOO_BAR_BAZ, value=test")).isTrue();
        assertThat(envVarString.contains("name=FOO_BAR, value=value1")).isTrue();
        assertThat(envVarString.contains("name=SPRING_CLOUD_TASK_EXECUTIONID, value=1")).isTrue();
        assertThat(envVarString.contains("name=SPRING_CLOUD_DATA_FLOW_TASKAPPNAME, value==a1")).isTrue();
        assertThat(envVarString.contains("name=SPRING_CLOUD_DATA_FLOW_PLATFORMNAME, value=platform1")).isTrue();
        assertThat(envVarString.contains("name=BLAH, value=chacha")).isTrue();

        props.put("spring.cloud.deployer.kubernetes.entryPointStyle", "exec");
        AppDeploymentRequest appDeploymentRequestExec = new AppDeploymentRequest(definition,
                resource, props);
        ContainerConfiguration execContainerConfiguration = new ContainerConfiguration("app-test",
                appDeploymentRequestExec);
        Container containerExec = defaultContainerFactory.create(execContainerConfiguration);
        assertThat(containerExec).isNotNull();
        assertThat(containerExec.getEnv()).hasSize(1);
        assertThat(containerExec.getArgs().get(0)).isEqualTo("--foo.bar.baz=test");

        props.put("spring.cloud.deployer.kubernetes.entryPointStyle", "boot");
        AppDeploymentRequest appDeploymentRequestBoot = new AppDeploymentRequest(definition,
                resource, props, Arrays.asList("--arg1=val1", "--arg2=val2"));
        ContainerConfiguration bootContainerConfiguration = new ContainerConfiguration("app-test",
                appDeploymentRequestBoot);
        Container containerBoot = defaultContainerFactory.create(bootContainerConfiguration);
        assertThat(containerBoot).isNotNull();
        assertThat(containerBoot.getEnv().get(0).getName()).isEqualTo("SPRING_APPLICATION_JSON");
        assertThat(containerBoot.getEnv().get(0).getValue()).isEqualTo(new ObjectMapper().writeValueAsString(appProps));
        assertThat(containerBoot.getArgs()).hasSize(2);
        assertThat(containerBoot.getArgs().get(0)).isEqualTo("--arg1=val1");
        assertThat(containerBoot.getArgs().get(1)).isEqualTo("--arg2=val2");
    }

    @Test
    public void createWithVolumeMounts() {
        // test volume mounts defined as deployer properties
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.volumeMounts",
                "["
                        + "{name: 'testhostpath', mountPath: '/test/hostPath'}, "
                        + "{name: 'testpvc', mountPath: '/test/pvc', readOnly: 'true'}, "
                        + "{name: 'testnfs', mountPath: '/test/nfs'}"
                        + "]");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container.getVolumeMounts()).containsOnly(
                new VolumeMount("/test/hostPath", null, "testhostpath", null, null, null),
                new VolumeMount("/test/pvc", null, "testpvc", true, null, null),
                new VolumeMount("/test/nfs", null, "testnfs", null, null, null));

        // test volume mounts defined as app deployment property, overriding the deployer property
        kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties
                .setVolumeMounts(Stream.of(
                                new VolumeMount("/test/hostPath", null, "testhostpath", false, null, null),
                                new VolumeMount("/test/pvc", null, "testpvc", true, null, null),
                                new VolumeMount("/test/nfs", null, "testnfs", false, null, null))
                        .collect(Collectors.toList()));
        defaultContainerFactory = new DefaultContainerFactory(kubernetesDeployerProperties);

        props.clear();
        props.put("spring.cloud.deployer.kubernetes.volumeMounts",
                "["
                        + "{name: 'testpvc', mountPath: '/test/pvc/overridden'}, "
                        + "{name: 'testnfs', mountPath: '/test/nfs/overridden', readOnly: 'true'}"
                        + "]");

        containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest);
        container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container.getVolumeMounts()).containsOnly(
                new VolumeMount("/test/hostPath", null, "testhostpath", false, null, null),
                new VolumeMount("/test/pvc/overridden", null, "testpvc", null, null, null),
                new VolumeMount("/test/nfs/overridden", null, "testnfs", true, null, null));
    }

    /**
     * @deprecated {@see {@link #createCustomLivenessHttpPortFromProperties()}}
     */
    @Test
    @Deprecated
    public void createCustomLivenessPortFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setLivenessProbePort(8090);
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080)
                .withHostNetwork(true);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).as("Only two container ports should be set").hasSize(2);
        assertThat((int) containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat((int) containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat((int) container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createCustomLivenessHttpPortFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setLivenessHttpProbePort(8090);
        kubernetesDeployerProperties.setStartupHttpProbePort(kubernetesDeployerProperties.getLivenessHttpProbePort());
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080)
                .withHostNetwork(true);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).as("Only two container ports should be set").hasSize(2);
        assertThat((int) containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat((int) containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat((int) container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createCustomLivenessHttpProbeSchemeFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setLivenessHttpProbeScheme("HTTPS");
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080)
                .withHostNetwork(true);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat((int) containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat((int) container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(container.getLivenessProbe().getHttpGet().getScheme()).isEqualTo("HTTPS");
    }

    @Test
    public void createCustomReadinessHttpSchemeFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setReadinessHttpProbeScheme("HTTPS");
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat((int) containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat((int) container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(container.getReadinessProbe().getHttpGet().getScheme()).isEqualTo("HTTPS");
    }

    @Test
    public void createCustomLivenessAndReadinessHttpProbeSchemeFromAppRequest() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.livenessHttpProbeScheme", "HTTPS");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessHttpProbeScheme", "HTTPS");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getScheme()).isEqualTo("HTTPS");

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getScheme()).isEqualTo("HTTPS");
    }

    /**
     * @deprecated {@see {@link #createCustomLivenessHttpPortFromAppRequest()}}
     */
    @Test
    @Deprecated
    public void createCustomLivenessPortFromAppRequest() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.liveness-probe-port", Integer.toString(8090));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createCustomLivenessHttpPortFromAppRequest() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.liveness-http-probe-port", Integer.toString(8090));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    /**
     * @deprecated {@see {@link #createCustomReadinessHttpPortFromAppRequest()}}
     */
    @Test
    @Deprecated
    public void createCustomReadinessPortFromAppRequest() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.readinessProbePort", Integer.toString(8090));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createCustomReadinessHttpPortFromAppRequest() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.readinessHttpProbePort", Integer.toString(8090));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    /**
     * @deprecated {@see {@link #createCustomReadinessHttpPortFromProperties()}}
     */
    @Test
    @Deprecated
    public void createCustomReadinessPortFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setReadinessProbePort(8090);
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createCustomReadinessHttpPortFromProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setReadinessHttpProbePort(8090);
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();

        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();

        assertThat(containerPorts).hasSize(2);
        assertThat(containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat(containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat(containerPorts.get(1).getContainerPort()).isEqualTo(8090);
        assertThat(containerPorts.get(1).getHostPort()).isEqualTo(8090);
        assertThat(container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8090);
    }

    @Test
    public void createDefaultHttpProbePorts() {
        int defaultPort = 8080;

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();
        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(defaultPort);
        Container container = defaultContainerFactory.create(containerConfiguration);
        assertThat(container).isNotNull();
        List<ContainerPort> containerPorts = container.getPorts();
        assertThat(containerPorts).isNotNull();
        assertThat(containerPorts).as("Only the default container port should set").hasSize(1);
        assertThat((int) containerPorts.get(0).getContainerPort()).isEqualTo(8080);
        assertThat((int) containerPorts.get(0).getHostPort()).isEqualTo(8080);
        assertThat((int) container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat((int) container.getReadinessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8080);
    }

    @Test
    public void createHttpProbesWithDefaultEndpoints() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();

        Map<String, String> props = new HashMap<>();
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, props);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo(HttpProbeCreator.BOOT_2_READINESS_PROBE_PATH);

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo(HttpProbeCreator.BOOT_2_LIVENESS_PROBE_PATH);
    }

    @Test
    public void createHttpProbesWithBoot1Endpoints() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.boot-major-version", "1");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo(HttpProbeCreator.BOOT_1_READINESS_PROBE_PATH);

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo(HttpProbeCreator.BOOT_1_LIVENESS_PROBE_PATH);
    }

    /**
     * @deprecated {@see {@link #createHttpProbesWithOverrides()}}
     */
    @Test
    @Deprecated
    public void createProbesWithOverrides() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.livenessProbePath", "/liveness");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessProbePath", "/readiness");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo("/readiness");

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/liveness");
    }

    @Test
    public void createHttpProbesWithOverrides() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.livenessHttpProbePath", "/liveness");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessHttpProbePath", "/readiness");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo("/readiness");

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/liveness");
    }

    /**
     * @deprecated {@see {@link #createHttpProbesWithPropertyOverrides()}}
     */
    @Test
    @Deprecated
    public void createProbesWithPropertyOverrides() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setReadinessProbePath("/readiness");
        kubernetesDeployerProperties.setLivenessProbePath("/liveness");
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, null);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo("/readiness");

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/liveness");
    }

    @Test
    public void createHttpProbesWithPropertyOverrides() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setReadinessHttpProbePath("/readiness");
        kubernetesDeployerProperties.setLivenessHttpProbePath("/liveness");
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition,
                resource, null);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getReadinessProbe().getHttpGet().getPath()).isEqualTo("/readiness");

        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isNotNull();
        assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/liveness");
    }

    @Test
    public void testHttpProbeCredentialsSecret() {
        Secret secret = randomSecret();
        String secretName = secret.getMetadata().getName();

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeCredentialsSecret", secretName);

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080)
                .withProbeCredentialsSecret(secret);

        ContainerFactory containerFactory = new DefaultContainerFactory(new KubernetesDeployerProperties());
        Container container = containerFactory.create(containerConfiguration);

        String credentials = containerConfiguration.getProbeCredentialsSecret().getData()
                .get(HttpProbeCreator.PROBE_CREDENTIALS_SECRET_KEY_NAME);

        HTTPHeader livenessProbeHeader = container.getLivenessProbe().getHttpGet().getHttpHeaders().get(0);
        assertThat(livenessProbeHeader.getName()).isEqualTo(HttpProbeCreator.AUTHORIZATION_HEADER_NAME);
        assertThat(livenessProbeHeader.getValue()).isEqualTo(ProbeAuthenticationType.Basic.name() + " " + credentials);

        HTTPHeader readinessProbeHeader = container.getReadinessProbe().getHttpGet().getHttpHeaders().get(0);
        assertThat(readinessProbeHeader.getName()).isEqualTo(HttpProbeCreator.AUTHORIZATION_HEADER_NAME);
        assertThat(readinessProbeHeader.getValue()).isEqualTo(ProbeAuthenticationType.Basic.name() + " " + credentials);
    }

    @Test
    public void testHttpProbeCredentialsInvalidSecret() {
        Secret secret = randomSecret();
        secret.setData(Collections.singletonMap("unexpectedkey", "dXNlcjpwYXNz"));

        String secretName = secret.getMetadata().getName();

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeCredentialsSecret", secretName);

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080)
                .withProbeCredentialsSecret(secret);

        ContainerFactory containerFactory = new DefaultContainerFactory(new KubernetesDeployerProperties());
        assertThatThrownBy(() -> {
            containerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testHttpProbeHeadersWithoutAuth() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource());

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withExternalPort(8080);

        ContainerFactory containerFactory = new DefaultContainerFactory(new KubernetesDeployerProperties());
        Container container = containerFactory.create(containerConfiguration);

        assertThat(container.getLivenessProbe().getHttpGet().getHttpHeaders()).isEmpty();
        assertThat(container.getReadinessProbe().getHttpGet().getHttpHeaders()).isEmpty();
    }

    @Test
    public void createTcpProbe() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.readinessTcpProbePort", "5050");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessTcpProbePort", "9090");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "9090");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(5050).withContainerPort(5050).build());
        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(9090).withContainerPort(9090).build());

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        Integer livenessTcpProbePort = livenessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(livenessTcpProbePort).isNotNull();
        assertThat(livenessTcpProbePort.intValue()).isEqualTo(9090);

        Integer readinessTcpProbePort = readinessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(readinessTcpProbePort).isNotNull();
        assertThat(readinessTcpProbePort.intValue()).isEqualTo(5050);

        assertThat(livenessProbe.getPeriodSeconds()).isNotNull();
        assertThat(readinessProbe.getPeriodSeconds()).isNotNull();

        assertThat(livenessProbe.getInitialDelaySeconds()).isNotNull();
        assertThat(readinessProbe.getInitialDelaySeconds()).isNotNull();
    }

    @Test
    public void createTcpProbeMissingLivenessPort() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.readinessTcpProbePort", "5050");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "9090");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The livenessTcpProbePort property must be set");
    }

    @Test
    public void createTcpProbeMissingReadinessPort() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.livenessTcpProbePort", "5050");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "5050");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A readinessTcpProbePort property must be set");
    }

    @Test
    public void createReadinessTcpProbeWithNonDigitPort() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.livenessTcpProbePort", "9090");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessTcpProbePort", "somePort");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "9090");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ReadinessTcpProbePort must contain all digits");
    }

    @Test
    public void createLivenessTcpProbeWithNonDigitPort() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.livenessTcpProbePort", "somePort");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessTcpProbePort", "5050");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "9090");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LivenessTcpProbePort must contain all digits");
    }

    @Test
    public void createTcpProbeGlobalProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setProbeType(ProbeType.TCP);
        kubernetesDeployerProperties.setReadinessTcpProbePort(5050);
        kubernetesDeployerProperties.setReadinessTcpProbeDelay(1);
        kubernetesDeployerProperties.setReadinessTcpProbePeriod(2);
        kubernetesDeployerProperties.setLivenessTcpProbePort(9090);
        kubernetesDeployerProperties.setLivenessTcpProbeDelay(3);
        kubernetesDeployerProperties.setLivenessTcpProbePeriod(4);
        kubernetesDeployerProperties.setStartupTcpProbePort(9090);

        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, null);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(5050).withContainerPort(5050).build());
        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(9090).withContainerPort(9090).build());

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        Integer livenessTcpProbePort = livenessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(livenessTcpProbePort).isNotNull();
        assertThat(livenessTcpProbePort.intValue()).isEqualTo(9090);

        Integer readinessTcpProbePort = readinessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(readinessTcpProbePort).isNotNull();
        assertThat(readinessTcpProbePort.intValue()).isEqualTo(5050);

        Integer livenessProbePeriodSeconds = livenessProbe.getPeriodSeconds();
        assertThat(livenessProbePeriodSeconds).isNotNull();
        assertThat(livenessProbePeriodSeconds.intValue()).isEqualTo(4);

        Integer readinessProbePeriodSeconds = readinessProbe.getPeriodSeconds();
        assertThat(readinessProbePeriodSeconds).isNotNull();
        assertThat(readinessProbePeriodSeconds.intValue()).isEqualTo(2);

        Integer livenessProbeInitialDelaySeconds = livenessProbe.getInitialDelaySeconds();
        assertThat(livenessProbeInitialDelaySeconds).isNotNull();
        assertThat(livenessProbeInitialDelaySeconds.intValue()).isEqualTo(3);

        Integer readinessProbeInitialDelaySeconds = readinessProbe.getInitialDelaySeconds();
        assertThat(readinessProbeInitialDelaySeconds).isNotNull();
        assertThat(readinessProbeInitialDelaySeconds.intValue()).isEqualTo(1);
    }

    @Test
    public void createTcpProbeGlobalPropertyOverride() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setProbeType(ProbeType.TCP);
        kubernetesDeployerProperties.setReadinessTcpProbePort(5050);
        kubernetesDeployerProperties.setReadinessTcpProbeDelay(1);
        kubernetesDeployerProperties.setReadinessTcpProbePeriod(2);
        kubernetesDeployerProperties.setLivenessTcpProbePort(9090);
        kubernetesDeployerProperties.setLivenessTcpProbeDelay(3);
        kubernetesDeployerProperties.setLivenessTcpProbePeriod(4);

        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.TCP.name());
        appProperties.put("spring.cloud.deployer.kubernetes.readinessTcpProbePort", "5050");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessProbePeriod", "11");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessProbeDelay", "12");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessTcpProbePort", "9090");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessProbePeriod", "13");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessProbeDelay", "14");
        appProperties.put("spring.cloud.deployer.kubernetes.startupTcpProbePort", "9090");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(5050).withContainerPort(5050).build());
        assertThat(container.getPorts())
                .contains(new ContainerPortBuilder().withHostPort(9090).withContainerPort(9090).build());

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        Integer livenessTcpProbePort = livenessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(livenessTcpProbePort).isNotNull();
        assertThat(livenessTcpProbePort.intValue()).isEqualTo(9090);

        Integer readinessTcpProbePort = readinessProbe.getTcpSocket().getPort().getIntVal();
        assertThat(readinessTcpProbePort).isNotNull();
        assertThat(readinessTcpProbePort.intValue()).isEqualTo(5050);

        Integer livenessProbePeriodSeconds = livenessProbe.getPeriodSeconds();
        assertThat(livenessProbePeriodSeconds).isNotNull();
        assertThat(livenessProbePeriodSeconds.intValue()).isEqualTo(13);

        Integer readinessProbePeriodSeconds = readinessProbe.getPeriodSeconds();
        assertThat(readinessProbePeriodSeconds).isNotNull();
        assertThat(readinessProbePeriodSeconds.intValue()).isEqualTo(11);

        Integer livenessProbeInitialDelaySeconds = livenessProbe.getInitialDelaySeconds();
        assertThat(livenessProbeInitialDelaySeconds).isNotNull();
        assertThat(livenessProbeInitialDelaySeconds.intValue()).isEqualTo(14);

        Integer readinessProbeInitialDelaySeconds = readinessProbe.getInitialDelaySeconds();
        assertThat(readinessProbeInitialDelaySeconds).isNotNull();
        assertThat(readinessProbeInitialDelaySeconds.intValue()).isEqualTo(12);
    }

    @Test
    public void createCommandProbe() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.COMMAND.name());
        appProperties.put("spring.cloud.deployer.kubernetes.readinessCommandProbeCommand", "ls /");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessCommandProbeCommand", "ls /dev");
        appProperties.put("spring.cloud.deployer.kubernetes.startupCommandProbeCommand", "ls /dev");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        List<String> livenessCommandProbeCommand = livenessProbe.getExec().getCommand();
        assertThat(livenessCommandProbeCommand).containsExactly("ls", "/dev");

        List<String> readinessCommandProbeCommand = readinessProbe.getExec().getCommand();
        assertThat(readinessCommandProbeCommand).containsExactly("ls", "/");

        assertThat(livenessProbe.getPeriodSeconds()).isNotNull();
        assertThat(readinessProbe.getPeriodSeconds()).isNotNull();

        assertThat(livenessProbe.getInitialDelaySeconds()).isNotNull();
        assertThat(readinessProbe.getInitialDelaySeconds()).isNotNull();
    }

    @Test
    public void createCommandProbeMissingCommand() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.COMMAND.name());
        appProperties.put("spring.cloud.deployer.kubernetes.startupCommandProbeCommand", "ls /");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        assertThatThrownBy(() -> {
            defaultContainerFactory.create(containerConfiguration);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The readinessCommandProbeCommand property must be set");
    }

    @Test
    public void createCommandProbeGlobalProperties() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setProbeType(ProbeType.COMMAND);
        kubernetesDeployerProperties.setReadinessCommandProbeCommand("ls /");
        kubernetesDeployerProperties.setReadinessCommandProbeDelay(1);
        kubernetesDeployerProperties.setReadinessCommandProbePeriod(2);
        kubernetesDeployerProperties.setLivenessCommandProbeCommand("ls /dev");
        kubernetesDeployerProperties.setLivenessCommandProbeDelay(3);
        kubernetesDeployerProperties.setLivenessCommandProbePeriod(4);
        kubernetesDeployerProperties.setStartupCommandProbeCommand("ls /dev");

        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        AppDefinition definition = new AppDefinition("app-test", null);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, null);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        String livenessTcpProbeCommand = String.join(" ", livenessProbe.getExec().getCommand());
        assertThat(livenessTcpProbeCommand).isNotNull();
        assertThat(livenessTcpProbeCommand).isEqualTo("ls /dev");

        String readinessTcpProbeCommand = String.join(" ", readinessProbe.getExec().getCommand());
        assertThat(readinessTcpProbeCommand).isNotNull();
        assertThat(readinessTcpProbeCommand).isEqualTo("ls /");

        Integer livenessProbePeriodSeconds = livenessProbe.getPeriodSeconds();
        assertThat(livenessProbePeriodSeconds).isNotNull();
        assertThat(livenessProbePeriodSeconds.intValue()).isEqualTo(4);

        Integer readinessProbePeriodSeconds = readinessProbe.getPeriodSeconds();
        assertThat(readinessProbePeriodSeconds).isNotNull();
        assertThat(readinessProbePeriodSeconds.intValue()).isEqualTo(2);

        Integer livenessProbeInitialDelaySeconds = livenessProbe.getInitialDelaySeconds();
        assertThat(livenessProbeInitialDelaySeconds).isNotNull();
        assertThat(livenessProbeInitialDelaySeconds.intValue()).isEqualTo(3);

        Integer readinessProbeInitialDelaySeconds = readinessProbe.getInitialDelaySeconds();
        assertThat(readinessProbeInitialDelaySeconds).isNotNull();
        assertThat(readinessProbeInitialDelaySeconds.intValue()).isEqualTo(1);
    }

    @Test
    public void createCommandProbeGlobalPropertyOverride() {
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setProbeType(ProbeType.COMMAND);
        kubernetesDeployerProperties.setReadinessCommandProbeCommand("ls /");
        kubernetesDeployerProperties.setReadinessCommandProbeDelay(1);
        kubernetesDeployerProperties.setReadinessCommandProbePeriod(2);
        kubernetesDeployerProperties.setLivenessCommandProbeCommand("ls /dev");
        kubernetesDeployerProperties.setLivenessCommandProbeDelay(3);
        kubernetesDeployerProperties.setLivenessCommandProbePeriod(4);

        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);

        Map<String, String> appProperties = new HashMap<>();
        appProperties.put("spring.cloud.deployer.kubernetes.probeType", ProbeType.COMMAND.name());
        appProperties.put("spring.cloud.deployer.kubernetes.readinessCommandProbeCommand", "ls /");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessProbePeriod", "11");
        appProperties.put("spring.cloud.deployer.kubernetes.readinessProbeDelay", "12");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessCommandProbeCommand", "ls /dev");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessProbePeriod", "13");
        appProperties.put("spring.cloud.deployer.kubernetes.livenessProbeDelay", "14");
        appProperties.put("spring.cloud.deployer.kubernetes.startupCommandProbeCommand", "ls /dev");

        AppDefinition definition = new AppDefinition("app-test", appProperties);
        Resource resource = getResource();

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, resource, appProperties);

        ContainerConfiguration containerConfiguration = new ContainerConfiguration("app-test", appDeploymentRequest)
                .withHostNetwork(true)
                .withExternalPort(8080);

        Container container = defaultContainerFactory.create(containerConfiguration);

        assertThat(container).isNotNull();

        Probe livenessProbe = container.getLivenessProbe();
        Probe readinessProbe = container.getReadinessProbe();

        assertThat(livenessProbe).isNotNull();
        assertThat(readinessProbe).isNotNull();

        String livenessTcpProbeCommand = String.join(" ", livenessProbe.getExec().getCommand());
        assertThat(livenessTcpProbeCommand).isNotNull();
        assertThat(livenessTcpProbeCommand).isEqualTo("ls /dev");

        String readinessTcpProbeCommand = String.join(" ", readinessProbe.getExec().getCommand());
        assertThat(readinessTcpProbeCommand).isNotNull();
        assertThat(readinessTcpProbeCommand).isEqualTo("ls /");

        Integer livenessProbePeriodSeconds = livenessProbe.getPeriodSeconds();
        assertThat(livenessProbePeriodSeconds).isNotNull();
        assertThat(livenessProbePeriodSeconds.intValue()).isEqualTo(13);

        Integer readinessProbePeriodSeconds = readinessProbe.getPeriodSeconds();
        assertThat(readinessProbePeriodSeconds).isNotNull();
        assertThat(readinessProbePeriodSeconds.intValue()).isEqualTo(11);

        Integer livenessProbeInitialDelaySeconds = livenessProbe.getInitialDelaySeconds();
        assertThat(livenessProbeInitialDelaySeconds).isNotNull();
        assertThat(livenessProbeInitialDelaySeconds.intValue()).isEqualTo(14);

        Integer readinessProbeInitialDelaySeconds = readinessProbe.getInitialDelaySeconds();
        assertThat(readinessProbeInitialDelaySeconds).isNotNull();
        assertThat(readinessProbeInitialDelaySeconds.intValue()).isEqualTo(12);
    }

    @Test
    public void testCommandLineArgsOverridesExistingProperties() {
        AppDefinition definition = new AppDefinition("app-test", Collections.singletonMap("foo", "bar"));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null,
                Collections.singletonList("--foo=newValue"));

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);
        assertThat(defaultContainerFactory.createCommandArgs(appDeploymentRequest)).containsExactly("--foo=newValue");
    }

    @Test
    public void testCommandLineArgsNoAssignment() {
        List<String> args = new ArrayList();
        args.add("a");
        args.add("--b = c");
        args.add("d=e");
        args.add("f = g");
        AppDefinition definition = new AppDefinition("app-test", Collections.singletonMap("b", "d"));
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null,
                args);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);
        assertThat(defaultContainerFactory.createCommandArgs(appDeploymentRequest)).containsExactly("a", "--b = c", "d=e", "f = g");
    }

    @Test
    public void testCommandLineArgsExcludesMalformedProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("sun.cpu.isalist", "");
        properties.put("foo", "bar");
        AppDefinition definition = new AppDefinition("app-test", properties);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource());

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        DefaultContainerFactory defaultContainerFactory = new DefaultContainerFactory(
                kubernetesDeployerProperties);
        assertThat(defaultContainerFactory.createCommandArgs(appDeploymentRequest)).containsExactly("--foo=bar");
    }

    private Resource getResource() {
        return new DockerResource(
                "springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

    private Secret randomSecret() {
        String secretName = "secret-" + UUID.randomUUID().toString().substring(0, 18);
        String secretValue = "dXNlcjpwYXNz"; // base64 encoded string of: user:pass

        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(secretName);

        Secret secret = new Secret();
        secret.setData(Collections.singletonMap(HttpProbeCreator.PROBE_CREDENTIALS_SECRET_KEY_NAME, secretValue));
        secret.setMetadata(objectMeta);

        return secret;
    }
}
