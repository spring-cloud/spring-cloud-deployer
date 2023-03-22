/*
 * Copyright 2021 the original author or authors.
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
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Turanski
 **/
@SpringBootTest(classes = { KubernetesAutoConfiguration.class }, properties = {
		"spring.cloud.deployer.kubernetes.maximum-concurrent-tasks=10" })
@ExtendWith(SpringExtension.class)
public class KubernetesTaskLauncherMaximumConcurrentTasksTests {

	@Autowired
	private TaskLauncher taskLauncher;

	@MockBean
	private KubernetesClient client;

	private List<Pod> pods;

	@Test
	public void getMaximumConcurrentTasksExceeded() {
		assertThat(taskLauncher).isNotNull();

		pods = stubForRunningPods(10);

		MixedOperation podsOperation = mock(MixedOperation.class);
		FilterWatchListDeletable filterWatchListDeletable = mock(FilterWatchListDeletable.class);
		when(podsOperation.withLabel("task-name")).thenReturn(filterWatchListDeletable);
		when(filterWatchListDeletable.list()).thenAnswer(invocation -> {
			PodList podList = new PodList();
			List<Pod> items = new ArrayList<>();
			podList.setItems(pods);
			return podList;
		});

		when(client.pods()).thenReturn(podsOperation);

		when(podsOperation.withName(anyString())).thenAnswer(invocation -> {
			Pod p = pods.stream().filter(pod -> pod.getMetadata().getName().equals(invocation.getArgument(0)))
					.findFirst().orElse(null);
			PodResource podResource = mock(PodResource.class);
			when(podResource.get()).thenReturn(p);
			return podResource;
		});

		int executionCount = taskLauncher.getRunningTaskExecutionCount();

		assertThat(executionCount).isEqualTo(10);

		assertThat(taskLauncher.getMaximumConcurrentTasks()).isEqualTo(taskLauncher.getRunningTaskExecutionCount());

		AppDefinition appDefinition = new AppDefinition("task", Collections.emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, mock(Resource.class),
				Collections.emptyMap());

		assertThatThrownBy(() -> {
			taskLauncher.launch(request);
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining(
				"Cannot launch task task. The maximum concurrent task executions is at its limit [10].");

	}

	private List<Pod> stubForRunningPods(int numTasks) {
		List<Pod> items = new ArrayList<>();
		for (int i = 0; i < numTasks; i++) {
			items.add(new PodBuilder().withNewMetadata()
					.withName("task-" + i).endMetadata()
					.withNewStatus()
					.withPhase("Running")
					.endStatus().build());
		}
		return items;
	}
}
