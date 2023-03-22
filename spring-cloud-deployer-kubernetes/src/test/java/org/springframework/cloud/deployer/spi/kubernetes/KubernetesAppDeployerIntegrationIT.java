/*
 * Copyright 2016-2021 the original author or authors.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.HostPathVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationJUnit5Tests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration tests for {@link KubernetesAppDeployer}.
 *
 * @author Thomas Risberg
 * @author Donovan Muller
 * @author David Turanski
 * @author Chris Schaefer
 * @author Christian Tzolov
 */
@SpringBootTest(classes = {KubernetesAutoConfiguration.class}, properties = {
		"logging.level.org.springframework.cloud.deployer.spi=INFO"
})
public class KubernetesAppDeployerIntegrationIT extends AbstractAppDeployerIntegrationJUnit5Tests {

	@Autowired
	private AppDeployer appDeployer;

	@Autowired
	private KubernetesClient kubernetesClient;

	@Override
	protected AppDeployer provideAppDeployer() {
		return appDeployer;
	}

	@BeforeEach
	public void setup() {
		if (kubernetesClient.getNamespace() == null) {
			kubernetesClient.getConfiguration().setNamespace("default");
		}
	}

	@Test
	public void testScaleStatefulSet() {
		log.info("Testing {}...", "ScaleStatefulSet");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();

		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer appDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		Timeout timeout = deploymentTimeout();
		String deploymentId = appDeployer.deploy(request);

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		// assertThat(deploymentId, eventually(appInstanceCount(is(3))));
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(3);
		});

		// Ensure that a StatefulSet is deployed
		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);
		List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();
		assertThat(statefulSets).isNotNull();
		assertThat(statefulSets).hasSize(1);
		StatefulSet statefulSet = statefulSets.get(0);
		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
		Assertions.assertThat(statefulSetSpec.getPodManagementPolicy()).isEqualTo("Parallel");
		Assertions.assertThat(statefulSetSpec.getReplicas()).isEqualTo(3);
		Assertions.assertThat(statefulSetSpec.getServiceName()).isEqualTo(deploymentId);
		Assertions.assertThat(statefulSet.getMetadata().getName()).isEqualTo(deploymentId);

		log.info("Scale Down {}...", request.getDefinition().getName());
		appDeployer.scale(new AppScaleRequest(deploymentId, 1));
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(1);
		});

		statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();
		assertThat(statefulSets).hasSize(1);
		statefulSetSpec = statefulSets.get(0).getSpec();
		Assertions.assertThat(statefulSetSpec.getReplicas()).isEqualTo(1);
		Assertions.assertThat(statefulSetSpec.getServiceName()).isEqualTo(deploymentId);
		Assertions.assertThat(statefulSet.getMetadata().getName()).isEqualTo(deploymentId);

		appDeployer.undeploy(deploymentId);
	}

	@Test
	public void testScaleDeployment() {
		log.info("Testing {}...", "ScaleDeployment");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();

		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer appDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.emptyMap());

		log.info("Deploying {}...", request.getDefinition().getName());
		Timeout timeout = deploymentTimeout();
		String deploymentId = appDeployer.deploy(request);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(1);
		});

		log.info("Scale Up {}...", request.getDefinition().getName());
		appDeployer.scale(new AppScaleRequest(deploymentId, 3));
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(3);
		});

		log.info("Scale Down {}...", request.getDefinition().getName());
		appDeployer.scale(new AppScaleRequest(deploymentId, 1));
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(1);
		});

		appDeployer.undeploy(deploymentId);
	}

	@Test
	public void testScaleWithNonExistingApps() {
		assertThatThrownBy(() -> {
			appDeployer.scale(new AppScaleRequest("Fake App", 10));
		}).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void testFailedDeploymentWithLoadBalancer() {
		log.info("Testing {}...", "FailedDeploymentWithLoadBalancer");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setCreateLoadBalancer(true);
		deployProperties.setLivenessHttpProbePeriod(10);
		deployProperties.setMaxTerminatedErrorRestarts(1);
		deployProperties.setMaxCrashLoopBackOffRestarts(1);
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer lbAppDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.livenessHttpProbePath", "/invalidpath");
		props.put("spring.cloud.deployer.kubernetes.livenessHttpProbeDelay", "1");
		props.put("spring.cloud.deployer.kubernetes.livenessHttpProbePeriod", "1");

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.failed);
		});

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testGoodDeploymentWithLoadBalancer() {
		log.info("Testing {}...", "GoodDeploymentWithLoadBalancer");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setCreateLoadBalancer(true);
		deployProperties.setMinutesToWaitForLoadBalancer(1);
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer lbAppDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

	}

	@Test
	@Disabled("Disabled until we can test lbs")
	public void testDeploymentWithLoadBalancerHasUrlAndAnnotation() {
		log.info("Testing {}...", "DeploymentWithLoadBalancerShowsUrl");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setCreateLoadBalancer(true);
		deployProperties.setMinutesToWaitForLoadBalancer(1);
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer lbAppDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.serviceAnnotations", "foo:bar,fab:baz"));

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		log.info("Checking instance attributes of {}...", request.getDefinition().getName());
		AppStatus status = lbAppDeployer.status(deploymentId);
		for (String inst : status.getInstances().keySet()) {
			appDeployer().status(deploymentId).getInstances().get(inst);
			await().pollInterval(Duration.ofMillis(timeout.pause))
					.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
					.untilAsserted(() -> {
				assertThat(appDeployer().status(deploymentId).getInstances().get(inst).getAttributes()).containsKey("url");
			});

		}
		log.info("Checking service annotations of {}...", request.getDefinition().getName());
		Map<String, String> annotations = kubernetesClient.services().withName(request.getDefinition().getName()).get()
				.getMetadata().getAnnotations();
		assertThat(annotations).isNotNull();
		assertThat(annotations).hasSize(2);
		assertThat(annotations).containsKey("foo");
		assertThat(annotations.get("foo")).isEqualTo("bar");
		assertThat(annotations).containsKey("fab");
		assertThat(annotations.get("fab")).isEqualTo("baz");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDeploymentWithPodAnnotation() {
		log.info("Testing {}...", "DeploymentWithPodAnnotation");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer appDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.podAnnotations",
						"iam.amazonaws.com/role:role-arn,foo:bar"));

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		log.info("Checking pod spec annotations of {}...", request.getDefinition().getName());

		List<Pod> pods = kubernetesClient.pods().withLabel("spring-deployment-id", request.getDefinition()
				.getName()).list().getItems();

		assertThat(pods).hasSize(1);

		Pod pod = pods.get(0);
		Map<String, String> annotations = pod.getMetadata().getAnnotations();
		log.info("Number of annotations found" + annotations.size());
		for (Map.Entry<String, String> annotationsEntry : annotations.entrySet()) {
			log.info("Annotation key: " + annotationsEntry.getKey());
		}
		assertThat(annotations.get("iam.amazonaws.com/role")).isEqualTo("role-arn");
		assertThat(annotations.get("foo")).isEqualTo("bar");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDeploymentWithMountedHostPathVolume() throws IOException {
		log.info("Testing {}...", "DeploymentWithMountedVolume");
		String containerPath = "/tmp/";
		String subPath = randomName();
		String mountName = "mount";

		HostPathVolumeSource hostPathVolumeSource = new HostPathVolumeSourceBuilder()
				.withPath("/tmp/" + randomName() + '/').build();

		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setVolumes(Collections.singletonList(new VolumeBuilder()
				.withHostPath(hostPathVolumeSource)
				.withName(mountName)
				.build()));
		deployProperties.setVolumeMounts(Collections.singletonList(new VolumeMount(hostPathVolumeSource.getPath(), null,
				mountName, false, null, null)));
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer lbAppDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(),
				Collections.singletonMap("logging.file", containerPath + subPath));
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);
		PodSpec spec = kubernetesClient.pods().withLabels(selector).list().getItems().get(0).getSpec();
		assertThat(spec.getVolumes()).isNotNull();
		Volume volume = spec.getVolumes().stream()
				.filter(v -> mountName.equals(v.getName()))
				.findAny()
				.orElseThrow(() -> new AssertionError("Volume not mounted"));
		assertThat(volume.getHostPath()).isNotNull();
		assertThat(hostPathVolumeSource.getPath()).isEqualTo(volume.getHostPath().getPath());

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	private void verifyAppEnv(String appId) {
		String ip = "";
		int port = 0;

		KubernetesDeployerProperties properties = new KubernetesDeployerProperties();
		boolean success = false;

		Service svc = kubernetesClient.services().withName(appId).get();
		RestTemplate restTemplate = new RestTemplate();

		if (svc != null && "LoadBalancer".equals(svc.getSpec().getType())) {
			int tries = 0;
			int maxWait = properties.getMinutesToWaitForLoadBalancer() * 6; // we check 6 times per minute
			while (tries++ < maxWait && !success) {
				if (svc.getStatus() != null && svc.getStatus().getLoadBalancer() != null &&
						svc.getStatus().getLoadBalancer().getIngress() != null &&
						!(svc.getStatus().getLoadBalancer().getIngress().isEmpty())) {
					ip = svc.getStatus().getLoadBalancer().getIngress().get(0).getIp();
					if (ip == null) {
						ip = svc.getStatus().getLoadBalancer().getIngress().get(0).getHostname();
					}
					port = svc.getSpec().getPorts().get(0).getPort();
					success = true;
					try {
						String url = String.format("http://%s:%d/actuator/env", ip, port);
						restTemplate.exchange(url,
								HttpMethod.GET, HttpEntity.EMPTY,
								new ParameterizedTypeReference<LinkedHashMap<String, ArrayList<LinkedHashMap>>>() {
									@Override
									public Type getType() {
										return Map.class;
									}
								});
					}
					catch(ResourceAccessException rae) {
						success = false;
						try {
							Thread.sleep(5000L);
						}
						catch (InterruptedException e) {
						}
					}
				}
				else {
					try {
						Thread.sleep(5000L);
					}
					catch (InterruptedException e) {
					}
					svc = kubernetesClient.services().withName(appId).get();
				}
			}
			log.debug(String.format("LoadBalancer Ingress: %s",
					svc.getStatus().getLoadBalancer().getIngress().toString()));
		}

		assertThat(success).as("cannot get service information for " + appId).isFalse();

		String url = String.format("http://%s:%d/actuator/env", ip, port);
		log.debug("getting app environment from " + url);
		restTemplate = new RestTemplate();

		ResponseEntity<LinkedHashMap<String, ArrayList<LinkedHashMap>>> response = restTemplate.exchange(url,
				HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<LinkedHashMap<String, ArrayList<LinkedHashMap>>>() {
					@Override
					public Type getType() {
						return Map.class;
					}
				});

		LinkedHashMap<String, ArrayList<LinkedHashMap>> env = response.getBody();
		ArrayList<LinkedHashMap> propertySources = env.get("propertySources");

		String hostName = null;
		String instanceIndex = null;

		for (LinkedHashMap propertySource : propertySources) {
			if (propertySource.get("name").equals("systemEnvironment")) {
				LinkedHashMap s = (LinkedHashMap) propertySource.get("properties");
				hostName = (String) ((LinkedHashMap) s.get("HOSTNAME")).get("value");
			}

			if (propertySource.get("name").equals("applicationConfig: [file:./config/application.properties]")) {
				LinkedHashMap s = (LinkedHashMap) propertySource.get("properties");
				instanceIndex = (String) ((LinkedHashMap) s.get("INSTANCE_INDEX")).get("value");
			}
		}

		assertThat(hostName).as("Hostname is null").isNotNull();
		assertThat(instanceIndex).as("Instance index is null").isNotNull();

		String expectedIndex = hostName.substring(hostName.lastIndexOf("-") + 1);
		assertThat(instanceIndex).isEqualTo(expectedIndex);
	}

	@Test
	@Disabled("Disabled until we can test lbs")
	public void testDeploymentWithGroupAndIndex() throws IOException {
		log.info("Testing {}...", "DeploymentWithWithGroupAndIndex");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setCreateLoadBalancer(true);
		deployProperties.setMinutesToWaitForLoadBalancer(1);
		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer testAppDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("security.basic.enabled", "false");
		appProperties.put("management.security.enabled", "false");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		Map<String, String> props = new HashMap<>();
		props.put(AppDeployer.GROUP_PROPERTY_KEY, "foo");
		props.put(AppDeployer.INDEXED_PROPERTY_KEY, "true");

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = testAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);
		PodSpec spec = kubernetesClient.pods().withLabels(selector).list().getItems().get(0).getSpec();

		Map<String, String> envVars = new HashMap<>();
		for (EnvVar e : spec.getContainers().get(0).getEnv()) {
			envVars.put(e.getName(), e.getValue());
		}
		assertThat(envVars).contains(entry("SPRING_CLOUD_APPLICATION_GROUP", "foo"));
		verifyAppEnv(deploymentId);

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		testAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDeploymentServiceAccountName() {
		log.info("Testing {}...", "DeploymentServiceAccountName");

		ServiceAccount deploymentServiceAccount = new ServiceAccountBuilder().withNewMetadata().withName("appsa")
				.endMetadata().build();

		this.kubernetesClient.serviceAccounts().create(deploymentServiceAccount);

		String serviceAccountName = deploymentServiceAccount.getMetadata().getName();

		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();
		deployProperties.setDeploymentServiceAccountName(serviceAccountName);

		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer appDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication());

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.serviceAccounts().delete(deploymentServiceAccount);
	}

	@Test
	public void testCreateStatefulSet() throws Exception {
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());
		String deploymentId = deployer.deploy(appDeploymentRequest);
		Map<String, String> idMap = deployer.createIdMap(deploymentId, appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();

		assertThat(statefulSets).hasSize(1);

		StatefulSet statefulSet = statefulSets.get(0);

		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();

		List<Container> statefulSetInitContainers = statefulSetSpec.getTemplate().getSpec().getInitContainers();
		assertThat(statefulSetInitContainers).hasSize(1);
		Container statefulSetInitContainer = statefulSetInitContainers.get(0);
		assertThat(statefulSetInitContainer.getImage()).isEqualTo(DeploymentPropertiesResolver.STATEFUL_SET_IMAGE_NAME);

		Assertions.assertThat(statefulSetSpec.getPodManagementPolicy()).isEqualTo("Parallel");
		Assertions.assertThat(statefulSetSpec.getReplicas()).isEqualTo(3);
		Assertions.assertThat(statefulSetSpec.getServiceName()).isEqualTo(deploymentId);

		Assertions.assertThat(statefulSet.getMetadata().getName()).isEqualTo(deploymentId);

		Assertions.assertThat(statefulSetSpec.getSelector().getMatchLabels())
				.containsAllEntriesOf(deployer.createIdMap(deploymentId, appDeploymentRequest));
		Assertions.assertThat(statefulSetSpec.getSelector().getMatchLabels())
				.contains(entry(KubernetesAppDeployer.SPRING_MARKER_KEY, KubernetesAppDeployer.SPRING_MARKER_VALUE));

		Assertions.assertThat(statefulSetSpec.getTemplate().getMetadata().getLabels()).containsAllEntriesOf(idMap);
		Assertions.assertThat(statefulSetSpec.getTemplate().getMetadata().getLabels())
				.contains(entry(KubernetesAppDeployer.SPRING_MARKER_KEY, KubernetesAppDeployer.SPRING_MARKER_VALUE));

		Container container = statefulSetSpec.getTemplate().getSpec().getContainers().get(0);

		Assertions.assertThat(container.getName()).isEqualTo(deploymentId);
		Assertions.assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
		Assertions.assertThat(container.getImage()).isEqualTo(testApplication().getURI().getSchemeSpecificPart());

		PersistentVolumeClaim pvc = statefulSetSpec.getVolumeClaimTemplates().get(0);
		Assertions.assertThat(pvc.getMetadata().getName()).isEqualTo(deploymentId);

		PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();
		Assertions.assertThat(pvcSpec.getAccessModes()).containsOnly("ReadWriteOnce");
		Assertions.assertThat(pvcSpec.getStorageClassName()).isNull();

		Assertions.assertThat(pvcSpec.getResources().getLimits().get("storage").getAmount()).isEqualTo("10");
		Assertions.assertThat(pvcSpec.getResources().getRequests().get("storage").getAmount()).isEqualTo("10");

		Assertions.assertThat(pvcSpec.getResources().getLimits().get("storage").getFormat()).isEqualTo("Mi");
		Assertions.assertThat(pvcSpec.getResources().getRequests().get("storage").getFormat()).isEqualTo("Mi");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCreateStatefulSetInitContainerImageNamePropOverride() throws Exception {
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");

		String imageName = testApplication().getURI().getSchemeSpecificPart();

		props.put("spring.cloud.deployer.kubernetes.statefulSetInitContainerImageName", imageName);

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());
		String deploymentId = deployer.deploy(appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();

		assertThat(statefulSets).hasSize(1);

		StatefulSet statefulSet = statefulSets.get(0);

		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();

		List<Container> statefulSetInitContainers = statefulSetSpec.getTemplate().getSpec().getInitContainers();
		assertThat(statefulSetInitContainers).hasSize(1);
		Container statefulSetInitContainer = statefulSetInitContainers.get(0);
		assertThat(statefulSetInitContainer.getImage()).isEqualTo(imageName);

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void createStatefulSetInitContainerImageNameGlobalOverride() throws Exception {
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		String imageName = testApplication().getURI().getSchemeSpecificPart();

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setStatefulSetInitContainerImageName(imageName);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(kubernetesDeployerProperties, kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());
		String deploymentId = deployer.deploy(appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();

		assertThat(statefulSets).hasSize(1);

		StatefulSet statefulSet = statefulSets.get(0);

		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();

		List<Container> statefulSetInitContainers = statefulSetSpec.getTemplate().getSpec().getInitContainers();
		assertThat(statefulSetInitContainers).hasSize(1);
		Container statefulSetInitContainer = statefulSetInitContainers.get(0);
		assertThat(statefulSetInitContainer.getImage()).isEqualTo(imageName);

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void createStatefulSetWithOverridingRequest() throws Exception {
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");
		props.put("spring.cloud.deployer.kubernetes.statefulSet.volumeClaimTemplate.name", "mystorage");
		props.put("spring.cloud.deployer.kubernetes.statefulSet.volumeClaimTemplate.storage", "1g");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());
		String deploymentId = deployer.deploy(appDeploymentRequest);
		Map<String, String> idMap = deployer.createIdMap(deploymentId, appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		StatefulSet statefulSet = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems().get(0);
		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();

		Assertions.assertThat(statefulSetSpec.getPodManagementPolicy()).isEqualTo("Parallel");
		Assertions.assertThat(statefulSetSpec.getReplicas()).isEqualTo(3);
		Assertions.assertThat(statefulSetSpec.getServiceName()).isEqualTo(deploymentId);
		Assertions.assertThat(statefulSet.getMetadata().getName()).isEqualTo(deploymentId);

		Assertions.assertThat(statefulSetSpec.getSelector().getMatchLabels())
				.containsAllEntriesOf(deployer.createIdMap(deploymentId, appDeploymentRequest));
		Assertions.assertThat(statefulSetSpec.getSelector().getMatchLabels())
				.contains(entry(KubernetesAppDeployer.SPRING_MARKER_KEY, KubernetesAppDeployer.SPRING_MARKER_VALUE));

		Assertions.assertThat(statefulSetSpec.getTemplate().getMetadata().getLabels()).containsAllEntriesOf(idMap);
		Assertions.assertThat(statefulSetSpec.getTemplate().getMetadata().getLabels())
				.contains(entry(KubernetesAppDeployer.SPRING_MARKER_KEY, KubernetesAppDeployer.SPRING_MARKER_VALUE));

		Container container = statefulSetSpec.getTemplate().getSpec().getContainers().get(0);

		Assertions.assertThat(container.getName()).isEqualTo(deploymentId);
		Assertions.assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
		Assertions.assertThat(container.getImage()).isEqualTo(testApplication().getURI().getSchemeSpecificPart());

		PersistentVolumeClaim pvc = statefulSetSpec.getVolumeClaimTemplates().get(0);
		Assertions.assertThat(pvc.getMetadata().getName()).isEqualTo("mystorage");

		PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();
		Assertions.assertThat(pvcSpec.getAccessModes()).containsOnly("ReadWriteOnce");

		Assertions.assertThat(pvcSpec.getResources().getLimits().get("storage").getAmount()).isEqualTo("1");
		Assertions.assertThat(pvcSpec.getResources().getRequests().get("storage").getAmount()).isEqualTo("1");

		Assertions.assertThat(pvcSpec.getResources().getLimits().get("storage").getFormat()).isEqualTo("Gi");
		Assertions.assertThat(pvcSpec.getResources().getRequests().get("storage").getFormat()).isEqualTo("Gi");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void createStatefulSetWithPVCDefaultName() throws Exception {
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");
		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());
		String deploymentId = deployer.deploy(appDeploymentRequest);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
					assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
				});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		StatefulSet statefulSet = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems().get(0);
		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
		Container container = statefulSetSpec.getTemplate().getSpec().getContainers().get(0);

		Assertions.assertThat(container.getName()).isEqualTo(deploymentId);

		PersistentVolumeClaim pvc = statefulSetSpec.getVolumeClaimTemplates().get(0);
		Assertions.assertThat(pvc.getMetadata().getName()).isEqualTo(deploymentId);

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
					assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
				});
	}

	@Test
	public void testStatefulSetPodAnnotations() {
		log.info("Testing {}...", "StatefulSetPodAnnotations");
		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();

		ContainerFactory containerFactory = new DefaultContainerFactory(deployProperties);
		KubernetesAppDeployer appDeployer = new KubernetesAppDeployer(deployProperties, kubernetesClient,
				containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "3");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");
		props.put("spring.cloud.deployer.kubernetes.podAnnotations",
				"iam.amazonaws.com/role:role-arn,foo:bar");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		Timeout timeout = deploymentTimeout();
		String deploymentId = appDeployer.deploy(request);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getInstances()).hasSize(3);
		});

		// Ensure that a StatefulSet is deployed
		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);
		List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems();
		assertThat(statefulSets).isNotNull();
		assertThat(statefulSets).hasSize(1);
		StatefulSet statefulSet = statefulSets.get(0);
		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();

		Map<String, String> annotations = statefulSetSpec.getTemplate().getMetadata().getAnnotations();
		assertThat(annotations.get("iam.amazonaws.com/role")).isEqualTo("role-arn");
		assertThat(annotations.get("foo")).isEqualTo("bar");
		appDeployer.undeploy(deploymentId);
	}

	@Test
	public void testDeploymentLabels() {
		Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels",
				"label1:value1,label2:value2");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());

		String deploymentId = deployer.deploy(appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		List<Deployment> deployments = kubernetesClient.apps().deployments().withLabels(selector).list().getItems();

		Map<String, String> specLabels = deployments.get(0).getSpec().getTemplate().getMetadata().getLabels();

		assertThat(specLabels.containsKey("label1")).as("Label 'label1' not found in deployment spec").isTrue();
		assertThat(specLabels.get("label1")).as("Unexpected value for label1").isEqualTo("value1");
		assertThat(specLabels).as("Label 'label2' not found in deployment spec").containsKey("label2");
		assertThat(specLabels.get("label2")).as("Unexpected value for label1").isEqualTo("value2");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDeploymentLabelsStatefulSet() {
		log.info("Testing {}...", "DeploymentLabelsForStatefulSet");
		Map<String, String> props = new HashMap<>();
		props.put(KubernetesAppDeployer.COUNT_PROPERTY_KEY, "2");
		props.put(KubernetesAppDeployer.INDEXED_PROPERTY_KEY, "true");
		props.put("spring.cloud.deployer.kubernetes.deploymentLabels",
				"stateful-label1:stateful-value1,stateful-label2:stateful-value2");
		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), props);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());

		String deploymentId = deployer.deploy(appDeploymentRequest);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});
		Map<String, String> idMap = deployer.createIdMap(deploymentId, appDeploymentRequest);
		Map<String, String> selector = Collections.singletonMap(AbstractKubernetesDeployer.SPRING_APP_KEY, deploymentId);

		StatefulSet statefulSet = kubernetesClient.apps().statefulSets().withLabels(selector).list().getItems().get(0);
		StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
		Assertions.assertThat(statefulSetSpec.getReplicas()).isEqualTo(2);
		Assertions.assertThat(statefulSetSpec.getTemplate().getMetadata().getLabels()).containsAllEntriesOf(idMap);

		//verify stateful set match labels
		Map<String, String> setLabels = statefulSet.getMetadata().getLabels();
		assertThat(setLabels).contains(entry("stateful-label1", "stateful-value1"), entry("stateful-label2", "stateful-value2"));

		//verify pod template labels
		Map<String, String> specLabels = statefulSetSpec.getTemplate().getMetadata().getLabels();
		assertThat(specLabels).contains(entry("stateful-label1", "stateful-value1"), entry("stateful-label2", "stateful-value2"));

		//verify that labels got replicated to one of the deployments
		List<Pod> pods =  kubernetesClient.pods().withLabels(selector).list().getItems();
		Map<String, String> podLabels = pods.get(0).getMetadata().getLabels();

		assertThat(podLabels).contains(entry("stateful-label1", "stateful-value1"), entry("stateful-label2", "stateful-value2"));

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCleanupOnDeployFailure() throws InterruptedException {
		AppDefinition definition = new AppDefinition("app-test", null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testApplication(), null);

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

		// simulate a pod going into an un-schedulable state
		KubernetesDeployerProperties.LimitsResources resources = new KubernetesDeployerProperties.LimitsResources();
		resources.setCpu("9000000");

		kubernetesDeployerProperties.setLimits(resources);

		KubernetesAppDeployer deployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		log.info("Deploying {}...", appDeploymentRequest.getDefinition().getName());

		String deploymentId = deployer.deploy(appDeploymentRequest);

		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		// attempt to undeploy the failed deployment
		log.info("Undeploying {}...", deploymentId);

		try {
			appDeployer.undeploy(deploymentId);
		} catch (Exception e) {
			log.info("Got expected not not deployed exception on undeployment: " + e.getMessage());
		}

		deployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(), kubernetesClient);

		log.info("Deploying {}... again", deploymentId);

		// ensure a previous failed deployment with the same name was cleaned up and can be deployed again
		deployer.deploy(appDeploymentRequest);

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		appDeployer.undeploy(deploymentId);

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testMultipleContainersInPod() {
		log.info("Testing {}...", "MultipleContainersInPod");

		KubernetesDeployerProperties deployProperties = new KubernetesDeployerProperties();

		KubernetesAppDeployer kubernetesAppDeployer = Mockito.spy(new KubernetesAppDeployer(deployProperties,
				kubernetesClient, new DefaultContainerFactory(deployProperties)));

		AppDefinition definition = new AppDefinition(randomName(), Collections.singletonMap("server.port", "9090"));
		Resource resource = testApplication();

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		doAnswer((Answer<PodSpec>) invocationOnMock -> {
			PodSpec podSpec = (PodSpec) invocationOnMock.callRealMethod();

			Container container = new ContainerBuilder().withName("asecondcontainer")
					.withImage(resource.getURI().getSchemeSpecificPart()).build();

			podSpec.getContainers().add(container);

			return podSpec;
		}).when(kubernetesAppDeployer).createPodSpec(Mockito.any(AppDeploymentRequest.class));

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDefaultServicePort() {
		log.info("Testing {}...", "DefaultServicePort");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		List<ServicePort> servicePorts = kubernetesClient.services().withName(request.getDefinition().getName()).get()
				.getSpec().getPorts();

		assertThat(servicePorts).hasSize(1);
		assertThat(servicePorts.get(0).getPort()).isEqualTo(8080);
		assertThat(servicePorts.get(0).getName()).isEqualTo("port-8080");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testDefaultServicePortOverride() {
		log.info("Testing {}...", "DefaultServicePortOverride");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), Collections.singletonMap("server.port", "9090"));
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		List<ServicePort> servicePorts = kubernetesClient.services().withName(request.getDefinition().getName()).get()
				.getSpec().getPorts();

		assertThat(servicePorts).hasSize(1);
		assertThat(servicePorts.get(0).getPort()).isEqualTo(9090);
		assertThat(servicePorts.get(0).getName()).isEqualTo("port-9090");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testServiceWithMultiplePorts() {
		log.info("Testing {}...", "ServiceWithMultiplePorts");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.servicePorts", "8080,9090"));

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		List<ServicePort> servicePorts = kubernetesClient.services().withName(request.getDefinition().getName()).get()
				.getSpec().getPorts();

		assertThat(servicePorts).hasSize(2);
		assertThat(servicePorts.stream().anyMatch(o -> o.getPort().equals(8080))).isTrue();
		assertThat(servicePorts.stream().anyMatch(o -> o.getName().equals("port-8080"))).isTrue();
		assertThat(servicePorts.stream().anyMatch(o -> o.getPort().equals(9090))).isTrue();
		assertThat(servicePorts.stream().anyMatch(o -> o.getName().equals("port-9090"))).isTrue();

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCreateInitContainer() {
		log.info("Testing {}...", "CreateInitContainer");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.initContainer",
				"{containerName: 'test', imageName: 'busybox:latest', commands: ['sh', '-c', 'echo hello']}");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Deployment deployment = kubernetesClient.apps().deployments().withName(request.getDefinition().getName()).get();
		List<Container> initContainers = deployment.getSpec().getTemplate().getSpec().getInitContainers();

		Optional<Container> initContainer = initContainers.stream().filter(i -> i.getName().equals("test")).findFirst();
		assertThat(initContainer.isPresent()).as("Init container not found").isTrue();

		Container testInitContainer = initContainer.get();

		assertThat(testInitContainer.getName()).as("Unexpected init container name").isEqualTo("test");
		assertThat(testInitContainer.getImage()).as("Unexpected init container image").isEqualTo("busybox:latest");

		List<String> commands = testInitContainer.getCommand();

		assertThat(commands).contains("sh", "-c", "echo hello");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCreateInitContainerWithEnvVariables() {
		log.info("Testing {}...", "CreateInitContainer");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.initContainer",
				"{containerName: 'test', imageName: 'busybox:latest', commands: ['sh', '-c', 'echo hello'], environmentVariables: ['KEY1=VAL1', 'KEY2=VAL2']}");

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
					assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
				});

		Deployment deployment = kubernetesClient.apps().deployments().withName(request.getDefinition().getName()).get();
		List<Container> initContainers = deployment.getSpec().getTemplate().getSpec().getInitContainers();

		Optional<Container> initContainer = initContainers.stream().filter(i -> i.getName().equals("test")).findFirst();
		assertThat(initContainer.isPresent()).as("Init container not found").isTrue();

		Container testInitContainer = initContainer.get();

		List<EnvVar> containerEnvs = testInitContainer.getEnv();

		assertThat(containerEnvs).hasSize(2);
		assertThat(containerEnvs.stream().map(EnvVar::getName).collect(Collectors.toList())).contains("KEY1", "KEY2");
		assertThat(containerEnvs.stream().map(EnvVar::getValue).collect(Collectors.toList())).contains("VAL1", "VAL2");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
					assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
				});
	}

	@Test
	public void testCreateInitContainerWithVolumeMounts() {
		log.info("Testing {}...", "CreateInitContainerWithVolumeMounts");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = Stream.of(new String[][]{
				{
						"spring.cloud.deployer.kubernetes.volumes",
						"[{name: 'test-volume', emptyDir: {}}]",
				},
				{
						"spring.cloud.deployer.kubernetes.volumeMounts",
						"[{name: 'test-volume', mountPath: '/tmp'}]",
				},
				{
						"spring.cloud.deployer.kubernetes.initContainer",
						"{containerName: 'test', imageName: 'busybox:latest', commands: ['sh', '-c', 'echo hello'], " +
								"volumeMounts: [{name: 'test-volume', mountPath: '/tmp', readOnly: true}]}",
				}
		}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Deployment deployment = kubernetesClient.apps().deployments().withName(request.getDefinition().getName()).get();
		List<Container> initContainers = deployment.getSpec().getTemplate().getSpec().getInitContainers();

		Optional<Container> initContainer = initContainers.stream().filter(i -> i.getName().equals("test")).findFirst();
		assertThat(initContainer.isPresent()).as("Init container not found").isTrue();

		Container testInitContainer = initContainer.get();

		assertThat(testInitContainer.getName()).as("Unexpected init container name").isEqualTo("test");
		assertThat(testInitContainer.getImage()).as("Unexpected init container image").isEqualTo("busybox:latest");

		List<String> commands = testInitContainer.getCommand();

		assertThat(commands != null && !commands.isEmpty()).as("Init container commands missing").isTrue();
		assertThat(commands).hasSize(3);
		assertThat(commands).contains("sh", "-c", "echo hello");

		List<VolumeMount> volumeMounts = testInitContainer.getVolumeMounts();
		assertThat(volumeMounts != null && !volumeMounts.isEmpty()).as("Init container volumeMounts missing").isTrue();
		assertThat(volumeMounts).hasSize(1);

		VolumeMount vm = volumeMounts.get(0);
		assertThat(vm.getName()).as("Unexpected init container volume mount name").isEqualTo("test-volume");
		assertThat(vm.getMountPath()).as("Unexpected init container volume mount path").isEqualTo("/tmp");
		assertThat(vm.getReadOnly()).as("Expected read only volume mount").isTrue();

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCreateAdditionalContainers() {
		log.info("Testing {}...", "CreateAdditionalContainers");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = Stream.of(new String[][]{
				{
						"spring.cloud.deployer.kubernetes.volumes",
						"[{name: 'test-volume', emptyDir: {}}]",
				},
				{
						"spring.cloud.deployer.kubernetes.volumeMounts",
						"[{name: 'test-volume', mountPath: '/tmp'}]",
				},
				{
						"spring.cloud.deployer.kubernetes.additional-containers",
						"[{name: 'c1', image: 'busybox:latest', command: ['sh', '-c', 'echo hello1'], volumeMounts: [{name: 'test-volume', mountPath: '/tmp', readOnly: true}]},"
								+ "{name: 'c2', image: 'busybox:1.26.1', command: ['sh', '-c', 'echo hello2']}]"
				}}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Deployment deployment = kubernetesClient.apps().deployments().withName(request.getDefinition().getName()).get();
		List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();

		assertThat(containers).hasSize(3);

		Optional<Container> additionalContainer1 = containers.stream().filter(i -> i.getName().equals("c1")).findFirst();
		assertThat(additionalContainer1.isPresent()).isTrue();

		Container testAdditionalContainer1 = additionalContainer1.get();

		assertThat(testAdditionalContainer1.getName()).as("Unexpected additional container name").isEqualTo("c1");
		assertThat(testAdditionalContainer1.getImage()).as("Unexpected additional container image").isEqualTo("busybox:latest");

		List<String> commands = testAdditionalContainer1.getCommand();

		assertThat(commands).contains("sh", "-c", "echo hello1");

		List<VolumeMount> volumeMounts = testAdditionalContainer1.getVolumeMounts();

		assertThat(volumeMounts).hasSize(1);
		assertThat(volumeMounts.get(0).getName()).isEqualTo("test-volume");
		assertThat(volumeMounts.get(0).getMountPath()).isEqualTo("/tmp");
		assertThat(volumeMounts.get(0).getReadOnly()).isTrue();

		Optional<Container> additionalContainer2 = containers.stream().filter(i -> i.getName().equals("c2")).findFirst();
		assertThat(additionalContainer2.isPresent()).as("Additional container c2 not found").isTrue();

		Container testAdditionalContainer2 = additionalContainer2.get();

		assertThat(testAdditionalContainer2.getName()).as("Unexpected additional container name").isEqualTo("c2");
		assertThat(testAdditionalContainer2.getImage()).as("Unexpected additional container image").isEqualTo("busybox:1.26.1");

		List<String> container2Commands = testAdditionalContainer2.getCommand();

		assertThat(container2Commands).contains("sh", "-c", "echo hello2");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testCreateAdditionalContainersOverride() {
		log.info("Testing {}...", "CreateAdditionalContainersOverride");
		KubernetesDeployerProperties.Container container1 = new KubernetesDeployerProperties.Container();
		container1.setName("c1");
		container1.setImage("busybox:1.31.0");
		container1.setCommand(Arrays.asList("sh", "-c", "echo hello-from-original-properties"));
		KubernetesDeployerProperties.Container container2 = new KubernetesDeployerProperties.Container();
		container2.setName("container2");
		container2.setImage("busybox:1.31.0");
		container2.setCommand(Arrays.asList("sh", "-c", "echo hello-from-original-properties"));
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setAdditionalContainers(Arrays.asList(container1, container2));
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		Map<String, String> props = Stream.of(new String[][]{
				{
						"spring.cloud.deployer.kubernetes.volumes",
						"[{name: 'test-volume', emptyDir: {}}]",
				},
				{
						"spring.cloud.deployer.kubernetes.volumeMounts",
						"[{name: 'test-volume', mountPath: '/tmp'}]",
				},
				{
						"spring.cloud.deployer.kubernetes.additional-containers",
						"[{name: 'c1', image: 'busybox:latest', command: ['sh', '-c', 'echo hello1'], volumeMounts: [{name: 'test-volume', mountPath: '/tmp', readOnly: true}]},"
								+ "{name: 'c2', image: 'busybox:1.26.1', command: ['sh', '-c', 'echo hello2']}]"
				}}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

		AppDefinition definition = new AppDefinition(randomName(), null);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Deployment deployment = kubernetesClient.apps().deployments().withName(request.getDefinition().getName()).get();
		List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();

		assertThat(containers).hasSize(4);

		// c1 from the deployment properties should have overridden the c1 from the original deployer properties
		Optional<Container> additionalContainer1 = containers.stream().filter(i -> i.getName().equals("c1")).findFirst();
		assertThat(additionalContainer1.isPresent()).as("Additional container c1 not found").isTrue();

		Container testAdditionalContainer1 = additionalContainer1.get();

		assertThat(testAdditionalContainer1.getName()).as("Unexpected additional container name").isEqualTo("c1");
		assertThat(testAdditionalContainer1.getImage()).as("Unexpected additional container image").isEqualTo("busybox:latest");

		List<String> commands = testAdditionalContainer1.getCommand();

		assertThat(commands).contains("sh", "-c", "echo hello1");

		List<VolumeMount> volumeMounts = testAdditionalContainer1.getVolumeMounts();

		assertThat(volumeMounts).hasSize(1);
		assertThat(volumeMounts.get(0).getName()).isEqualTo("test-volume");
		assertThat(volumeMounts.get(0).getMountPath()).isEqualTo("/tmp");
		assertThat(volumeMounts.get(0).getReadOnly()).isTrue();

		Optional<Container> additionalContainer2 = containers.stream().filter(i -> i.getName().equals("c2")).findFirst();
		assertThat(additionalContainer2.isPresent()).as("Additional container c2 not found").isTrue();

		Container testAdditionalContainer2 = additionalContainer2.get();

		assertThat(testAdditionalContainer2.getName()).as("Unexpected additional container name").isEqualTo("c2");
		assertThat(testAdditionalContainer2.getImage()).as("Unexpected additional container image").isEqualTo("busybox:1.26.1");

		List<String> container2Commands = testAdditionalContainer2.getCommand();

		assertThat(container2Commands).contains("sh", "-c", "echo hello2");

		// Verifying the additional container passed from the root deployer properties
		Optional<Container> additionalContainer3 = containers.stream().filter(i -> i.getName().equals("container2")).findFirst();
		assertThat(additionalContainer3.isPresent()).as("Additional container c2 not found").isTrue();

		Container testAdditionalContainer3 = additionalContainer3.get();

		assertThat(testAdditionalContainer3.getName()).as("Unexpected additional container name").isEqualTo("container2");
		assertThat(testAdditionalContainer3.getImage()).as("Unexpected additional container image").isEqualTo("busybox:1.31.0");

		List<String> container3Commands = testAdditionalContainer3.getCommand();

		assertThat(container3Commands).contains("sh", "-c", "echo hello-from-original-properties");

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testUnknownStatusOnPendingResources() throws InterruptedException {
		log.info("Testing {}...", "UnknownStatusOnPendingResources");
		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		Map<String, String> props = new HashMap<>();
		// requests.cpu mirrors limits.cpu when only limits is set avoiding need to set both here
		props.put("spring.cloud.deployer.kubernetes.limits.cpu", "5000");

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);

		while(kubernetesClient.pods().withLabel("spring-deployment-id", deploymentId).list().getItems().isEmpty()) {
			log.info("Waiting for deployed pod");
			Thread.sleep(500);
		}

		Pod pod = kubernetesClient.pods().withLabel("spring-deployment-id", deploymentId).list().getItems().get(0);

		while (pod.getStatus().getConditions().isEmpty()) {
			log.info("Waiting for pod conditions to be set");
			Thread.sleep(500);
		}

		while(!"Unschedulable".equals(pod.getStatus().getConditions().get(0).getReason())) {
			log.info("Waiting for deployed pod to become Unschedulable");
		}

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();

		assertThatThrownBy(() -> {
			kubernetesAppDeployer.undeploy(deploymentId);
		}).isInstanceOf(IllegalStateException.class)
			.hasMessage("App '%s' is not deployed", deploymentId);

		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});
	}

	@Test
	public void testSecretRef() throws InterruptedException {
		log.info("Testing {}...", "SecretRef");

		Secret secret = randomSecret();

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setSecretRefs(Collections.singletonList(secret.getMetadata().getName()));

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties, kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(envFromSource.getSecretRef().getName()).isEqualTo(secret.getMetadata().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		assertThat(secret.getData()).hasSize(2);

		for(Map.Entry<String, String> secretData : secret.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(secretData.getValue()));
			assertThat(podEnvironment).contains(secretData.getKey() + "=" + decodedValue);
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.secrets().delete(secret);
	}

	@Test
	public void testSecretRefFromDeployerProperty() throws InterruptedException {
		log.info("Testing {}...", "SecretRefFromDeployerProperty");

		Secret secret = randomSecret();

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.secretRefs", secret.getMetadata().getName());

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(secret.getMetadata().getName()).isEqualTo(envFromSource.getSecretRef().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		assertThat(secret.getData()).hasSize(2);

		for(Map.Entry<String, String> secretData : secret.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(secretData.getValue()));
			assertThat(podEnvironment).contains(secretData.getKey() + "=" + decodedValue);
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.secrets().delete(secret);
	}

	@Test
	public void testSecretRefFromDeployerPropertyOverride() throws IOException, InterruptedException {
		log.info("Testing {}...", "SecretRefFromDeployerPropertyOverride");

		Secret propertySecret = randomSecret();
		Secret deployerPropertySecret = randomSecret();

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setSecretRefs(Collections.singletonList(propertySecret.getMetadata().getName()));

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.secretRefs", deployerPropertySecret.getMetadata().getName());

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(envFromSource.getSecretRef().getName()).isEqualTo(deployerPropertySecret.getMetadata().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		for(Map.Entry<String, String> deployerPropertySecretData : deployerPropertySecret.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(deployerPropertySecretData.getValue()));
			assertThat(podEnvironment).contains(deployerPropertySecretData.getKey() + "=" + decodedValue);
		}

		for(Map.Entry<String, String> propertySecretData : propertySecret.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(propertySecretData.getValue()));
			assertThat(podEnvironment).doesNotContain(propertySecretData.getKey() + "=" + decodedValue);
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.secrets().delete(propertySecret);
		kubernetesClient.secrets().delete(deployerPropertySecret);
	}

	@Test
	public void testSecretRefFromPropertyMultiple() throws InterruptedException {
		log.info("Testing {}...", "SecretRefFromPropertyMultiple");

		Secret secret1 = randomSecret();
		Secret secret2 = randomSecret();

		List<String> secrets = new ArrayList<>();
		secrets.add(secret1.getMetadata().getName());
		secrets.add(secret2.getMetadata().getName());

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setSecretRefs(secrets);

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, null);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(2);

		String podEnvironment = getPodEnvironment(deploymentId);

		EnvFromSource envFromSource1 = envFromSources.get(0);
		assertThat(envFromSource1.getSecretRef().getName()).isEqualTo(secret1.getMetadata().getName());

		EnvFromSource envFromSource2 = envFromSources.get(1);
		assertThat(envFromSource2.getSecretRef().getName()).isEqualTo(secret2.getMetadata().getName());

		Map<String, String> mergedSecretData = new HashMap<>();
		mergedSecretData.putAll(secret1.getData());
		mergedSecretData.putAll(secret2.getData());

		assertThat(mergedSecretData).hasSize(4);

		for(Map.Entry<String, String> secretData : secret1.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(secretData.getValue()));
			assertThat(podEnvironment).contains(secretData.getKey() + "=" + decodedValue);
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.secrets().delete(secret1);
		kubernetesClient.secrets().delete(secret2);
	}

	@Test
	public void testSecretRefFromDeploymentPropertyMultiple() throws InterruptedException {
		log.info("Testing {}...", "SecretRefFromDeploymentPropertyMultiple");

		Secret secret1 = randomSecret();
		Secret secret2 = randomSecret();

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.secretRefs", "[" + secret1.getMetadata().getName() + "," +
				secret2.getMetadata().getName() + "]");

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(2);

		String podEnvironment = getPodEnvironment(deploymentId);

		EnvFromSource envFromSource1 = envFromSources.get(0);
		assertThat(secret1.getMetadata().getName()).isEqualTo(envFromSource1.getSecretRef().getName());

		EnvFromSource envFromSource2 = envFromSources.get(1);
		assertThat(secret2.getMetadata().getName()).isEqualTo(envFromSource2.getSecretRef().getName());

		Map<String, String> mergedSecretData = new HashMap<>();
		mergedSecretData.putAll(secret1.getData());
		mergedSecretData.putAll(secret2.getData());

		assertThat(mergedSecretData).hasSize(4);

		for(Map.Entry<String, String> secretData : secret1.getData().entrySet()) {
			String decodedValue = new String(Base64.getDecoder().decode(secretData.getValue()));
			assertThat(podEnvironment).contains(secretData.getKey() + "=" + decodedValue);
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.secrets().delete(secret1);
		kubernetesClient.secrets().delete(secret2);
	}

	@Test
	public void testConfigMapRef() throws InterruptedException {
		log.info("Testing {}...", "ConfigMapRef");

		ConfigMap configMap = randomConfigMap();

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setConfigMapRefs(Collections.singletonList(configMap.getMetadata().getName()));

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties, kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(envFromSource.getConfigMapRef().getName()).isEqualTo(configMap.getMetadata().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		assertThat(configMap.getData()).hasSize(2);

		for(Map.Entry<String, String> configMapData : configMap.getData().entrySet()) {
			assertThat(podEnvironment).contains(configMapData.getKey() + "=" + configMapData.getValue());
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.configMaps().delete(configMap);
	}

	@Test
	public void testConfigMapRefFromDeployerProperty() throws InterruptedException {
		log.info("Testing {}...", "ConfigMapRefFromDeployerProperty");

		ConfigMap configMap = randomConfigMap();

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.config-map-refs", configMap.getMetadata().getName());

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(envFromSource.getConfigMapRef().getName()).isEqualTo(configMap.getMetadata().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		assertThat(configMap.getData()).hasSize(2);

		for(Map.Entry<String, String> configMapData : configMap.getData().entrySet()) {
			assertThat(podEnvironment).contains(configMapData.getKey() + "=" + configMapData.getValue());
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.configMaps().delete(configMap);
	}

	@Test
	public void testConfigMapRefFromDeployerPropertyOverride() throws IOException, InterruptedException {
		log.info("Testing {}...", "ConfigMapRefFromDeployerPropertyOverride");

		ConfigMap propertyConfigMap = randomConfigMap();
		ConfigMap deployerPropertyConfigMap = randomConfigMap();

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setConfigMapRefs(Collections.singletonList(propertyConfigMap.getMetadata().getName()));

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.configMapRefs", deployerPropertyConfigMap.getMetadata().getName());

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(1);

		EnvFromSource envFromSource = envFromSources.get(0);
		assertThat(deployerPropertyConfigMap.getMetadata().getName()).isEqualTo(envFromSource.getConfigMapRef().getName());

		String podEnvironment = getPodEnvironment(deploymentId);

		for(Map.Entry<String, String> deployerPropertyConfigMapData : deployerPropertyConfigMap.getData().entrySet()) {
			assertThat(podEnvironment)
					.contains(deployerPropertyConfigMapData.getKey() + "=" + deployerPropertyConfigMapData.getValue());
		}

		for(Map.Entry<String, String> propertyConfigMapData : propertyConfigMap.getData().entrySet()) {
			assertThat(podEnvironment).doesNotContain(propertyConfigMapData.getKey() + "=" + propertyConfigMapData.getValue());
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.configMaps().delete(propertyConfigMap);
		kubernetesClient.configMaps().delete(deployerPropertyConfigMap);
	}

	@Test
	public void testConfigMapRefFromPropertyMultiple() throws InterruptedException {
		log.info("Testing {}...", "ConfigMapRefFromPropertyMultiple");

		ConfigMap configMap1 = randomConfigMap();
		ConfigMap configMap2 = randomConfigMap();

		List<String> configMaps = new ArrayList<>();
		configMaps.add(configMap1.getMetadata().getName());
		configMaps.add(configMap2.getMetadata().getName());

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setConfigMapRefs(configMaps);

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(kubernetesDeployerProperties,
				kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, null);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).hasSize(2);

		String podEnvironment = getPodEnvironment(deploymentId);

		EnvFromSource envFromSource1 = envFromSources.get(0);
		assertThat(configMap1.getMetadata().getName()).isEqualTo(envFromSource1.getConfigMapRef().getName());

		EnvFromSource envFromSource2 = envFromSources.get(1);
		assertThat(configMap2.getMetadata().getName()).isEqualTo(envFromSource2.getConfigMapRef().getName());

		Map<String, String> mergedConfigMapData = new HashMap<>();
		mergedConfigMapData.putAll(configMap1.getData());
		mergedConfigMapData.putAll(configMap2.getData());

		assertThat(mergedConfigMapData).hasSize(4);

		for(Map.Entry<String, String> configMapData : configMap1.getData().entrySet()) {
			assertThat(podEnvironment).contains(configMapData.getKey() + "=" + configMapData.getValue());
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.configMaps().delete(configMap1);
		kubernetesClient.configMaps().delete(configMap2);
	}

	@Test
	public void testConfigMapRefFromDeploymentPropertyMultiple() throws InterruptedException {
		log.info("Testing {}...", "ConfigMapRefFromDeploymentPropertyMultiple");

		ConfigMap configMap1 = randomConfigMap();
		ConfigMap configMap2 = randomConfigMap();

		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.configMapRefs", "[" + configMap1.getMetadata().getName() + "," +
				configMap2.getMetadata().getName() + "]");

		KubernetesAppDeployer kubernetesAppDeployer = new KubernetesAppDeployer(new KubernetesDeployerProperties(),
				kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = kubernetesAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);
		});

		Container container = kubernetesClient.apps().deployments().withName(deploymentId).get().getSpec()
				.getTemplate().getSpec().getContainers().get(0);

		List<EnvFromSource> envFromSources = container.getEnvFrom();

		assertThat(envFromSources).isNotNull();
		assertThat(envFromSources).hasSize(2);

		String podEnvironment = getPodEnvironment(deploymentId);

		EnvFromSource envFromSource1 = envFromSources.get(0);
		assertThat(envFromSource1.getConfigMapRef().getName()).isEqualTo(configMap1.getMetadata().getName());

		EnvFromSource envFromSource2 = envFromSources.get(1);
		assertThat(envFromSource2.getConfigMapRef().getName()).isEqualTo(configMap2.getMetadata().getName());

		Map<String, String> mergedConfigMapData = new HashMap<>();
		mergedConfigMapData.putAll(configMap1.getData());
		mergedConfigMapData.putAll(configMap2.getData());

		assertThat(mergedConfigMapData).hasSize(4);

		for(Map.Entry<String, String> configMapData : configMap1.getData().entrySet()) {
			assertThat(podEnvironment).contains(configMapData.getKey() + "=" + configMapData.getValue());
		}

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		kubernetesAppDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
				.untilAsserted(() -> {
			assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown);
		});

		kubernetesClient.configMaps().delete(configMap1);
		kubernetesClient.configMaps().delete(configMap2);
	}

	@Override
	protected String randomName() {
		// Kubernetest service names must start with a letter and can only be 24 characters long
		return "app-" + UUID.randomUUID().toString().substring(0, 18);
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(300, 2000);
	}

	@Override
	protected Resource testApplication() {
		return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
	}

	private String getPodEnvironment(String deploymentId) throws InterruptedException {
		String podName = kubernetesClient.pods().withLabel("spring-deployment-id", deploymentId).list().getItems()
				.get(0).getMetadata().getName();

		final CountDownLatch countDownLatch = new CountDownLatch(1);
		ByteArrayOutputStream execOutputStream = new ByteArrayOutputStream();

		ExecWatch watch = kubernetesClient.pods().withName(podName).inContainer(deploymentId)
				.writingOutput(execOutputStream)
				.usingListener(new ExecListener() {
					@Override
					public void onOpen(Response response) {
					}

					@Override
					public void onFailure(Throwable throwable, Response response) {
						countDownLatch.countDown();
					}

					@Override
					public void onClose(int code, String reason) {
						countDownLatch.countDown();
					}
				}).exec("printenv");

		countDownLatch.await();

		byte[] bytes = execOutputStream.toByteArray();

		watch.close();

		return new String(bytes);
	}

	// Creates a Secret with a name will be generated prefixed by "secret-" followed by random numbers.
	//
	// Two data keys are present, both prefixed by "d-" followed by random numbers. This allows for cases where
	// multiple Secrets may be read into environment variables avoiding variable name clashes.
	//
	// Data values are Base64 encoded strings of value1 and value2
	private Secret randomSecret() {
		Map<String, String> secretData = new HashMap<>();
		secretData.put("d-" + UUID.randomUUID().toString().substring(0, 5), "dmFsdWUx");
		secretData.put("d-" + UUID.randomUUID().toString().substring(0, 5), "dmFsdWUy");

		Secret secret = new Secret();
		secret.setData(secretData);

		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("secret-" + UUID.randomUUID().toString().substring(0, 5));

		secret.setMetadata(objectMeta);

		return kubernetesClient.secrets().create(secret);
	}

	// Creates a ConfigMap with a name will be generated prefixed by "cm-" followed by random numbers.
	//
	// Two data keys are present, both prefixed by "d-" followed by random numbers. This allows for cases where
	// multiple ConfigMaps may be read into environment variables avoiding variable name clashes.
	//
	// Data values are strings of value1 and value
	private ConfigMap randomConfigMap() {
		Map<String, String> configMapData = new HashMap<>();
		configMapData.put("d-" + UUID.randomUUID().toString().substring(0, 5), "value1");
		configMapData.put("d-" + UUID.randomUUID().toString().substring(0, 5), "value2");

		ConfigMap configMap = new ConfigMap();
		configMap.setData(configMapData);

		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("cm-" + UUID.randomUUID().toString().substring(0, 5));

		configMap.setMetadata(objectMeta);

		return kubernetesClient.configMaps().create(configMap);
	}
}
