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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Integration tests for {@link KubernetesTaskLauncher} using jobs instead of bare pods.
 *
 * <p>NOTE: The tests do not call {@code TaskLauncher.destroy/cleanup} in a finally block but instead rely on the
 * {@link AbstractKubernetesTaskLauncherIntegrationTests#cleanupLingeringApps() AfterEach method} to clean any stray apps.
 *
 * @author Leonardo Diniz
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Chris Bono
 * @author Glenn Renfro
 */
@SpringBootTest(classes = {KubernetesAutoConfiguration.class})
@TestPropertySource(properties = "spring.cloud.deployer.kubernetes.create-job=true")
@ExtendWith(OutputCaptureExtension.class)
public class KubernetesTaskLauncherWithJobIntegrationIT extends AbstractKubernetesTaskLauncherIntegrationTests {

	@BeforeEach
	public void setup() {
		if (kubernetesClient.getNamespace() == null) {
			kubernetesClient.getConfiguration().setNamespace("default");
		}
	}

	@Test
	void taskLaunchedWithJobAnnotations(TestInfo testInfo) {
		logTestInfo(testInfo);
		launchTaskJobAndValidateCreatedJobAndPodWithCleanup(
				Collections.singletonMap("spring.cloud.deployer.kubernetes.jobAnnotations", "key1:val1,key2:val2,key3:val31:val32"),
				(job) -> assertThat(job.getMetadata().getAnnotations()).isNotEmpty()
						.contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32")),
				(pod) -> assertThat(pod.getMetadata().getAnnotations()).isNotEmpty()
						.contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32")));
	}

	@Test
	void taskLaunchedWithJobSpecProperties(TestInfo testInfo) {
		logTestInfo(testInfo);
		Map<String, String> deploymentProps = new HashMap<>();
		deploymentProps.put("spring.cloud.deployer.kubernetes.restartPolicy", "OnFailure");
		deploymentProps.put("spring.cloud.deployer.kubernetes.backoffLimit", "5");
		launchTaskJobAndValidateCreatedJobAndPodWithCleanup(
				deploymentProps,
				(job) -> assertThat(job.getSpec().getBackoffLimit()).isEqualTo(5),
				(pod) -> {});
	}

	private void launchTaskJobAndValidateCreatedJobAndPodWithCleanup(Map<String, String> deploymentProps,
			Consumer<Job> assertingJobConsumer, Consumer<Pod> assertingPodConsumer) {
		String taskName = randomName();
		AppDefinition definition = new AppDefinition(taskName, null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProps);

		log.info("Launching {}...", taskName);
		String launchId = taskLauncher().launch(request);
		awaitWithPollAndTimeout(deploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.launching));

		log.info("Checking task Job for {}...", taskName);
		List<Job> jobs = getJobsForTask(taskName);
		assertThat(jobs).hasSize(1);
		assertThat(jobs).singleElement().satisfies(assertingJobConsumer);

		log.info("Checking task Pod for {}...", taskName);
		List<Pod> pods = getPodsForTask(taskName);
		assertThat(pods).hasSize(1);
		assertThat(pods).singleElement().satisfies(assertingPodConsumer);

		log.info("Destroying {}...", taskName);
		taskLauncher().destroy(taskName);
		awaitWithPollAndTimeout(undeploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown));
	}

	@Test
	void taskLaunchedWithInvalidRestartPolicyThrowsException(TestInfo testInfo) {
		logTestInfo(testInfo);
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		Map<String, String> deploymentProps = new HashMap<>();
		deploymentProps.put("spring.cloud.deployer.kubernetes.restartPolicy", "Always");
		deploymentProps.put("spring.cloud.deployer.kubernetes.backoffLimit", "5");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProps);

		log.info("Launching {}...", request.getDefinition().getName());
		assertThatThrownBy(() -> taskLauncher.launch(request))
				.isInstanceOf(Exception.class)
				.hasMessage("RestartPolicy should not be 'Always' when the JobSpec is used.");
	}

	@Test
	void cleanupDeletesTaskJob(TestInfo testInfo) {
		logTestInfo(testInfo);
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, null);
		String taskName = request.getDefinition().getName();

		log.info("Launching {}...", taskName);
		String launchId = taskLauncher().launch(request);
		awaitWithPollAndTimeout(deploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.launching));

		List<Job> jobs = getJobsForTask(taskName);
		assertThat(jobs).hasSize(1);

		log.info("Cleaning up {}...", taskName);
		taskLauncher().cleanup(launchId);
		awaitWithPollAndTimeout(undeploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown));

		jobs = getJobsForTask(taskName);
		assertThat(jobs).isEmpty();
	}

	@Test
	void cleanupForNonExistentTaskThrowsException(TestInfo testInfo, CapturedOutput taskOutput) {
		logTestInfo(testInfo);
		taskLauncher().cleanup("foo");
		assertThat(taskOutput.getAll()).contains("Cannot delete job for task \"foo\" (reason: job does not exist)");
	}

	@Test
	void deleteJobAfterTtlSecondsOnAfterFinishedExpire(TestInfo testInfo) {
		logTestInfo(testInfo);

		Map<String, String> applicationProps = new HashMap<>();
		applicationProps.put("killDelay", "1");
		AppDefinition definition = new AppDefinition(randomName(), applicationProps);

		Resource resource = testApplication();

		Map<String, String> deploymentProps = new HashMap<>();
		deploymentProps.put("spring.cloud.deployer.kubernetes.restartPolicy", "Never");
		deploymentProps.put("spring.cloud.deployer.kubernetes.backoffLimit", "0");
		deploymentProps.put("spring.cloud.deployer.kubernetes.ttlSecondsAfterFinished", "3");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProps);
		String taskName = request.getDefinition().getName();

		log.info("Launching {}...", taskName);
		String launchId = taskLauncher().launch(request);
		awaitWithPollAndTimeout(deploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.launching));

		List<Job> jobs = getJobsForTask(taskName);
		assertThat(jobs).hasSize(1);

		log.info("Waiting for deleting the job {}...", taskName);

		awaitWithPollAndTimeout(undeploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown));

		jobs = getJobsForTask(taskName);
		assertThat(jobs).isEmpty();
	}
}
