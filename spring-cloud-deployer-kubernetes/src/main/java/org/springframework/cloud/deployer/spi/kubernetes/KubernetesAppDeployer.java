/*
 * Copyright 2015-2020 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.kubernetes.support.PropertyParserUtils;
import org.springframework.util.StringUtils;

/**
 * A deployer that targets Kubernetes.
 *
 * @author Florian Rosenberg
 * @author Thomas Risberg
 * @author Mark Fisher
 * @author Donovan Muller
 * @author David Turanski
 * @author Ilayaperumal Gopinathan
 * @author Chris Schaefer
 * @author Christian Tzolov
 * @author Omar Gonzalez
 */
public class KubernetesAppDeployer extends AbstractKubernetesDeployer implements AppDeployer {

	protected final Log logger = LogFactory.getLog(getClass().getName());

	@Autowired
	public KubernetesAppDeployer(KubernetesDeployerProperties properties, KubernetesClient client) {
		this(properties, client, new DefaultContainerFactory(properties));
	}

	@Autowired
	public KubernetesAppDeployer(KubernetesDeployerProperties properties, KubernetesClient client,
		ContainerFactory containerFactory) {
		this.properties = properties;
		this.client = client;
		this.containerFactory = containerFactory;
		this.deploymentPropertiesResolver = new DeploymentPropertiesResolver(
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX, properties);
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		String appId = createDeploymentId(request);
		logger.debug(String.format("Deploying app: %s", appId));

		try {
			AppStatus status = status(appId);

			if (!status.getState().equals(DeploymentState.unknown)) {
				throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
			}

			String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
			boolean indexed = (indexedProperty != null) ? Boolean.valueOf(indexedProperty) : false;
			logPossibleDownloadResourceMessage(request.getResource());

			createService(request);
			if (indexed) {
				createStatefulSet(request);
			}
			else {
				createDeployment(request);
			}
			return appId;
		}
		catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void undeploy(String appId) {
		logger.debug(String.format("Undeploying app: %s", appId));
		AppStatus status = status(appId);
		if (status.getState().equals(DeploymentState.unknown)) {
			// ensure objects for this appId are deleted in the event a previous deployment failed.
			// allows for log inspection prior to making an undeploy request.
			deleteAllObjects(appId);

			throw new IllegalStateException(String.format("App '%s' is not deployed", appId));
		}

		try {
			deleteAllObjects(appId);
		}
		catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public AppStatus status(String appId) {
		Map<String, String> selector = new HashMap<>();
		ServiceList services = client.services().withLabel(SPRING_APP_KEY, appId).list();
		selector.put(SPRING_APP_KEY, appId);
		PodList podList = client.pods().withLabels(selector).list();
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Building AppStatus for app: %s", appId));
			if (podList != null && podList.getItems() != null) {
				logger.debug(String.format("Pods for appId %s: %d", appId, podList.getItems().size()));
				for (Pod pod : podList.getItems()) {
					logger.debug(String.format("Pod: %s", pod.getMetadata().getName()));
				}
			}
		}
		AppStatus status = buildAppStatus(appId, podList, services);
		logger.debug(String.format("Status for app: %s is %s", appId, status));

		return status;
	}

	@Override
	public String getLog(String appId) {
		Map<String, String> selector = new HashMap<>();
		selector.put(SPRING_APP_KEY, appId);
		PodList podList = client.pods().withLabels(selector).list();
		StringBuilder logAppender = new StringBuilder();
		for (Pod pod : podList.getItems()) {

			if(pod.getSpec().getContainers().size() > 1){
				for(Container container : pod.getSpec().getContainers()) {
					if(container.getEnv().stream().anyMatch(envVar -> "SPRING_CLOUD_APPLICATION_GUID".equals(envVar.getName()))) {
						//find log for this container
						logAppender.append(this.client.pods()
												   .withName(pod.getMetadata().getName())
												   .inContainer(container.getName())
												   .tailingLines(500).getLog());
						break;
					}

				}
			}
			else{
				logAppender.append(this.client.pods().withName(pod.getMetadata().getName()).tailingLines(500).getLog());
			}
		}

		return logAppender.toString();
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		String deploymentId = appScaleRequest.getDeploymentId();
		logger.debug(String.format("Scale app: %s to: %s", deploymentId, appScaleRequest.getCount()));

		ScalableResource scalableResource = this.client.apps().deployments().withName(deploymentId);
		if (scalableResource.get() == null) {
			scalableResource = this.client.apps().statefulSets().withName(deploymentId);
		}
		if (scalableResource.get() == null) {
			throw new IllegalStateException(String.format("App '%s' is not deployed", deploymentId));
		}
		scalableResource.scale(appScaleRequest.getCount(), true);
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(AppDeployer.class, this.getClass());
	}

	private Deployment createDeployment(AppDeploymentRequest request) {

		String appId = createDeploymentId(request);

		logger.debug(String.format("Creating Deployment: %s", appId));

		int replicas = getCountFromRequest(request);

		Map<String, String> idMap = createIdMap(appId, request);

		Map<String, String> kubernetesDeployerProperties = request.getDeploymentProperties();

		Map<String, String> annotations = this.deploymentPropertiesResolver.getPodAnnotations(kubernetesDeployerProperties);
		Map<String, String> deploymentLabels = this.deploymentPropertiesResolver.getDeploymentLabels(kubernetesDeployerProperties);

		PodSpec podSpec = createPodSpec(request);

		Deployment d = new DeploymentBuilder().withNewMetadata().withName(appId).withLabels(idMap)
				.addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE).addToLabels(deploymentLabels).endMetadata()
				.withNewSpec().withNewSelector().addToMatchLabels(idMap).endSelector().withReplicas(replicas)
				.withNewTemplate().withNewMetadata().withLabels(idMap).addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE)
				.addToLabels(deploymentLabels).withAnnotations(annotations).endMetadata().withSpec(podSpec).endTemplate()
				.endSpec().build();

