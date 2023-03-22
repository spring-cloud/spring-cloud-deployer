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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link KubernetesTaskLauncher} using jobs instead of bare pods.
 *
 * @author Leonardo Diniz
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest(classes = {KubernetesAutoConfiguration.class})
@TestPropertySource(properties = "spring.cloud.deployer.kubernetes.create-job=true")
public class KubernetesTaskLauncherWithJobIntegrationIT extends AbstractTaskLauncherIntegrationJUnit5Tests {

	@Autowired
	private TaskLauncher taskLauncher;

	@Autowired
	private KubernetesClient kubernetesClient;

	@BeforeEach
	public void setup() {
		if (kubernetesClient.getNamespace() == null) {
			kubernetesClient.getConfiguration().setNamespace("default");
		}
	}

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
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
	@Override
	@Disabled("Currently reported as failed instead of cancelled")
	public void testSimpleCancel() throws InterruptedException {
		super.testSimpleCancel();
	}

	@Test
	public void testJobAnnotations() {
		log.info("Testing {}...", "JobAnnotations");

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setCreateJob(true);

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(kubernetesDeployerProperties,
				new KubernetesTaskLauncherProperties(), kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.kubernetes.jobAnnotations",
						"key1:val1,key2:val2,key3:val31:val32"));

		log.info("Launching {}...", request.getDefinition().getName());

		String launchId = kubernetesTaskLauncher.launch(request);
		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.launching);
        });

		String taskName = request.getDefinition().getName();

		log.info("Checking Job spec annotations of {}...", taskName);

		List<Job> jobs = kubernetesClient.batch().jobs().withLabel("task-name", taskName).list().getItems();

		assertThat(jobs).hasSize(1);

		Map<String, String> jobAnnotations = jobs.get(0).getMetadata().getAnnotations();
		assertThat(jobAnnotations).contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32"));

		log.info("Checking Pod spec annotations of {}...", taskName);

		List<Pod> pods = kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();

		assertThat(pods).hasSize(1);

		Map<String, String> podAnnotations = pods.get(0).getMetadata().getAnnotations();
		assertThat(podAnnotations).contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32"));

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
	public void testJobSpecProperties() {
		log.info("Testing {}...", "JobSpecProperties");

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setCreateJob(true);

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(kubernetesDeployerProperties,
				new KubernetesTaskLauncherProperties(), this.kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		Map<String, String> appRequest = new HashMap<>();
		appRequest.put("spring.cloud.deployer.kubernetes.restartPolicy", "OnFailure");
		appRequest.put("spring.cloud.deployer.kubernetes.backoffLimit", "5");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, appRequest);

		log.info("Launching {}...", request.getDefinition().getName());

		String launchId = kubernetesTaskLauncher.launch(request);
		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.launching);
        });

		String taskName = request.getDefinition().getName();

		log.info("Checking job spec of {}...", taskName);

		List<Job> jobs = kubernetesClient.batch().jobs().withLabel("task-name", taskName).list().getItems();

		assertThat(jobs).hasSize(1);

		Job job = jobs.get(0);
		assertThat(job.getSpec().getBackoffLimit()).isEqualTo(5);

		log.info("Checking pod spec of {}...", taskName);

		List<Pod> pods = kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();

		assertThat(pods).hasSize(1);

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
	public void testJobSpecWithInvalidRestartPolicy() {
		log.info("Testing {}...", "JobSpecWithInvalidRestartPolicy");

		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setCreateJob(true);

		KubernetesTaskLauncher kubernetesTaskLauncher = new KubernetesTaskLauncher(kubernetesDeployerProperties,
				new KubernetesTaskLauncherProperties(), this.kubernetesClient);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		Map<String, String> appRequest = new HashMap<>();
		appRequest.put("spring.cloud.deployer.kubernetes.restartPolicy", "Always");
		appRequest.put("spring.cloud.deployer.kubernetes.backoffLimit", "5");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, appRequest);

		log.info("Launching {}...", request.getDefinition().getName());

		assertThatThrownBy(() -> {
			kubernetesTaskLauncher.launch(request);
		}).isInstanceOf(Exception.class)
			.hasMessage("RestartPolicy should not be 'Always' when the JobSpec is used.");
	}
}
