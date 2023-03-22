/*
 * Copyright 2015-2022 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.ObjectFieldSelector;
import io.fabric8.kubernetes.api.model.Probe;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Create a Kubernetes {@link Container} that will be started as part of a
 * Kubernetes Pod by launching the specified Docker image.
 *
 * @author Florian Rosenberg
 * @author Thomas Risberg
 * @author Donovan Muller
 * @author David Turanski
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Glenn Renfro
 * @author Corneil du Plessis
 */
public class DefaultContainerFactory implements ContainerFactory {
    private static Log logger = LogFactory.getLog(DefaultContainerFactory.class);
    private static final String SPRING_APPLICATION_JSON = "SPRING_APPLICATION_JSON";
    private static final String SPRING_CLOUD_APPLICATION_GUID = "SPRING_CLOUD_APPLICATION_GUID";

    private final KubernetesDeployerProperties properties;

    public DefaultContainerFactory(KubernetesDeployerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Container create(ContainerConfiguration containerConfiguration) {
        AppDeploymentRequest request = containerConfiguration.getAppDeploymentRequest();
        Map<String, String> deploymentProperties = getDeploymentProperties(request);
        DeploymentPropertiesResolver deploymentPropertiesResolver = getDeploymentPropertiesResolver(request);

        String image;
        try {
            image = request.getResource().getURI().getSchemeSpecificPart();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get URI for " + request.getResource(), e);
        }
        logger.info("Using Docker image: " + image);

        EntryPointStyle entryPointStyle = deploymentPropertiesResolver.determineEntryPointStyle(deploymentProperties);
        logger.info("Using Docker entry point style: " + entryPointStyle);

        Map<String, String> envVarsMap = new HashMap<>();
        for (String envVar : this.properties.getEnvironmentVariables()) {
            String[] strings = envVar.split("=", 2);
            Assert.isTrue(strings.length == 2, "Invalid environment variable declared: " + envVar);
            envVarsMap.put(strings[0], strings[1]);
        }
        //Create EnvVar entries for additional variables set at the app level
        //For instance, this may be used to set JAVA_OPTS independently for each app if the base container
        //image supports it.
        envVarsMap.putAll(deploymentPropertiesResolver.getAppEnvironmentVariables(deploymentProperties));

        List<String> appArgs = new ArrayList<>();

        Map<String, String> appAdminCredentials = new HashMap<>();
        properties.getAppAdmin().addCredentialsToAppEnvironmentAsProperties(appAdminCredentials);

        switch (entryPointStyle) {
            case exec:
                appArgs = createCommandArgs(request);
                List<String> finalAppArgs = appArgs;
                appAdminCredentials.forEach((k, v) -> finalAppArgs.add(String.format("--%s=%s", k, v)));


                break;
            case boot:
                if (envVarsMap.containsKey(SPRING_APPLICATION_JSON)) {
                    throw new IllegalStateException(
                            "You can't use boot entry point style and also set SPRING_APPLICATION_JSON for the app");
                }
                try {
                    envVarsMap.put(SPRING_APPLICATION_JSON,
                            new ObjectMapper().writeValueAsString(request.getDefinition().getProperties()));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Unable to create SPRING_APPLICATION_JSON", e);
                }

                appArgs = request.getCommandlineArguments();

                break;
            case shell:
                for (String key : request.getDefinition().getProperties().keySet()) {
                    String envVar = key.replace('.', '_').toUpperCase();
                    envVarsMap.put(envVar, request.getDefinition().getProperties().get(key));
                    envVarsMap.putAll(appAdminCredentials);

                }
                // Push all the command line arguments as environment properties
                // The task app name(in case of Composed Task), platform_name and executionId are expected to be updated.
                // This will also override any of the existing app properties that match the provided cmdline args.
                for (String cmdLineArg : request.getCommandlineArguments()) {
                    String cmdLineArgKey;

                    if (cmdLineArg.startsWith("--")) {
                        cmdLineArgKey = cmdLineArg.substring(2, cmdLineArg.indexOf("="));
                    } else {
                        cmdLineArgKey = cmdLineArg.substring(0, cmdLineArg.indexOf("="));
                    }

                    String cmdLineArgValue = cmdLineArg.substring(cmdLineArg.indexOf("=") + 1);
                    envVarsMap.put(cmdLineArgKey.replace('.', '_').toUpperCase(), cmdLineArgValue);
                }
                break;
        }

        List<EnvVar> envVars = new ArrayList<>();
        for (Map.Entry<String, String> e : envVarsMap.entrySet()) {
            envVars.add(new EnvVar(e.getKey(), e.getValue(), null));
        }

        envVars.addAll(deploymentPropertiesResolver.getSecretKeyRefs(deploymentProperties));
        envVars.addAll(deploymentPropertiesResolver.getConfigMapKeyRefs(deploymentProperties));
        envVars.add(getGUIDEnvVar());

        if (request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY) != null) {
            envVars.add(new EnvVar("SPRING_CLOUD_APPLICATION_GROUP",
                    request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY), null));
        }

