/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.deployer.spi.test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Abstract base class for integration tests of
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher} implementations.
 * <p>
 * Inheritors should setup an environment with a newly created
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher}.
 *
 * Tests in this class are independent and leave the
 * launcher in a clean state after they successfully run.
 * </p>
 * <p>
 * As deploying a task is often quite time consuming, some tests assert
 * various aspects of deployment in a row, to avoid re-deploying apps over and
 * over again.
 * </p>
 *
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 */
public abstract class AbstractTaskLauncherIntegrationJUnit5Tests extends AbstractIntegrationJUnit5Tests {


	private TaskLauncherWrapper launcherWrapper;

	/**
	 * To be implemented by subclasses, which should return the instance of TaskLauncher that needs
	 * to be tested. If subclasses decide to add additional implementation-specific tests, they should
	 * interact with the task launcher through {@link #taskLauncher()}, and not directly via a field or a call
	 * to this method.
	 * @return the task launcher
	 */
	protected abstract TaskLauncher provideTaskLauncher();

	/**
	 * Subclasses should call this method to interact with the AppDeployer under test.
	 * Returns a wrapper around the deployer returned by {@link #provideTaskLauncher()}, that keeps
	 * track of which apps have been deployed and undeployed.
	 * @return the task launcher
	 */
	protected TaskLauncher taskLauncher() {
		return launcherWrapper;
	}


	@BeforeEach
	public void wrapDeployer() {
		launcherWrapper = new TaskLauncherWrapper(provideTaskLauncher());
	}

	@AfterEach
	public void cleanupLingeringApps() {
		for (String id : launcherWrapper.launchedTasks) {
			try {
				launcherWrapper.wrapped.cleanup(id);
			}
			catch (Exception e) {
				log.warn("Exception caught while trying to cleanup '{}'. Moving on...", id);
			}
		}
		for (String appName : launcherWrapper.deployedApps) {
			try {
				log.warn("Test named '{}' left behind an app for ''. Trying to destroy.", this.testName, appName);
				launcherWrapper.wrapped.destroy(appName);
			}
			catch (Exception e) {
				log.warn("Exception caught while trying to destroy '{}'. Moving on...", appName);
			}
		}
	}

	@Test
	public void testNonExistentAppsStatus() {
		assertThat(taskLauncher().status(randomName()).getState()).isEqualTo(LaunchState.unknown);
	}

	@Test
	public void testSimpleLaunch() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.complete);
        });

		taskLauncher().destroy(definition.getName());
	}

	@Test
	public void testReLaunch() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.complete);
        });

		log.info("Re-Launching {}...", request.getDefinition().getName());
		String newLaunchId = taskLauncher().launch(request);

		assertThat(newLaunchId).isNotEqualTo(launchId);

		timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(newLaunchId).getState()).isEqualTo(LaunchState.complete);
        });

		taskLauncher().destroy(definition.getName());
	}

	@Test
	public void testErrorExit() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "1");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.failed);
        });

		taskLauncher().destroy(definition.getName());
	}

	@Test
	public void testSimpleCancel() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "-1");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.running);
        });

		log.info("Cancelling {}...", request.getDefinition().getName());
		taskLauncher().cancel(launchId);

		timeout = undeploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.cancelled);
        });

		taskLauncher().destroy(definition.getName());
	}

	/**
	 * Tests that command line args can be passed in.
	 */
	@Test
	public void testCommandLineArgs() {
		Map<String, String> properties = new HashMap<>();
		properties.put("killDelay", "1000");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.<String, String>emptyMap(),
				Collections.singletonList("--exitCode=0"));
		log.info("Launching {}...", request.getDefinition().getName());
		String deploymentId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.totalTime))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(deploymentId).getState()).isEqualTo(LaunchState.complete);
        });
		taskLauncher().destroy(definition.getName());
	}

	/**
	 * Tests support for DeployerEnvironmentInfo is implemented.
	 */
	@Test
	public void testEnvironmentInfo() {
		RuntimeEnvironmentInfo info = taskLauncher().environmentInfo();
		assertThat(info.getImplementationVersion()).isNotNull();
		assertThat(info.getPlatformType()).isNotNull();
		assertThat(info.getPlatformClientVersion()).isNotNull();
		assertThat(info.getPlatformHostVersion()).isNotNull();
	}

    protected static class TaskLauncherAssert extends AbstractAssert<TaskLauncherAssert, TaskLauncher> {

		public TaskLauncherAssert(TaskLauncher launcher) {
			super(launcher, TaskLauncherAssert.class);
		}

	}

	/**
	 * A decorator for TaskLauncher that keeps track of deployed/undeployed apps.
	 *
	 * @author Eric Bottard
	 */
	protected static class TaskLauncherWrapper implements TaskLauncher {
		private final TaskLauncher wrapped;

		private final Set<String> deployedApps = new LinkedHashSet<>();

		private final Set<String> launchedTasks = new LinkedHashSet<>();

		public TaskLauncherWrapper(TaskLauncher wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public String launch(AppDeploymentRequest request) {
			String launchId = wrapped.launch(request);
			deployedApps.add(request.getDefinition().getName());
			launchedTasks.add(launchId);
			return launchId;
		}

		@Override
		public void cancel(String id) {
			wrapped.cancel(id);
		}

		@Override
		public TaskStatus status(String id) {
			return wrapped.status(id);
		}

		@Override
		public void cleanup(String id) {
			wrapped.cleanup(id);
			launchedTasks.remove(id);
		}

		@Override
		public void destroy(String appName) {
			wrapped.destroy(appName);
			deployedApps.remove(appName);
		}

		@Override
		public RuntimeEnvironmentInfo environmentInfo() {
			return wrapped.environmentInfo();
		}
		@Override
		public int getMaximumConcurrentTasks() {
			return wrapped.getMaximumConcurrentTasks();
		}
		@Override
		public int getRunningTaskExecutionCount() {
			return wrapped.getRunningTaskExecutionCount();
		}

		@Override
		public String getLog(String id) {
			return wrapped.getLog(id);
		}

	}
}
