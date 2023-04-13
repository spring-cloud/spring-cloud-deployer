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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationJUnit5Tests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link KubernetesTaskLauncher}.
 *
 * @author Thomas Risberg
 * @author Chris Schaefer
 */
@SpringBootTest(classes = {KubernetesAutoConfiguration.class}, properties = {
		"spring.cloud.deployer.kubernetes.namespace=default"
})
public class KubernetesTaskLauncherIntegrationIT extends AbstractTaskLauncherIntegrationJUnit5Tests {

	@Autowired
	private TaskLauncher taskLauncher;

	@Autowired
	private KubernetesClient kubernetesClient;

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
	}

	@Test
	@Override
	@Disabled("Currently reported as failed instead of cancelled")
	public void testSimpleCancel() throws InterruptedException {
		super.testSimpleCancel();
	}

	@Override
	protected String randomName() {
		return "task-" + UUID.randomUUID().toString().substring(0, 18);
	}

	@Override
	protected Resource testApplication() {
		return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(20, 5000);
	}

	@Test
	public void testJobPodAnnotation() {
		log.info("Testing {}...", "JobPodAnnotation");

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(new KubernetesDeployerProperties(),
				new KubernetesTaskLauncherProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.jobAnnotations", "key1:val1,key2:val2,key3:val31:val32"));

		log.info("Launching {}...", request.getDefinition().getName());

		String launchId = kubernetesTaskLauncher.launch(request);
		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running);
        });

		String taskName = request.getDefinition().getName();

		log.info("Checking job pod spec annotations of {}...", taskName);

		List<Pod> pods = kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();

		assertThat(pods).hasSize(1);

		Pod pod = pods.get(0);

		assertThat(pod.getSpec().getContainers().get(0).getPorts()).isEmpty();

		Map<String, String> annotations = pod.getMetadata().getAnnotations();

		assertThat(annotations).contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32"));

		log.info("Destroying {}...", taskName);

		timeout = undeploymentTimeout();
		kubernetesTaskLauncher.destroy(taskName);

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown);
        });
	}

	@Test
	public void testDeploymentLabels() {
		log.info("Testing {}...", "deploymentLabels");

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(new KubernetesDeployerProperties(),
				new KubernetesTaskLauncherProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels", "label1:value1,label2:value2"));

		log.info("Launching {}...", request.getDefinition().getName());

		String launchId = kubernetesTaskLauncher.launch(request);
		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running);
        });

		String taskName = request.getDefinition().getName();

		log.info("Checking job pod spec labels of {}...", taskName);

		List<Pod> pods = kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();

		assertThat(pods).hasSize(1);

		Pod pod = pods.get(0);

		assertThat(pod.getSpec().getContainers().get(0).getPorts()).isEmpty();

		Map<String, String> labels = pod.getMetadata().getLabels();

		assertThat(labels).contains(entry("label1", "value1"), entry("label2", "value2"));

		log.info("Destroying {}...", taskName);

		timeout = undeploymentTimeout();
		kubernetesTaskLauncher.destroy(taskName);

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown);
        });
	}

	@Test
	public void testTaskAdditionalContainer() {
		log.info("Testing {}...", "TaskAdditionalContainer");

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(new KubernetesDeployerProperties(),
				new KubernetesTaskLauncherProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.additionalContainers",
				"[{name: 'test', image: 'busybox:latest', command: ['sh', '-c', 'echo hello']}]");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, props);

		log.info("Launching {}...", request.getDefinition().getName());

		String launchId = kubernetesTaskLauncher.launch(request);
		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running);
        });

		String taskName = request.getDefinition().getName();

		List<Pod> pods = kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();

		assertThat(pods).hasSize(1);

		Pod pod = pods.get(0);

		assertThat(pod.getSpec().getContainers()).hasSize(2);

		Optional<Container> additionalContainer = pod.getSpec().getContainers().stream().filter(i -> i.getName().equals("test")).findFirst();
		assertThat(additionalContainer.isPresent()).as("Additional container not found").isTrue();

		Container testAdditionalContainer = additionalContainer.get();

		assertThat(testAdditionalContainer.getName()).as("Unexpected additional container name").isEqualTo("test");
		assertThat(testAdditionalContainer.getImage()).as("Unexpected additional container image").isEqualTo("busybox:latest");

		List<String> commands = testAdditionalContainer.getCommand();

		assertThat(commands).contains("sh", "-c", "echo hello");

		log.info("Destroying {}...", taskName);

		timeout = undeploymentTimeout();
		kubernetesTaskLauncher.destroy(taskName);

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown);
        });
	}
}