        List<EnvFromSource> envFromSources = new ArrayList<>();
        envFromSources.addAll(deploymentPropertiesResolver.getConfigMapRefs(deploymentProperties));
        envFromSources.addAll(deploymentPropertiesResolver.getSecretRefs(deploymentProperties));

        ContainerBuilder container = new ContainerBuilder();
        container.withName(containerConfiguration.getAppId()).withImage(image).withEnv(envVars).withEnvFrom(envFromSources)
                .withArgs(appArgs).withVolumeMounts(deploymentPropertiesResolver.getVolumeMounts(deploymentProperties));

        Set<Integer> ports = new HashSet<>();

        Integer defaultPort = containerConfiguration.getExternalPort();

        if (defaultPort != null) {
            ports.add(defaultPort);
        }

        ports.addAll(deploymentPropertiesResolver.getContainerPorts(deploymentProperties));
        configureStartupProbe(containerConfiguration, container, ports);
        configureReadinessProbe(containerConfiguration, container, ports);
        configureLivenessProbe(containerConfiguration, container, ports);

        if (!ports.isEmpty()) {
            for (Integer containerPort : ports) {
                if (containerConfiguration.isHostNetwork()) {
                    container.addNewPort().withContainerPort(containerPort).withHostPort(containerPort).endPort();
                } else {
                    container.addNewPort().withContainerPort(containerPort).endPort();
                }
            }
        }

