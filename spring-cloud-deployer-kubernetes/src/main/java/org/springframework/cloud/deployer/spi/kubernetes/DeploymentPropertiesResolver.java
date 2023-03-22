/*
 * Copyright 2020-2023 the original author or authors.
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
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapEnvSource;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretEnvSource;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Sysctl;
import io.fabric8.kubernetes.api.model.SysctlBuilder;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties.ConfigMapKeyRef;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties.InitContainer;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties.SecretKeyRef;
import org.springframework.cloud.deployer.spi.kubernetes.support.PropertyParserUtils;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.cloud.deployer.spi.util.CommandLineTokenizer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Class that resolves the appropriate deployment properties based on the property prefix being used.
 * Currently, both the Deployer/TaskLauncher and the Scheduler use this resolver to retrieve the
 * deployment properties for the given Kubernetes deployer properties.
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Chris Bono
 */

class DeploymentPropertiesResolver {
	static final String STATEFUL_SET_IMAGE_NAME = "busybox";

	private final Log logger = LogFactory.getLog(getClass().getName());

	private String propertyPrefix;
	private KubernetesDeployerProperties properties;

	DeploymentPropertiesResolver(String propertyPrefix, KubernetesDeployerProperties properties) {
		this.propertyPrefix = propertyPrefix;
		this.properties = properties;
	}

	String getPropertyPrefix() {
		return this.propertyPrefix;
	}

	List<Toleration> getTolerations(Map<String, String> kubernetesDeployerProperties) {
		List<Toleration> tolerations = new ArrayList<>();

		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".tolerations", "tolerations" );

		deployerProperties.getTolerations().forEach(toleration -> tolerations.add(
				new Toleration(toleration.getEffect(), toleration.getKey(), toleration.getOperator(),
						toleration.getTolerationSeconds(), toleration.getValue())));

		this.properties.getTolerations().stream()
				.filter(toleration -> tolerations.stream()
						.noneMatch(existing -> existing.getKey().equals(toleration.getKey())))
				.collect(Collectors.toList())
				.forEach(toleration -> tolerations.add(new Toleration(toleration.getEffect(), toleration.getKey(),
						toleration.getOperator(), toleration.getTolerationSeconds(), toleration.getValue())));