		return client.apps().deployments().create(d);
	}

	private int getCountFromRequest(AppDeploymentRequest request) {
		String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
		return (countProperty != null) ? Integer.parseInt(countProperty) : 1;
	}

	/**
	 * Create a StatefulSet
	 *
	 * @param request the {@link AppDeploymentRequest}
	 */
	protected void createStatefulSet(AppDeploymentRequest request) {

		String appId = createDeploymentId(request);

		int externalPort = getExternalPort(request);

		Map<String, String> idMap = createIdMap(appId, request);

		int replicas = getCountFromRequest(request);

		Map<String, String> kubernetesDeployerProperties = request.getDeploymentProperties();

		logger.debug(String.format("Creating StatefulSet: %s on %d with %d replicas", appId, externalPort, replicas));

		Map<String, Quantity> storageResource = Collections.singletonMap("storage",
				new Quantity(this.deploymentPropertiesResolver.getStatefulSetStorage(kubernetesDeployerProperties)));

		String storageClassName = this.deploymentPropertiesResolver.getStatefulSetStorageClassName(kubernetesDeployerProperties);

		String volumeClaimTemplateName = this.deploymentPropertiesResolver.getStatefulSetVolumeClaimTemplateName(kubernetesDeployerProperties);

		volumeClaimTemplateName = StringUtils.hasText(volumeClaimTemplateName) ? volumeClaimTemplateName : appId;

		PersistentVolumeClaimBuilder persistentVolumeClaimBuilder = new PersistentVolumeClaimBuilder().withNewSpec().
				withStorageClassName(storageClassName).withAccessModes(Collections.singletonList("ReadWriteOnce"))
				.withNewResources().addToLimits(storageResource).addToRequests(storageResource).endResources()
				.endSpec().withNewMetadata().withName(volumeClaimTemplateName).withLabels(idMap)
				.addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE).endMetadata();

		PodSpec podSpec = createPodSpec(request);

		podSpec.getVolumes().add(new VolumeBuilder().withName("config").withNewEmptyDir().endEmptyDir().build());

		podSpec.getContainers().get(0).getVolumeMounts()
			.add(new VolumeMountBuilder().withName("config").withMountPath("/config").build());

		String statefulSetInitContainerImageName = this.deploymentPropertiesResolver.getStatefulSetInitContainerImageName(kubernetesDeployerProperties);

		podSpec.getInitContainers().add(createStatefulSetInitContainer(statefulSetInitContainerImageName));

		Map<String, String> deploymentLabels=  this.deploymentPropertiesResolver.getDeploymentLabels(request.getDeploymentProperties());
		Map<String, String> annotations = this.deploymentPropertiesResolver.getPodAnnotations(kubernetesDeployerProperties);

		StatefulSetSpec spec = new StatefulSetSpecBuilder().withNewSelector().addToMatchLabels(idMap)
				.addToMatchLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE).endSelector()
				.withVolumeClaimTemplates(persistentVolumeClaimBuilder.build()).withServiceName(appId)
				.withPodManagementPolicy("Parallel").withReplicas(replicas).withNewTemplate().withNewMetadata()
				.withLabels(idMap).addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE).addToLabels(deploymentLabels)
				.addToAnnotations(annotations).endMetadata().withSpec(podSpec).endTemplate().build();

		StatefulSet statefulSet = new StatefulSetBuilder().withNewMetadata().withName(appId).withLabels(idMap)
				.addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE).addToLabels(deploymentLabels).endMetadata().withSpec(spec).build();

		client.apps().statefulSets().create(statefulSet);
	}

	protected void createService(AppDeploymentRequest request) {

		String appId = createDeploymentId(request);

		int externalPort = getExternalPort(request);

		logger.debug(String.format("Creating Service: %s on %d", appId, externalPort));

		Map<String, String> idMap = createIdMap(appId, request);

		ServiceSpecBuilder spec = new ServiceSpecBuilder();
		boolean isCreateLoadBalancer = false;
		String createLoadBalancer = PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
			"spring.cloud.deployer.kubernetes.createLoadBalancer");
		String createNodePort = PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
			"spring.cloud.deployer.kubernetes.createNodePort");
		String additionalServicePorts = PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
				"spring.cloud.deployer.kubernetes.servicePorts");

		if (createLoadBalancer != null && createNodePort != null) {
			throw new IllegalArgumentException("Cannot create NodePort and LoadBalancer at the same time.");
		}

		if (createLoadBalancer == null) {
			isCreateLoadBalancer = properties.isCreateLoadBalancer();
		}
		else {
			if ("true".equals(createLoadBalancer.toLowerCase())) {
				isCreateLoadBalancer = true;
			}
		}

		if (isCreateLoadBalancer) {
			spec.withType("LoadBalancer");
		}

		ServicePort servicePort = new ServicePort();
		servicePort.setPort(externalPort);
		servicePort.setName("port-" + externalPort);

		if (createNodePort != null) {
			spec.withType("NodePort");
			if (!"true".equals(createNodePort.toLowerCase())) {
				try {
					Integer nodePort = Integer.valueOf(createNodePort);
					servicePort.setNodePort(nodePort);
				}
				catch (NumberFormatException e) {
					throw new IllegalArgumentException(
						String.format("Invalid value: %s: provided port is not valid.", createNodePort));
				}
			}
		}

		Set<ServicePort> servicePorts = new HashSet<>();
		servicePorts.add(servicePort);

		if (StringUtils.hasText(additionalServicePorts)) {
			servicePorts.addAll(addAdditionalServicePorts(additionalServicePorts));
		}

		spec.addAllToPorts(servicePorts);

		Map<String, String> annotations = this.deploymentPropertiesResolver.getServiceAnnotations(request.getDeploymentProperties());

		String serviceName = getServiceName(request, appId);

		// if called from skipper, use unique selectors to maintain connectivity
		// between service and pods that are being brought up/down
		if (request.getDeploymentProperties().containsKey(APP_NAME_PROPERTY_KEY)) {
			spec.withSelector(Collections.singletonMap(APP_NAME_KEY,
					request.getDeploymentProperties().get(APP_NAME_PROPERTY_KEY)));

			String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);

			if (groupId != null) {
				spec.addToSelector(SPRING_GROUP_KEY, groupId);
			}
		} else {
			spec.withSelector(idMap);
		}

		client.services().createOrReplaceWithNew().withNewMetadata().withName(serviceName)
			.withLabels(idMap).withAnnotations(annotations).addToLabels(SPRING_MARKER_KEY, SPRING_MARKER_VALUE)
			.endMetadata().withSpec(spec.build()).done();
	}

	// logic to support using un-versioned service names when called from skipper
	private String getServiceName(AppDeploymentRequest request, String appId) {
		String appName = request.getDeploymentProperties().get(APP_NAME_PROPERTY_KEY);

		// if we have an un-versioned app name from skipper
		if(StringUtils.hasText(appName)) {
			String serviceName = formatServiceName(request, appName);

			// need to check if a versioned service exists to maintain backwards compat..
			// version number itself isn't checked on as it could be different if create or upgrade
			// which we don't know at runtime....
			List<Service> services = client.services().withLabel(SPRING_DEPLOYMENT_KEY).list().getItems();

			for (Service service : services) {
				String serviceMetadataName = service.getMetadata().getName();

				if(serviceMetadataName.startsWith(serviceName + "-v")) {
					return appId;
				}
			}

			return serviceName;
		}

		// no un-versioned app name provided, maybe not called from skipper, return whatever is in appId
		return appId;
	}

	private String formatServiceName(AppDeploymentRequest request, String appName) {
		String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);

		String serviceName = groupId == null ? String.format("%s", appName)
				: String.format("%s-%s", groupId, appName);

		return serviceName.replace('.', '-').toLowerCase();
	}

	private Set<ServicePort> addAdditionalServicePorts(String additionalServicePorts) {
		Set<ServicePort> ports = new HashSet<>();

		String[] servicePorts = additionalServicePorts.split(",");
		for (String servicePort : servicePorts) {
			Integer port = Integer.parseInt(servicePort.trim());
			ServicePort extraServicePort = new ServicePort();
			extraServicePort.setPort(port);
			extraServicePort.setName("port-" + port);

			ports.add(extraServicePort);
		}

		return ports;
	}

	/**
	 * For StatefulSets, create an init container to parse ${HOSTNAME} to get the `instance.index` and write it to
	 * config/application.properties on a shared volume so the main container has it. Using the legacy annotation
	 * configuration since the current client version does not directly support InitContainers.
	 *
	 * Since 1.8 the annotation method has been removed, and the initContainer API is supported since 1.6
	 *
	 * @param imageName the image name to use in the init container
	 * @return a container definition with the above mentioned configuration
	 */
	private Container createStatefulSetInitContainer(String imageName) {
		List<String> command = new LinkedList<>();

		String commandString = String
				.format("%s && %s", setIndexProperty("INSTANCE_INDEX"), setIndexProperty("spring.application.index"));

		command.add("sh");
		command.add("-c");
		command.add(commandString);
		return new ContainerBuilder().withName("index-provider")
				.withImage(imageName)
				.withImagePullPolicy("IfNotPresent")
				.withCommand(command)
				.withVolumeMounts(new VolumeMountBuilder().withName("config").withMountPath("/config").build())
				.build();
	}

	private String setIndexProperty(String name) {
		return String
			.format("echo %s=\"$(expr $HOSTNAME | grep -o \"[[:digit:]]*$\")\" >> /config/application" + ".properties",
				name);
	}

	private void deleteAllObjects(String appIdToDelete) {
		Map<String, String> labels = Collections.singletonMap(SPRING_APP_KEY, appIdToDelete);

		waitForLoadBalancerReady(labels);
		deleteService(labels);
		deleteDeployment(labels);
		deleteStatefulSet(labels);
		deletePod(labels);
		deletePvc(labels);
	}

	private void deleteService(Map<String, String> labels) {
		FilterWatchListDeletable<Service, ServiceList, Boolean, Watch> servicesToDelete =
				client.services().withLabels(labels);

		if (servicesToDelete != null && servicesToDelete.list().getItems() != null) {
			boolean servicesDeleted = servicesToDelete.delete();
			logger.debug(String.format("Service deleted for: %s - %b", labels, servicesDeleted));
		}
	}

	private void deleteDeployment(Map<String, String> labels) {
		FilterWatchListDeletable<Deployment, DeploymentList, Boolean, Watch> deploymentsToDelete =
				client.apps().deployments().withLabels(labels);

		if (deploymentsToDelete != null && deploymentsToDelete.list().getItems() != null) {
			boolean deploymentsDeleted = deploymentsToDelete.delete();
			logger.debug(String.format("Deployment deleted for: %s - %b", labels, deploymentsDeleted));
		}
	}

	private void deleteStatefulSet(Map<String, String> labels) {
		FilterWatchListDeletable<StatefulSet, StatefulSetList, Boolean, Watch> ssToDelete =
				client.apps().statefulSets().withLabels(labels);

		if (ssToDelete != null && ssToDelete.list().getItems() != null) {
			boolean ssDeleted = ssToDelete.delete();
			logger.debug(String.format("StatefulSet deleted for: %s - %b", labels, ssDeleted));
		}
	}

	private void deletePod(Map<String, String> labels) {
		FilterWatchListDeletable<Pod, PodList, Boolean, Watch> podsToDelete = client.pods()
				.withLabels(labels);

		if (podsToDelete != null && podsToDelete.list().getItems() != null) {
			boolean podsDeleted = podsToDelete.delete();
			logger.debug(String.format("Pod deleted for: %s - %b", labels, podsDeleted));
		}
	}

	private void deletePvc(Map<String, String> labels) {
		FilterWatchListDeletable<PersistentVolumeClaim, PersistentVolumeClaimList, Boolean, Watch> pvcsToDelete = client.persistentVolumeClaims()
				.withLabels(labels);

		if (pvcsToDelete != null && pvcsToDelete.list().getItems() != null) {
			boolean pvcsDeleted = pvcsToDelete.delete();
			logger.debug(String.format("PVC deleted for: %s - %b", labels, pvcsDeleted));
		}
	}

	private void waitForLoadBalancerReady(Map<String, String> labels) {
		List<Service> services = client.services().withLabels(labels).list().getItems();

		if (!services.isEmpty()) {
			Service svc = services.get(0);
			if (svc != null && "LoadBalancer".equals(svc.getSpec().getType())) {
				int tries = 0;
				int maxWait = properties.getMinutesToWaitForLoadBalancer() * 6; // we check 6 times per minute
				while (tries++ < maxWait) {
					if (svc.getStatus() != null && svc.getStatus().getLoadBalancer() != null
							&& svc.getStatus().getLoadBalancer().getIngress() != null && svc.getStatus()
							.getLoadBalancer().getIngress().isEmpty()) {
						if (tries % 6 == 0) {
							logger.warn("Waiting for LoadBalancer to complete before deleting it ...");
						}
						logger.debug(String.format("Waiting for LoadBalancer, try %d", tries));
						try {
							Thread.sleep(10000L);
						}
						catch (InterruptedException e) {
						}
						svc = client.services().withLabels(labels).list().getItems().get(0);
					}
					else {
						break;
					}
				}
				logger.debug(String.format("LoadBalancer Ingress: %s",
						svc.getStatus().getLoadBalancer().getIngress().toString()));
			}
		}
	}
}
