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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.core.io.Resource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.task.LaunchState.complete;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

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
public abstract class AbstractTaskLauncherIntegrationTests extends AbstractIntegrationTests {


	private TaskLauncherWrapper launcherWrapper;

	/**
	 * To be implemented by subclasses, which should return the instance of TaskLauncher that needs
	 * to be tested. If subclasses decide to add additional implementation-specific tests, they should
	 * interact with the task launcher through {@link #taskLauncher()}, and not directly via a field or a call
	 * to this method.
	 */
	protected abstract TaskLauncher provideTaskLauncher();

	/**
	 * Subclasses should call this method to interact with the AppDeployer under test.
	 * Returns a wrapper around the deployer returned by {@link #provideTaskLauncher()}, that keeps
	 * track of which apps have been deployed and undeployed.
	 */
	protected TaskLauncher taskLauncher() {
		return launcherWrapper;
	}


	@Before
	public void wrapDeployer() {
		launcherWrapper = new TaskLauncherWrapper(provideTaskLauncher());
	}

	@After
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
				log.warn("Test named '{}' left behind an app for ''. Trying to destroy.", name.getMethodName(), appName);
				launcherWrapper.wrapped.destroy(appName);
			}
			catch (Exception e) {
				log.warn("Exception caught while trying to destroy '{}'. Moving on...", appName);
			}
		}
	}

	@Test
	public void testNonExistentAppsStatus() {
		assertThat(randomName(), hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", is(LaunchState.unknown))));
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
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

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
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

		log.info("Re-Launching {}...", request.getDefinition().getName());
		String newLaunchId = taskLauncher().launch(request);

		assertThat(newLaunchId, not(is(launchId)));

		timeout = deploymentTimeout();
		assertThat(newLaunchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

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
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.failed))), timeout.maxAttempts, timeout.pause));

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
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.running))), timeout.maxAttempts, timeout.pause));

		log.info("Cancelling {}...", request.getDefinition().getName());
		taskLauncher().cancel(launchId);

		timeout = undeploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.cancelled))), timeout.maxAttempts, timeout.pause));

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
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(complete))), timeout.maxAttempts, timeout.pause));
		taskLauncher().destroy(definition.getName());
	}

	/**
	 * Tests support for DeployerEnvironmentInfo is implemented.
	 */
	@Test
	public void testEnvironmentInfo() {
		RuntimeEnvironmentInfo info = taskLauncher().environmentInfo();
		assertNotNull(info.getImplementationVersion());
		assertNotNull(info.getPlatformType());
		assertNotNull(info.getPlatformClientVersion());
		assertNotNull(info.getPlatformHostVersion());
	}

	/**
	 * A Hamcrest Matcher that queries the deployment status for some task id.
	 */
	protected Matcher<String> hasStatusThat(final Matcher<TaskStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private TaskStatus status;

			@Override
			public boolean matches(Object item) {
				status = taskLauncher().status((String) item);
				return statusMatcher.matches(status);
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("status of ").appendValue(item).appendText(" ");
				statusMatcher.describeMismatch(status, mismatchDescription);
			}


			@Override
			public void describeTo(Description description) {
				statusMatcher.describeTo(description);
			}
		};
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

