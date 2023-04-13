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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.utils.PodStatusUtil;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Integration tests for {@link KubernetesTaskLauncher}.
 *
 * <p>NOTE: The tests do not call {@code TaskLauncher.destroy/cleanup} in a finally block but instead rely on the
 * {@link AbstractKubernetesTaskLauncherIntegrationTests#cleanupLingeringApps() AfterEach method} to clean any stray apps.
 *
 * @author Thomas Risberg
 * @author Chris Schaefer
 * @author Chris Bono
 * @author Glenn Renfro
 */
@SpringBootTest(classes = {KubernetesAutoConfiguration.class}, properties = {
		"spring.cloud.deployer.kubernetes.namespace=default"
})
@ExtendWith(OutputCaptureExtension.class)
public class KubernetesTaskLauncherIntegrationIT extends AbstractKubernetesTaskLauncherIntegrationTests {

	@Test
	void taskLaunchedWithJobPodAnnotations(TestInfo testInfo) {
		logTestInfo(testInfo);
		launchTaskPodAndValidateCreatedPodWithCleanup(
				Collections.singletonMap("spring.cloud.deployer.kubernetes.jobAnnotations", "key1:val1,key2:val2,key3:val31:val32"),
				(pod) -> {
					assertThat(pod.getSpec().getContainers()).isNotEmpty()
							.element(0).extracting(Container::getPorts).asList().isEmpty();
					assertThat(pod.getMetadata().getAnnotations()).isNotEmpty()
							.contains(entry("key1", "val1"), entry("key2", "val2"), entry("key3", "val31:val32"));
				});
	}

	@Test
	void taskLaunchedWithDeploymentLabels(TestInfo testInfo) {
		logTestInfo(testInfo);
		launchTaskPodAndValidateCreatedPodWithCleanup(
				Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels", "label1:value1,label2:value2"),
				(pod) -> {
					assertThat(pod.getSpec().getContainers()).isNotEmpty()
							.element(0).extracting(Container::getPorts).asList().isEmpty();
					assertThat(pod.getMetadata().getLabels()).isNotEmpty()
							.contains(entry("label1", "value1"), entry("label2", "value2"));
				});
	}

	@Test
	void tasksLaunchedWithAdditionalContainers(TestInfo testInfo) {
		logTestInfo(testInfo);
		launchTaskPodAndValidateCreatedPodWithCleanup(
				Collections.singletonMap("spring.cloud.deployer.kubernetes.additionalContainers",
						"[{name: 'test', image: 'busybox:latest', command: ['sh', '-c', 'echo hello']}]"),
				(pod) -> assertThat(pod.getSpec().getContainers()).hasSize(2)
						.filteredOn("name", "test").singleElement()
						.hasFieldOrPropertyWithValue("image", "busybox:latest")
						.hasFieldOrPropertyWithValue("command", Arrays.asList("sh", "-c", "echo hello")));
	}

	private void launchTaskPodAndValidateCreatedPodWithCleanup(Map<String, String> deploymentProps, Consumer<Pod> assertingConsumer) {
		String taskName = randomName();
		AppDefinition definition = new AppDefinition(taskName, null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProps);

		log.info("Launching {}...", taskName);
		String launchId = taskLauncher().launch(request);
		awaitWithPollAndTimeout(deploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running));

		log.info("Checking task Pod for {}...", taskName);
		List<Pod> pods = getPodsForTask(taskName);
		assertThat(pods).hasSize(1);
		assertThat(pods).singleElement().satisfies(assertingConsumer);

		log.info("Destroying {}...", taskName);
		taskLauncher().destroy(taskName);
		awaitWithPollAndTimeout(undeploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown));
	}

	@Test
	void cleanupDeletesTaskPod(TestInfo testInfo) {
		logTestInfo(testInfo);
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, null);
		String taskName = request.getDefinition().getName();

		log.info("Launching {}...", taskName);
		String launchId = taskLauncher().launch(request);
		awaitWithPollAndTimeout(deploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running));

		List<Pod> pods = getPodsForTask(taskName);
		assertThat(pods).hasSize(1);
		assertThat(PodStatusUtil.isRunning(pods.get(0))).isTrue();

		log.info("Cleaning up {}...", taskName);
		taskLauncher().cleanup(launchId);
		awaitWithPollAndTimeout(undeploymentTimeout())
				.untilAsserted(() -> assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.unknown));

		pods = getPodsForTask(taskName);
		assertThat(pods).isEmpty();
	}

	@Test
	void cleanupForNonExistentTaskThrowsException(TestInfo testInfo, CapturedOutput taskOutput) {
		logTestInfo(testInfo);
		taskLauncher().cleanup("foo");
		assertThat(taskOutput.getAll()).contains("Cannot delete pod for task \"foo\" (reason: pod does not exist)");
	}
}