		return tolerations;
	}

	/**
	 * Volume deployment properties are specified in YAML format:
	 *
	 * <code>
	 *     spring.cloud.deployer.kubernetes.volumes=[{name: testhostpath, hostPath: { path: '/test/override/hostPath' }},
	 *     	{name: 'testpvc', persistentVolumeClaim: { claimName: 'testClaim', readOnly: 'true' }},
	 *     	{name: 'testnfs', nfs: { server: '10.0.0.1:111', path: '/test/nfs' }}]
	 * </code>
	 *
	 * Volumes can be specified as deployer properties as well as app deployment properties.
	 * Deployment properties override deployer properties.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployer properties map
	 * @return the configured volumes
	 */
	List<Volume> getVolumes(Map<String, String> kubernetesDeployerProperties) {
		List<Volume> volumes = new ArrayList<>();

		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".volumes", "volumes");

		volumes.addAll(deployerProperties.getVolumes());

		// only add volumes that have not already been added, based on the volume's name
		// i.e. allow provided deployment volumes to override deployer defined volumes
		volumes.addAll(properties.getVolumes().stream()
				.filter(volume -> volumes.stream()
						.noneMatch(existingVolume -> existingVolume.getName().equals(volume.getName())))
				.collect(Collectors.toList()));

		return volumes;
	}

	/**
	 * Get the resource limits for the deployment request. A Pod can define its maximum needed resources by setting the
	 * limits and Kubernetes can provide more resources if any are free.
	 * <p>
	 * Falls back to the server properties if not present in the deployment request.
	 * <p>
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployment properties map
	 * @return the resource limits to use
	 */
	Map<String, Quantity> deduceResourceLimits(Map<String, String> kubernetesDeployerProperties) {
		String memory = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".limits.memory");

		if (!StringUtils.hasText(memory)) {
			memory = properties.getLimits().getMemory();
		}

		String cpu = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".limits.cpu");

		if (!StringUtils.hasText(cpu)) {
			cpu = properties.getLimits().getCpu();
		}

		String gpuVendor = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
			this.propertyPrefix + ".limits.gpuVendor");

		if (!StringUtils.hasText(gpuVendor)) {
			gpuVendor = properties.getLimits().getGpuVendor();
		}

		String gpuCount = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
			this.propertyPrefix + ".limits.gpuCount");

		if (!StringUtils.hasText(gpuCount)) {
			gpuCount = properties.getLimits().getGpuCount();
		}

		Map<String,Quantity> limits = new HashMap<String,Quantity>();

		if (StringUtils.hasText(memory)) {
			limits.put("memory", new Quantity(memory));
		}

		if (StringUtils.hasText(cpu)) {
			limits.put("cpu", new Quantity(cpu));
		}

		if (StringUtils.hasText(gpuVendor) && StringUtils.hasText(gpuCount)) {
			limits.put(gpuVendor + "/gpu", new Quantity(gpuCount));
		}

		return limits;
	}

	/**
	 * Get the image pull policy for the deployment request. If it is not present use the server default. If an override
	 * for the deployment is present but not parseable, fall back to a default value.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployment properties map
	 * @return The image pull policy to use for the container in the request.
	 */
	ImagePullPolicy deduceImagePullPolicy(Map<String, String> kubernetesDeployerProperties) {
		String pullPolicyOverride = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
						this.propertyPrefix + ".imagePullPolicy");

		ImagePullPolicy pullPolicy;
		if (pullPolicyOverride == null) {
			pullPolicy = properties.getImagePullPolicy();
		} else {
			pullPolicy = ImagePullPolicy.relaxedValueOf(pullPolicyOverride);
			if (pullPolicy == null) {
				logger.warn("Parsing of pull policy " + pullPolicyOverride + " failed, using default \"IfNotPresent\".");
				pullPolicy = ImagePullPolicy.IfNotPresent;
			}
		}

		logger.debug("Using imagePullPolicy " + pullPolicy);

		return pullPolicy;
	}

	/**
	 * Get the resource requests for the deployment request. Resource requests are guaranteed by the Kubernetes
	 * runtime.
	 * Falls back to the server properties if not present in the deployment request.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployer properties map
	 * @return the resource requests to use
	 */
	Map<String, Quantity> deduceResourceRequests(Map<String, String> kubernetesDeployerProperties) {
		String memOverride = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".requests.memory");

		if (memOverride == null) {
			memOverride = properties.getRequests().getMemory();
		}


		String cpuOverride = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".requests.cpu");

		if (cpuOverride == null) {
			cpuOverride = properties.getRequests().getCpu();
		}

		logger.debug("Using requests - cpu: " + cpuOverride + " mem: " + memOverride);

		Map<String,Quantity> requests = new HashMap<String, Quantity>();

		if (memOverride != null) {
			requests.put("memory", new Quantity(memOverride));
		}

		if (cpuOverride != null) {
			requests.put("cpu", new Quantity(cpuOverride));
		}

		return requests;
	}

	/**
	 * Get the VolumeClaim template name for Statefulset from the deployment properties.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployer properties
	 * @return the volume claim template name
	 */
	String getStatefulSetVolumeClaimTemplateName(Map<String, String> kubernetesDeployerProperties) {
		String name = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".statefulSet.volumeClaimTemplate.name");

		if (name == null && properties.getStatefulSet() != null && properties.getStatefulSet().getVolumeClaimTemplate() != null) {
			name = properties.getStatefulSet().getVolumeClaimTemplate().getName();
		}

		return name;
	}

	/**
	 * Get the StatefulSet storage class name to be set in VolumeClaim template for the deployment properties.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployer properties
	 * @return the storage class name
	 */
	String getStatefulSetStorageClassName(Map<String, String> kubernetesDeployerProperties) {
		String storageClassName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".statefulSet.volumeClaimTemplate.storageClassName");

		if (storageClassName == null && properties.getStatefulSet() != null && properties.getStatefulSet().getVolumeClaimTemplate() != null) {
			storageClassName = properties.getStatefulSet().getVolumeClaimTemplate().getStorageClassName();
		}

		return storageClassName;
	}

	/**
	 * Get the StatefulSet storage value to be set in VolumeClaim template for the given deployment properties.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployer properties
	 * @return the StatefulSet storage
	 */
	String getStatefulSetStorage(Map<String, String> kubernetesDeployerProperties) {
		String storage = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".statefulSet.volumeClaimTemplate.storage");

		if (storage == null && properties.getStatefulSet() != null && properties.getStatefulSet().getVolumeClaimTemplate() != null) {
			storage = properties.getStatefulSet().getVolumeClaimTemplate().getStorage();
		}

		return ByteSizeUtils.parseToMebibytes(storage) + "Mi";
	}

	/**
	 * Get the hostNetwork setting for the deployment request.
	 *
	 * @param kubernetesDeployerProperties the kubernetes deployment properties map
	 * @return Whether host networking is requested
	 */
	boolean getHostNetwork(Map<String, String> kubernetesDeployerProperties) {
		String hostNetworkOverride = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
						this.propertyPrefix + ".hostNetwork");
		boolean hostNetwork;

		if (!StringUtils.hasText(hostNetworkOverride)) {
			hostNetwork = properties.isHostNetwork();
		}
		else {
			hostNetwork = Boolean.valueOf(hostNetworkOverride);
		}

		logger.debug("Using hostNetwork " + hostNetwork);

		return hostNetwork;
	}

	/**
	 * Get the nodeSelectors setting for the deployment request.
	 *
	 * @param deploymentProperties The deployment request deployment properties.
	 * @return map of nodeSelectors
	 */
	Map<String, String> getNodeSelectors(Map<String, String> deploymentProperties) {
		Map<String, String> nodeSelectors = new HashMap<>();

		String nodeSelector = this.properties.getNodeSelector();

		String nodeSelectorDeploymentProperty = deploymentProperties
				.getOrDefault(KubernetesDeployerProperties.KUBERNETES_DEPLOYMENT_NODE_SELECTOR, "");

		boolean hasGlobalNodeSelector = StringUtils.hasText(properties.getNodeSelector());
		boolean hasDeployerPropertyNodeSelector = StringUtils.hasText(nodeSelectorDeploymentProperty);

		if ((hasGlobalNodeSelector && hasDeployerPropertyNodeSelector) ||
				(!hasGlobalNodeSelector && hasDeployerPropertyNodeSelector)) {
			nodeSelector = nodeSelectorDeploymentProperty;
		}

		if (StringUtils.hasText(nodeSelector)) {
			String[] nodeSelectorPairs = nodeSelector.split(",");
			for (String nodeSelectorPair : nodeSelectorPairs) {
				String[] selector = nodeSelectorPair.split(":");
				Assert.isTrue(selector.length == 2, String.format("Invalid nodeSelector value: '%s'", nodeSelectorPair));
				nodeSelectors.put(selector[0].trim(), selector[1].trim());
			}
		}

		return nodeSelectors;
	}

	String getImagePullSecret(Map<String, String> kubernetesDeployerProperties) {
		String imagePullSecret = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".imagePullSecret", "");

		if(!StringUtils.hasText(imagePullSecret)) {
			imagePullSecret = this.properties.getImagePullSecret();
		}

		return imagePullSecret;
	}

	List<String> getImagePullSecrets(Map<String, String> kubernetesDeployerProperties) {

		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".imagePullSecrets", "imagePullSecrets");

		if (deployerProperties.getImagePullSecrets() == null || deployerProperties.getImagePullSecrets().isEmpty()) {
			return properties.getImagePullSecrets();
		} else {
			return deployerProperties.getImagePullSecrets();
		}
	}

	String getDeploymentServiceAccountName(Map<String, String> kubernetesDeployerProperties) {
		String deploymentServiceAccountName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".deploymentServiceAccountName");

		if (!StringUtils.hasText(deploymentServiceAccountName)) {
			deploymentServiceAccountName = properties.getDeploymentServiceAccountName();
		}

		return deploymentServiceAccountName;
	}

	Boolean getShareProcessNamespace(Map<String, String> kubernetesDeployerProperties) {
		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".shareProcessNamespace", "shareProcessNamespace");
		return deployerProperties.getShareProcessNamespace();
	}

	String getPriorityClassName(Map<String, String> kubernetesDeployerProperties) {
		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".priorityClassName", "priorityClassName");
		return deployerProperties.getPriorityClassName();
	}

	PodSecurityContext getPodSecurityContext(Map<String, String> kubernetesDeployerProperties) {
		PodSecurityContext podSecurityContext = null;

		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".podSecurityContext", "podSecurityContext");

		if (deployerProperties.getPodSecurityContext() != null) {
			podSecurityContext = buildPodSecurityContext(deployerProperties);
		} else if (this.properties.getPodSecurityContext() != null ) {
			podSecurityContext = buildPodSecurityContext(this.properties);
		}
		return podSecurityContext;
	}

	private PodSecurityContext buildPodSecurityContext(KubernetesDeployerProperties deployerProperties) {
		PodSecurityContextBuilder podSecurityContextBuilder = new PodSecurityContextBuilder()
				.withRunAsUser(deployerProperties.getPodSecurityContext().getRunAsUser())
				.withRunAsGroup(deployerProperties.getPodSecurityContext().getRunAsGroup())
				.withRunAsNonRoot(deployerProperties.getPodSecurityContext().getRunAsNonRoot())
				.withFsGroup(deployerProperties.getPodSecurityContext().getFsGroup())
				.withFsGroupChangePolicy(deployerProperties.getPodSecurityContext().getFsGroupChangePolicy())
				.withSupplementalGroups(deployerProperties.getPodSecurityContext().getSupplementalGroups());
		if (deployerProperties.getPodSecurityContext().getSeccompProfile() != null) {
			podSecurityContextBuilder.withNewSeccompProfile(
					deployerProperties.getPodSecurityContext().getSeccompProfile().getLocalhostProfile(),
					deployerProperties.getPodSecurityContext().getSeccompProfile().getType());
		}
		if (deployerProperties.getPodSecurityContext().getSeLinuxOptions() != null) {
			podSecurityContextBuilder.withNewSeLinuxOptions(
					deployerProperties.getPodSecurityContext().getSeLinuxOptions().getLevel(),
					deployerProperties.getPodSecurityContext().getSeLinuxOptions().getRole(),
					deployerProperties.getPodSecurityContext().getSeLinuxOptions().getType(),
					deployerProperties.getPodSecurityContext().getSeLinuxOptions().getUser());
		}
		if (!CollectionUtils.isEmpty(deployerProperties.getPodSecurityContext().getSysctls()))  {
			List<Sysctl> sysctls = deployerProperties.getPodSecurityContext().getSysctls().stream()
					.map((sysctlInfo) -> new SysctlBuilder().withName(sysctlInfo.getName())
							.withValue(sysctlInfo.getValue()).build())
					.collect(Collectors.toList());
			podSecurityContextBuilder.withSysctls(sysctls);
		}
		if (deployerProperties.getPodSecurityContext().getWindowsOptions() != null) {
			podSecurityContextBuilder.withNewWindowsOptions(
					deployerProperties.getPodSecurityContext().getWindowsOptions().getGmsaCredentialSpec(),
					deployerProperties.getPodSecurityContext().getWindowsOptions().getGmsaCredentialSpecName(),
					deployerProperties.getPodSecurityContext().getWindowsOptions().getHostProcess(),
					deployerProperties.getPodSecurityContext().getWindowsOptions().getRunAsUserName());
		}
		return podSecurityContextBuilder.build();
	}

	SecurityContext getContainerSecurityContext(Map<String, String> kubernetesDeployerProperties) {
		SecurityContext securityContext = null;

		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".containerSecurityContext", "containerSecurityContext");

		if (deployerProperties.getContainerSecurityContext() != null) {
			securityContext = buildContainerSecurityContext(deployerProperties);
		} else if (this.properties.getContainerSecurityContext() != null ) {
			securityContext = buildContainerSecurityContext(this.properties);
		}
		return securityContext;
	}

	private SecurityContext buildContainerSecurityContext(KubernetesDeployerProperties deployerProperties) {
		SecurityContextBuilder securityContextBuilder = new SecurityContextBuilder()
				.withAllowPrivilegeEscalation(deployerProperties.getContainerSecurityContext().getAllowPrivilegeEscalation())
				.withPrivileged(deployerProperties.getContainerSecurityContext().getPrivileged())
				.withProcMount(deployerProperties.getContainerSecurityContext().getProcMount())
				.withReadOnlyRootFilesystem(deployerProperties.getContainerSecurityContext().getReadOnlyRootFilesystem())
				.withRunAsUser(deployerProperties.getContainerSecurityContext().getRunAsUser())
				.withRunAsGroup(deployerProperties.getContainerSecurityContext().getRunAsGroup())
				.withRunAsNonRoot(deployerProperties.getContainerSecurityContext().getRunAsNonRoot());

		if (deployerProperties.getContainerSecurityContext().getCapabilities() != null) {
			securityContextBuilder.withCapabilities(
					new CapabilitiesBuilder().withAdd(deployerProperties.getContainerSecurityContext().getCapabilities().getAdd())
							.withDrop(deployerProperties.getContainerSecurityContext().getCapabilities().getDrop())
							.build());
		}
		if (deployerProperties.getContainerSecurityContext().getSeccompProfile() != null) {
			securityContextBuilder.withNewSeccompProfile(
					deployerProperties.getContainerSecurityContext().getSeccompProfile().getLocalhostProfile(),
					deployerProperties.getContainerSecurityContext().getSeccompProfile().getType());
		}
		if (deployerProperties.getContainerSecurityContext().getSeLinuxOptions() != null) {
			securityContextBuilder.withNewSeLinuxOptions(
					deployerProperties.getContainerSecurityContext().getSeLinuxOptions().getLevel(),
					deployerProperties.getContainerSecurityContext().getSeLinuxOptions().getRole(),
					deployerProperties.getContainerSecurityContext().getSeLinuxOptions().getType(),
					deployerProperties.getContainerSecurityContext().getSeLinuxOptions().getUser());
		}
		if (deployerProperties.getContainerSecurityContext().getWindowsOptions() != null) {
			securityContextBuilder.withNewWindowsOptions(
					deployerProperties.getContainerSecurityContext().getWindowsOptions().getGmsaCredentialSpec(),
					deployerProperties.getContainerSecurityContext().getWindowsOptions().getGmsaCredentialSpecName(),
					deployerProperties.getContainerSecurityContext().getWindowsOptions().getHostProcess(),
					deployerProperties.getContainerSecurityContext().getWindowsOptions().getRunAsUserName());
		}
		return securityContextBuilder.build();
	}

	Affinity getAffinityRules(Map<String, String> kubernetesDeployerProperties) {
		Affinity affinity = new Affinity();

		String nodeAffinityPropertyKey = this.propertyPrefix + ".affinity.nodeAffinity";
		String podAffinityPropertyKey = this.propertyPrefix + ".affinity.podAffinity";
		String podAntiAffinityPropertyKey = this.propertyPrefix + ".affinity.podAntiAffinity";

		String nodeAffinityValue = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				nodeAffinityPropertyKey);
		String podAffinityValue = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				podAffinityPropertyKey);
		String podAntiAffinityValue = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				podAntiAffinityPropertyKey);

		if (properties.getNodeAffinity() != null && !StringUtils.hasText(nodeAffinityValue)) {
			affinity.setNodeAffinity(new AffinityBuilder()
					.withNodeAffinity(properties.getNodeAffinity())
					.buildNodeAffinity());
		} else if (StringUtils.hasText(nodeAffinityValue)) {
			KubernetesDeployerProperties nodeAffinityProperties = bindProperties(kubernetesDeployerProperties,
					nodeAffinityPropertyKey, "nodeAffinity");

			affinity.setNodeAffinity(new AffinityBuilder()
					.withNodeAffinity(nodeAffinityProperties.getNodeAffinity())
					.buildNodeAffinity());
		}

		if (properties.getPodAffinity() != null && !StringUtils.hasText(podAffinityValue)) {
			affinity.setPodAffinity(new AffinityBuilder()
					.withPodAffinity(properties.getPodAffinity())
					.buildPodAffinity());
		} else if (StringUtils.hasText(podAffinityValue)) {
			KubernetesDeployerProperties podAffinityProperties = bindProperties(kubernetesDeployerProperties,
					podAffinityPropertyKey, "podAffinity");

			affinity.setPodAffinity(new AffinityBuilder()
					.withPodAffinity(podAffinityProperties.getPodAffinity())
					.buildPodAffinity());
		}

		if (properties.getPodAntiAffinity() != null && !StringUtils.hasText(podAntiAffinityValue)) {
			affinity.setPodAntiAffinity(new AffinityBuilder()
					.withPodAntiAffinity(properties.getPodAntiAffinity())
					.buildPodAntiAffinity());
		} else if (StringUtils.hasText(podAntiAffinityValue)) {
			KubernetesDeployerProperties podAntiAffinityProperties = bindProperties(kubernetesDeployerProperties,
					podAntiAffinityPropertyKey, "podAntiAffinity");

			affinity.setPodAntiAffinity(new AffinityBuilder()
					.withPodAntiAffinity(podAntiAffinityProperties.getPodAntiAffinity())
					.buildPodAntiAffinity());
		}

		return affinity;
	}

	Container getInitContainer(Map<String, String> kubernetesDeployerProperties) {
		Container container = null;
		KubernetesDeployerProperties deployerProperties = bindProperties(kubernetesDeployerProperties,
				this.propertyPrefix + ".initContainer", "initContainer");

		if (deployerProperties.getInitContainer() == null) {
			String containerName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
					this.propertyPrefix + ".initContainer.containerName");

			String imageName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
					this.propertyPrefix + ".initContainer.imageName");

			String commands = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
					this.propertyPrefix + ".initContainer.commands");

			String envString = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
					this.propertyPrefix + ".initContainer.environmentVariables");

			List<VolumeMount> vms = this.getInitContainerVolumeMounts(kubernetesDeployerProperties);

			if (StringUtils.hasText(containerName) && StringUtils.hasText(imageName)) {
				container = new ContainerBuilder()
						.withName(containerName)
						.withImage(imageName)
						.withCommand(commands)
						.withEnv(toEnvironmentVariables((envString != null)? envString.split(","): new String[0]))
						.addAllToVolumeMounts(vms)
						.build();
			}
		}
		else {
			InitContainer initContainer = deployerProperties.getInitContainer();

			if (initContainer != null) {
				container = new ContainerBuilder()
						.withName(initContainer.getContainerName())
						.withImage(initContainer.getImageName())
						.withCommand(initContainer.getCommands())
						.withEnv(toEnvironmentVariables(initContainer.getEnvironmentVariables()))
						.addAllToVolumeMounts(Optional.ofNullable(initContainer.getVolumeMounts()).orElse(Collections.emptyList()))
						.build();
			}
		}

		return container;
	}

	private List<EnvVar>  toEnvironmentVariables(String[] environmentVariables) {
		Map<String, String> envVarsMap = new HashMap<>();
		if (environmentVariables != null) {
			for (String envVar : environmentVariables) {
				String[] strings = envVar.split("=", 2);
				Assert.isTrue(strings.length == 2, "Invalid environment variable declared: " + envVar);
				envVarsMap.put(strings[0], strings[1]);
			}
		}

		List<EnvVar> envVars = new ArrayList<>();
		for (Map.Entry<String, String> e : envVarsMap.entrySet()) {
			envVars.add(new EnvVar(e.getKey(), e.getValue(), null));
		}
		return envVars;
	}

	List<Container> getAdditionalContainers(Map<String, String> deploymentProperties) {
		List<Container> containers = new ArrayList<>();

		KubernetesDeployerProperties deployerProperties = bindProperties(deploymentProperties,
				this.propertyPrefix + ".additionalContainers", "additionalContainers" );

		if (deployerProperties.getAdditionalContainers() != null) {
			deployerProperties.getAdditionalContainers().forEach(container ->
					containers.add(container));
		}

		// Add the containers from the original properties excluding the containers with the matching names from the
		// deployment properties
		if (this.properties.getAdditionalContainers() != null) {
			this.properties.getAdditionalContainers().stream()
					.filter(container -> containers.stream().noneMatch(existing -> existing.getName().equals(container.getName())))
					.forEachOrdered(container -> containers.add(container));
		}

		return containers;
	}

	Map<String, String> getPodAnnotations(Map<String, String> kubernetesDeployerProperties) {
		String annotationsValue = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".podAnnotations", "");

		if (!StringUtils.hasText(annotationsValue)) {
			annotationsValue = properties.getPodAnnotations();
		}

		return PropertyParserUtils.getStringPairsToMap(annotationsValue);
	}

	Map<String, String> getServiceAnnotations(Map<String, String> kubernetesDeployerProperties) {
		String annotationsProperty = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".serviceAnnotations", "");

		if (!StringUtils.hasText(annotationsProperty)) {
			annotationsProperty = this.properties.getServiceAnnotations();
		}

		return PropertyParserUtils.getStringPairsToMap(annotationsProperty);
	}

	Map<String, String> getDeploymentLabels(Map<String, String> kubernetesDeployerProperties) {
		Map<String, String> labels = new HashMap<>();

		String deploymentLabels = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".deploymentLabels", "");

		// Add deployment labels set at the deployer level.
		String updatedLabels = StringUtils.hasText(this.properties.getDeploymentLabels()) ?
				new StringBuilder().append(deploymentLabels).append(StringUtils.hasText(deploymentLabels) ? ",": "")
						.append(this.properties.getDeploymentLabels()).toString() : deploymentLabels;

		if (StringUtils.hasText(updatedLabels)) {
			String[] deploymentLabel = updatedLabels.split(",");

			for (String label : deploymentLabel) {
				String[] labelPair = label.split(":");
				Assert.isTrue(labelPair.length == 2,
						String.format("Invalid label format, expected 'labelKey:labelValue', got: '%s'", labelPair));
				labels.put(labelPair[0].trim(), labelPair[1].trim());
			}
		}
		return labels;
	}

	RestartPolicy getRestartPolicy(Map<String, String> kubernetesDeployerProperties) {
		String restartPolicy = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".restartPolicy", "");

		if (StringUtils.hasText(restartPolicy)) {
			return RestartPolicy.valueOf(restartPolicy);
		}

		return this.properties.getRestartPolicy();
	}

	String getTaskServiceAccountName(Map<String, String> kubernetesDeployerProperties) {
		String taskServiceAccountName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".taskServiceAccountName", "");
		if (StringUtils.hasText(taskServiceAccountName)) {
			return taskServiceAccountName;
		}

		return this.properties.getTaskServiceAccountName();
	}

	/**
	 * Binds the YAML formatted value of a deployment property to a {@link KubernetesDeployerProperties} instance.
	 *
	 * @param kubernetesDeployerProperties the map of Kubernetes deployer properties
	 * @param propertyKey the property key to obtain the value to bind for
	 * @param yamlLabel the label representing the field to bind to
	 * @return a {@link KubernetesDeployerProperties} with the bound property data
	 */
	private static KubernetesDeployerProperties bindProperties(Map<String, String> kubernetesDeployerProperties,
			String propertyKey, String yamlLabel) {
		String deploymentPropertyValue = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties, propertyKey);

		KubernetesDeployerProperties deployerProperties = new KubernetesDeployerProperties();

		if (StringUtils.hasText(deploymentPropertyValue)) {
			try {
				YamlPropertiesFactoryBean properties = new YamlPropertiesFactoryBean();
				String tmpYaml = "{ " + yamlLabel + ": " + deploymentPropertyValue + " }";
				properties.setResources(new ByteArrayResource(tmpYaml.getBytes()));
				Properties yaml = properties.getObject();
				MapConfigurationPropertySource source = new MapConfigurationPropertySource(yaml);
				deployerProperties = new Binder(source)
						.bind("", Bindable.of(KubernetesDeployerProperties.class)).get();
			} catch (Exception e) {
				throw new IllegalArgumentException(
						String.format("Invalid binding property '%s'", deploymentPropertyValue), e);
			}
		}

		return deployerProperties;
	}

	String getStatefulSetInitContainerImageName(Map<String, String> kubernetesDeployerProperties) {
		String statefulSetInitContainerImageName = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".statefulSetInitContainerImageName", "");

		if (StringUtils.hasText(statefulSetInitContainerImageName)) {
			return statefulSetInitContainerImageName;
		}

		statefulSetInitContainerImageName = this.properties.getStatefulSetInitContainerImageName();

		if (StringUtils.hasText(statefulSetInitContainerImageName)) {
			return statefulSetInitContainerImageName;
		}

		return STATEFUL_SET_IMAGE_NAME;
	}

	Map<String, String> getJobAnnotations(Map<String, String> kubernetesDeployerProperties) {
		String annotationsProperty = PropertyParserUtils.getDeploymentPropertyValue(kubernetesDeployerProperties,
				this.propertyPrefix + ".jobAnnotations", "");

		if (!StringUtils.hasText(annotationsProperty)) {
			annotationsProperty = this.properties.getJobAnnotations();
		}

		return PropertyParserUtils.getStringPairsToMap(annotationsProperty);
	}

	/**
	 * Volume mount deployment properties are specified in YAML format:
	 * <p>
	 * <code>
	 * spring.cloud.deployer.kubernetes.volumeMounts=[{name: 'testhostpath', mountPath: '/test/hostPath'},
	 * {name: 'testpvc', mountPath: '/test/pvc'}, {name: 'testnfs', mountPath: '/test/nfs'}]
	 * </code>
	 * <p>
	 * Volume mounts can be specified as deployer properties as well as app deployment properties.
	 * Deployment properties override deployer properties.
	 *
	 * @param deploymentProperties the deployment properties from {@link AppDeploymentRequest}
	 * @return the configured volume mounts
	 */
	List<VolumeMount> getVolumeMounts(Map<String, String> deploymentProperties) {
		return this.getVolumeMounts(PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".volumeMounts"));
	}

	/**
	 * Init Containers volume mount properties are specified in YAML format:
	 * <p>
	 * <code>
	 * spring.cloud.deployer.kubernetes.initContainer.volumeMounts=[{name: 'testhostpath', mountPath: '/test/hostPath'},
	 * {name: 'testpvc', mountPath: '/test/pvc'}, {name: 'testnfs', mountPath: '/test/nfs'}]
	 * </code>
	 * <p>
	 * They can be specified as deployer properties as well as app deployment properties.
	 * The later overrides deployer properties.
	 *
	 * @param deploymentProperties the deployment properties from {@link AppDeploymentRequest}
	 * @return the configured volume mounts
	 */
	private List<VolumeMount> getInitContainerVolumeMounts(Map<String, String> deploymentProperties) {
		return this.getVolumeMounts(PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".initContainer.volumeMounts"));
	}

	private List<VolumeMount> getVolumeMounts(String propertyValue) {
		List<VolumeMount> volumeMounts = new ArrayList<>();

		if (StringUtils.hasText(propertyValue)) {
			try {
				YamlPropertiesFactoryBean properties = new YamlPropertiesFactoryBean();
				String tmpYaml = "{ volume-mounts: " + propertyValue + " }";
				properties.setResources(new ByteArrayResource(tmpYaml.getBytes()));
				Properties yaml = properties.getObject();
				MapConfigurationPropertySource source = new MapConfigurationPropertySource(yaml);
				KubernetesDeployerProperties deployerProperties = new Binder(source)
						.bind("", Bindable.of(KubernetesDeployerProperties.class)).get();
				volumeMounts.addAll(deployerProperties.getVolumeMounts());
			} catch (Exception e) {
				throw new IllegalArgumentException(
						String.format("Invalid volume mount '%s'", propertyValue), e);
			}
		}

		// only add volume mounts that have not already been added, based on the volume mount's name
		// i.e. allow provided deployment volume mounts to override deployer defined volume mounts
		volumeMounts.addAll(this.properties.getVolumeMounts().stream().filter(volumeMount -> volumeMounts.stream()
				.noneMatch(existingVolumeMount -> existingVolumeMount.getName().equals(volumeMount.getName())))
				.collect(Collectors.toList()));

		return volumeMounts;
	}

	/**
	 * The list represents a single command with many arguments.
	 *
	 * @param deploymentProperties the kubernetes deployer properties map
	 * @return a list of strings that represents the command and any arguments for that command
	 */
	List<String> getContainerCommand(Map<String, String> deploymentProperties) {
		String containerCommand = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".containerCommand", "");

		return new CommandLineTokenizer(containerCommand).getArgs();
	}

	/**
	 * Parse Lifecycle hooks.
	 * @param deploymentProperties the kubernetes deployer properties map
	 * @return Lifecycle spec
	 */
	KubernetesDeployerProperties.Lifecycle getLifeCycle(Map<String,String> deploymentProperties) {
		KubernetesDeployerProperties.Lifecycle lifecycle = properties.getLifecycle();

		if (deploymentProperties.keySet().stream()
				.noneMatch(s -> s.startsWith(propertyPrefix + ".lifecycle"))) {
			return lifecycle;
		}
		String postStart = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".lifecycle.postStart.exec.command");
		if (StringUtils.hasText(postStart)) {
			lifecycle.setPostStart(lifecycleHook(postStart));
		}

		String preStop = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".lifecycle.preStop.exec.command");
		if (StringUtils.hasText(preStop)) {
			lifecycle.setPreStop(lifecycleHook(preStop));
		}
		return lifecycle;
	}

	private KubernetesDeployerProperties.Lifecycle.Hook lifecycleHook(String command) {
		KubernetesDeployerProperties.Lifecycle.Hook hook = new KubernetesDeployerProperties.Lifecycle.Hook();
		KubernetesDeployerProperties.Lifecycle.Exec exec = new KubernetesDeployerProperties.Lifecycle.Exec();
		exec.setCommand(Arrays.asList(command.split(",")));
		hook.setExec(exec);
		return hook;
	}

	/**
	 * @param deploymentProperties the kubernetes deployer properties map
	 * @return a list of Integers to add to the container
	 */
	List<Integer> getContainerPorts(Map<String, String> deploymentProperties) {
		List<Integer> containerPortList = new ArrayList<>();
		String containerPorts = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".containerPorts", null);

		if (containerPorts != null) {
			String[] containerPortSplit = containerPorts.split(",");
			for (String containerPort : containerPortSplit) {
				logger.trace("Adding container ports from AppDeploymentRequest: " + containerPort);
				Integer port = Integer.parseInt(containerPort.trim());
				containerPortList.add(port);
			}
		}

		return containerPortList;
	}

	/**
	 * @param deploymentProperties the kubernetes deployer properties map
	 * @return a List of EnvVar objects for app specific environment settings
	 */
	Map<String, String> getAppEnvironmentVariables(Map<String, String> deploymentProperties) {
		Map<String, String> appEnvVarMap = new HashMap<>();
		String appEnvVar = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".environmentVariables", null);

		if (appEnvVar != null) {
			String[] appEnvVars = new NestedCommaDelimitedVariableParser().parse(appEnvVar);
			for (String envVar : appEnvVars) {
				logger.trace("Adding environment variable from AppDeploymentRequest: " + envVar);
				String[] strings = envVar.split("=", 2);
				Assert.isTrue(strings.length == 2, "Invalid environment variable declared: " + envVar);
				appEnvVarMap.put(strings[0], strings[1]);
			}
		}

		return appEnvVarMap;
	}

	static class NestedCommaDelimitedVariableParser {
		static final String REGEX = "(\\w+='.+?'),?";
		static final Pattern pattern = Pattern.compile(REGEX);

		String[] parse(String value) {
			List<String> vars = new ArrayList<>();

			Matcher m = pattern.matcher(value);

			while (m.find()) {
				String replacedVar = m.group(1).replaceAll("'","");

				if (StringUtils.hasText(replacedVar)) {
					vars.add(replacedVar);
				}
			}

			String nonQuotedVars = value.replaceAll(pattern.pattern(), "");

			if (StringUtils.hasText(nonQuotedVars)) {
				vars.addAll(Arrays.asList(nonQuotedVars.split(",")));
			}

			return vars.toArray(new String[0]);
		}
	}

	EntryPointStyle determineEntryPointStyle(Map<String, String> deploymentProperties) {
		EntryPointStyle entryPointStyle = null;
		String deployerPropertyValue = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".entryPointStyle", null);

		if (deployerPropertyValue != null) {
			try {
				entryPointStyle = EntryPointStyle.valueOf(deployerPropertyValue.toLowerCase());
			}
			catch (IllegalArgumentException ignore) {
			}
		}

		if (entryPointStyle == null) {
			entryPointStyle = this.properties.getEntryPointStyle();
		}

		return entryPointStyle;
	}

	ProbeType determineProbeType(Map<String, String> deploymentProperties) {
		ProbeType probeType = this.properties.getProbeType();
		String deployerPropertyValue = PropertyParserUtils.getDeploymentPropertyValue(deploymentProperties,
				this.propertyPrefix + ".probeType", null);

		if (StringUtils.hasText(deployerPropertyValue)) {
			probeType = ProbeType.valueOf(deployerPropertyValue.toUpperCase());
		}

		return probeType;
	}

	List<EnvVar> getConfigMapKeyRefs(Map<String, String> deploymentProperties) {
		List<EnvVar> configMapKeyRefs = new ArrayList<>();
		KubernetesDeployerProperties deployerProperties = bindProperties(deploymentProperties,
				this.propertyPrefix + ".configMapKeyRefs", "configMapKeyRefs");

		deployerProperties.getConfigMapKeyRefs().forEach(configMapKeyRef ->
				configMapKeyRefs.add(buildConfigMapKeyRefEnvVar(configMapKeyRef)));

		properties.getConfigMapKeyRefs().stream()
				.filter(configMapKeyRef -> configMapKeyRefs.stream()
						.noneMatch(existing -> existing.getName().equals(configMapKeyRef.getEnvVarName())))
				.collect(Collectors.toList())
				.forEach(configMapKeyRef -> configMapKeyRefs.add(buildConfigMapKeyRefEnvVar(configMapKeyRef)));

		return configMapKeyRefs;
	}

	private EnvVar buildConfigMapKeyRefEnvVar(ConfigMapKeyRef configMapKeyRef) {
		ConfigMapKeySelector configMapKeySelector = new ConfigMapKeySelector();

		EnvVarSource envVarSource = new EnvVarSource();
		envVarSource.setConfigMapKeyRef(configMapKeySelector);

		EnvVar configMapKeyEnvRefVar = new EnvVar();
		configMapKeyEnvRefVar.setValueFrom(envVarSource);
		configMapKeySelector.setName(configMapKeyRef.getConfigMapName());
		configMapKeySelector.setKey(configMapKeyRef.getDataKey());
		configMapKeyEnvRefVar.setName(configMapKeyRef.getEnvVarName());

		return configMapKeyEnvRefVar;
	}

	List<EnvVar> getSecretKeyRefs(Map<String, String> deploymentProperties) {
		List<EnvVar> secretKeyRefs = new ArrayList<>();

		KubernetesDeployerProperties deployerProperties = bindProperties(deploymentProperties,
				this.propertyPrefix + ".secretKeyRefs", "secretKeyRefs" );

		deployerProperties.getSecretKeyRefs().forEach(secretKeyRef ->
				secretKeyRefs.add(buildSecretKeyRefEnvVar(secretKeyRef)));

		properties.getSecretKeyRefs().stream()
				.filter(secretKeyRef -> secretKeyRefs.stream()
						.noneMatch(existing -> existing.getName().equals(secretKeyRef.getEnvVarName())))
				.collect(Collectors.toList())
				.forEach(secretKeyRef -> secretKeyRefs.add(buildSecretKeyRefEnvVar(secretKeyRef)));

		return secretKeyRefs;
	}

	private EnvVar buildSecretKeyRefEnvVar(SecretKeyRef secretKeyRef) {
		SecretKeySelector secretKeySelector = new SecretKeySelector();

		EnvVarSource envVarSource = new EnvVarSource();
		envVarSource.setSecretKeyRef(secretKeySelector);

		EnvVar secretKeyEnvRefVar = new EnvVar();
		secretKeyEnvRefVar.setValueFrom(envVarSource);
		secretKeySelector.setName(secretKeyRef.getSecretName());
		secretKeySelector.setKey(secretKeyRef.getDataKey());
		secretKeyEnvRefVar.setName(secretKeyRef.getEnvVarName());

		return secretKeyEnvRefVar;
	}

	List<EnvFromSource> getConfigMapRefs(Map<String, String> deploymentProperties) {
		List<EnvFromSource> configMapRefs = new ArrayList<>();
		KubernetesDeployerProperties deployerProperties = bindProperties(deploymentProperties,
				this.propertyPrefix + ".configMapRefs", "configMapRefs");

		deployerProperties.getConfigMapRefs().forEach(configMapRef ->
				configMapRefs.add(buildConfigMapRefEnvVar(configMapRef)));

		if (deployerProperties.getConfigMapRefs().isEmpty()) {
			properties.getConfigMapRefs().stream()
					.filter(configMapRef -> configMapRefs.stream()
							.noneMatch(existing -> existing.getConfigMapRef().getName().equals(configMapRef)))
					.collect(Collectors.toList())
					.forEach(configMapRef -> configMapRefs.add(buildConfigMapRefEnvVar(configMapRef)));
		}

		return configMapRefs;
	}

	private EnvFromSource buildConfigMapRefEnvVar(String configMapRefName) {
		ConfigMapEnvSource configMapEnvSource = new ConfigMapEnvSource();
		configMapEnvSource.setName(configMapRefName);

		EnvFromSource envFromSource = new EnvFromSource();
		envFromSource.setConfigMapRef(configMapEnvSource);

		return envFromSource;
	}

	List<EnvFromSource> getSecretRefs(Map<String, String> deploymentProperties) {
		List<EnvFromSource> secretRefs = new ArrayList<>();
		KubernetesDeployerProperties deployerProperties = bindProperties(deploymentProperties,
				this.propertyPrefix + ".secretRefs", "secretRefs");

		deployerProperties.getSecretRefs().forEach(secretRef ->
				secretRefs.add(buildSecretRefEnvVar(secretRef)));

		if (deployerProperties.getSecretRefs().isEmpty()) {
			properties.getSecretRefs().stream()
					.filter(secretRef -> secretRefs.stream()
							.noneMatch(existing -> existing.getSecretRef().getName().equals(secretRef)))
					.collect(Collectors.toList())
					.forEach(secretRef -> secretRefs.add(buildSecretRefEnvVar(secretRef)));
		}

		return secretRefs;
	}

	private EnvFromSource buildSecretRefEnvVar(String secretRefName) {
		SecretEnvSource secretEnvSource = new SecretEnvSource();
		secretEnvSource.setName(secretRefName);

		EnvFromSource envFromSource = new EnvFromSource();
		envFromSource.setSecretRef(secretEnvSource);

		return envFromSource;
	}
}