        //Override the containers default entry point with one specified during the app deployment
        List<String> containerCommand = deploymentPropertiesResolver.getContainerCommand(deploymentProperties);
        if (!containerCommand.isEmpty()) {
            container.withCommand(containerCommand);
        }
        return container.build();
    }

    private EnvVar getGUIDEnvVar() {
        ObjectFieldSelector objectFieldSelector = new ObjectFieldSelector();
        objectFieldSelector.setFieldPath("metadata.uid");

        EnvVarSource envVarSource = new EnvVarSource();
        envVarSource.setFieldRef(objectFieldSelector);

        EnvVar guidEnvVar = new EnvVar();
        guidEnvVar.setValueFrom(envVarSource);
        guidEnvVar.setName(SPRING_CLOUD_APPLICATION_GUID);

        return guidEnvVar;
    }

    private void configureReadinessProbe(ContainerConfiguration containerConfiguration,
                                         ContainerBuilder containerBuilder, Set<Integer> ports) {
        Probe readinessProbe = ProbeCreatorFactory.createReadinessProbe(containerConfiguration, properties,
                getProbeType(containerConfiguration));

        Integer probePort = null;

        if (readinessProbe.getHttpGet() != null) {
            probePort = readinessProbe.getHttpGet().getPort().getIntVal();
        }

        if (readinessProbe.getTcpSocket() != null) {
            probePort = readinessProbe.getTcpSocket().getPort().getIntVal();
        }

        if (probePort != null || (containerConfiguration.getExternalPort() != null && readinessProbe.getExec() != null)) {
            containerBuilder.withReadinessProbe(readinessProbe);
        }

        if (probePort != null) {
            ports.add(probePort);
        }
    }

    private void configureStartupProbe(ContainerConfiguration containerConfiguration,
                                       ContainerBuilder containerBuilder, Set<Integer> ports) {
        Probe startupProbe = ProbeCreatorFactory.createStartupProbe(containerConfiguration, properties,
                getProbeType(containerConfiguration));

        Integer probePort = null;

        if (startupProbe.getHttpGet() != null) {
            probePort = startupProbe.getHttpGet().getPort().getIntVal();
        }

        if (startupProbe.getTcpSocket() != null) {
            probePort = startupProbe.getTcpSocket().getPort().getIntVal();
        }

        if (probePort != null || (containerConfiguration.getExternalPort() != null && startupProbe.getExec() != null)) {
            containerBuilder.withStartupProbe(startupProbe);
        }

        if (probePort != null) {
            ports.add(probePort);
        }
    }

    private void configureLivenessProbe(ContainerConfiguration containerConfiguration,
                                        ContainerBuilder containerBuilder, Set<Integer> ports) {
        Probe livenessProbe = ProbeCreatorFactory.createLivenessProbe(containerConfiguration, properties,
                getProbeType(containerConfiguration));

        Integer probePort = null;

        if (livenessProbe.getHttpGet() != null) {
            probePort = livenessProbe.getHttpGet().getPort().getIntVal();
        }

        if (livenessProbe.getTcpSocket() != null) {
            probePort = livenessProbe.getTcpSocket().getPort().getIntVal();
        }

        if (probePort != null || (containerConfiguration.getExternalPort() != null && livenessProbe.getExec() != null)) {
            containerBuilder.withLivenessProbe(livenessProbe);
        }

        if (probePort != null) {
            ports.add(probePort);
        }
    }

    private ProbeType getProbeType(ContainerConfiguration containerConfiguration) {
        AppDeploymentRequest appDeploymentRequest = containerConfiguration.getAppDeploymentRequest();
        Map<String, String> deploymentProperties = getDeploymentProperties(appDeploymentRequest);
        DeploymentPropertiesResolver deploymentPropertiesResolver = getDeploymentPropertiesResolver(appDeploymentRequest);

        return deploymentPropertiesResolver.determineProbeType(deploymentProperties);
    }

    /**
     * Create command arguments
     *
     * @param request the {@link AppDeploymentRequest}
     * @return the command line arguments to use
     */
    List<String> createCommandArgs(AppDeploymentRequest request) {
        List<String> cmdArgs = new LinkedList<>();

        List<String> commandArgOptions = request.getCommandlineArguments().stream()
                .map(this::getArgOption)
                .collect(Collectors.toList());

        // add properties from deployment request
        Map<String, String> args = request.getDefinition().getProperties();
        for (Map.Entry<String, String> entry : args.entrySet()) {
            if (!StringUtils.hasText(entry.getValue())) {
                logger.warn(
                        "Excluding request property with missing value from command args: " + entry.getKey());
            } else if (commandArgOptions.contains(entry.getKey())) {
                logger.warn(
                        String.format(
                                "Excluding request property [--%s=%s] as a command arg. Existing command line argument takes precedence."
                                , entry.getKey(), entry.getValue()));
            } else {
                cmdArgs.add(String.format("--%s=%s", entry.getKey(), entry.getValue()));
            }
        }
        // add provided command line args
        cmdArgs.addAll(request.getCommandlineArguments());
        logger.debug("Using command args: " + cmdArgs);
        return cmdArgs;
    }

    private String getArgOption(String arg) {
        int indexOfAssignment = arg.indexOf("=");
        String argOption = (indexOfAssignment < 0) ? arg : arg.substring(0, indexOfAssignment);
        return argOption.trim().replaceAll("^--", "");
    }

    private DeploymentPropertiesResolver getDeploymentPropertiesResolver(AppDeploymentRequest request) {
        String propertiesPrefix = (request instanceof ScheduleRequest &&
                ((ScheduleRequest) request).getSchedulerProperties() != null &&
                ((ScheduleRequest) request).getSchedulerProperties().size() > 0) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
                KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;
        return new DeploymentPropertiesResolver(propertiesPrefix, this.properties);
    }

    private Map<String, String> getDeploymentProperties(AppDeploymentRequest request) {
        return request.getDeploymentProperties();
    }
}
