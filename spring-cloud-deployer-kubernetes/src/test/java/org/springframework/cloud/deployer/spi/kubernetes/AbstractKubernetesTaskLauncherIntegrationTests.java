/*
 * Copyright 2021-2021 the original author or authors.
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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationJUnit5Tests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.io.Resource;

import static org.awaitility.Awaitility.await;

/**
 * Abstract base class for integration tests for {@link KubernetesTaskLauncher}.
 *
 * @author Chris Bono
 */
abstract class AbstractKubernetesTaskLauncherIntegrationTests extends AbstractTaskLauncherIntegrationJUnit5Tests {

	@Autowired
	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	protected TaskLauncher taskLauncher;

	@Autowired
	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	protected KubernetesClient kubernetesClient;

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

	protected void logTestInfo(TestInfo testInfo) {
		log.info("Testing {}...", testInfo.getTestMethod().map(Method::getName).orElse(testInfo.getDisplayName()));
	}

	protected ConditionFactory awaitWithPollAndTimeout(Timeout timeout) {
		return await().pollInterval(Duration.ofMillis(timeout.pause))
				.atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause));
	}

	protected List<Pod> getPodsForTask(String taskName) {
		return kubernetesClient.pods().withLabel("task-name", taskName).list().getItems();
	}

	protected List<Job> getJobsForTask(String taskName) {
		return kubernetesClient.batch().v1().jobs().withLabel("task-name", taskName).list().getItems();
	}
}
