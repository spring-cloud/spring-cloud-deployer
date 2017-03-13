/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deploying;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.failed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.partial;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.test.app.DeployerIntegrationTestProperties;
import org.springframework.core.io.Resource;

/**
 * Abstract base class for integration tests of
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer}
 * implementations.
 * <p>
 * Inheritors should setup an environment with a newly created
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer}.
 * Tests in this class are independent and leave the
 * deployer in a clean state after they successfully run.
 * </p>
 * <p>
 * As deploying an application is often quite time consuming, some tests assert
 * various aspects of deployment in a row, to avoid re-deploying apps over and
 * over again.
 * </p>
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Greg Turnquist
 */
public abstract class AbstractAppDeployerIntegrationTests extends AbstractIntegrationTests {

	private AppDeployerWrapper deployerWrapper;

	/**
	 * To be implemented by subclasses, which should return the instance of AppDeployer that needs
	 * to be tested. If subclasses decide to add additional implementation-specific tests, they should
	 * interact with the deployer through {@link #appDeployer()}, and not directly via a field or a call
	 * to this method.
	 */
	protected abstract AppDeployer provideAppDeployer();

	/**
	 * Subclasses should call this method to interact with the AppDeployer under test.
	 * Returns a wrapper around the deployer returned by {@link #provideAppDeployer()}, that keeps
	 * track of which apps have been deployed and undeployed.
	 */
	protected AppDeployer appDeployer() {
		return deployerWrapper;
	}

	@Before
	public void wrapDeployer() {
		deployerWrapper = new AppDeployerWrapper(provideAppDeployer());
	}

	@After
	public void cleanupLingeringApps() {
		for (String id : deployerWrapper.deployments) {
			try {
				log.warn("Test named {} left behind an app for deploymentId '{}', trying to cleanup", name.getMethodName(), id);
				deployerWrapper.wrapped.undeploy(id);
			}
			catch (Exception e) {
				log.warn("Exception caught while trying to cleanup '{}'. Moving on...", id);
			}
		}
	}

	@Test
	public void testUnknownDeployment() {
		String unknownId = randomName();
		AppStatus status = appDeployer().status(unknownId);

		assertThat(status.getDeploymentId(), is(unknownId));
		assertThat("The map was not empty: " + status.getInstances(), status.getInstances().isEmpty(), is(true));
		assertThat(status.getState(), is(unknown));
	}

	/**
	 * Tests a simple deploy-undeploy cycle.
	 */
	@Test
	public void testSimpleDeployment() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Deploying {} again...", request.getDefinition().getName());

		try {
			appDeployer().deploy(request);
			fail("Should have thrown an IllegalStateException");
		}
		catch (IllegalStateException ok) {
		}

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
		try {
			appDeployer().undeploy(deploymentId);
			fail("Should have thrown an IllegalStateException");
		}
		catch (IllegalStateException ok) {
		}
	}

	/**
	 * An app deployer should be able to re-deploy an application after it has been un-deployed.
	 * This test makes sure the deployer does not leave things lying around for example.
	 */
	@Test
	public void testRedeploy() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		// Optionally pause before re-using request
		try {
			Thread.sleep(redeploymentPause());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		log.info("Deploying {} again...", request.getDefinition().getName());

		// Attempt re-deploy of SAME request
		deploymentId = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

	}

	/**
	 * Tests that an app which takes a long time to deploy is correctly reported as deploying.
	 * Test that such an app can be undeployed.
	 */
	@Test
	public void testDeployingStateCalculationAndCancel() {
		Map<String, String> properties = new HashMap<>();
		properties.put("initDelay", "" + 1000 * 60 * 60); // 1hr
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deploying))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

	}

	@Test
	public void testFailedDeployment() {
		Map<String, String> properties = new HashMap<>();
		properties.put("killDelay", "0");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(failed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	/**
	 * Tests that properties (key-value mappings) can be passed to a deployed app,
	 * including values that typically require special handling.
	 */
	@Test
	public void testApplicationPropertiesPassing() {
		Map<String, String> properties = new HashMap<>();
		properties.put("parameterThatMayNeedEscaping", DeployerIntegrationTestProperties.FUNNY_CHARACTERS);
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Map<String, String> deploymentProperties = new HashMap<>();
		// This makes sure that deploymentProperties are not passed to the deployed app itself
		deploymentProperties.put("killDelay", "0");

		AppDeploymentRequest request = new AppDeploymentRequest(definition, testApplication(), deploymentProperties);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		// This second pass makes sure that properties are indeed passed

		properties.put("parameterThatMayNeedEscaping", "notWhatIsExpected");
		definition = new AppDefinition(randomName(), properties);

		request = new AppDeploymentRequest(definition, testApplication(), deploymentProperties);

		log.info("Deploying {}, expecting it to fail...", request.getDefinition().getName());

		deploymentId = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(failed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	/**
	 * Tests that command line arguments (ordered strings) can be passed to a deployed app,
	 * including values that typically require special handling.
	 */
	@Test
	public void testCommandLineArgumentsPassing() {
		Map<String, String> properties = new HashMap<>();
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Map<String, String> deploymentProperties = new HashMap<>();

		List<String> cmdLineArgs = Arrays.asList("--commandLineArgValueThatMayNeedEscaping=" + DeployerIntegrationTestProperties.FUNNY_CHARACTERS);
		AppDeploymentRequest request =
				new AppDeploymentRequest(definition, testApplication(), deploymentProperties, cmdLineArgs);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		// This second pass makes sure that commandLine args are indeed understood
		properties = new HashMap<>();
		definition = new AppDefinition(randomName(), properties);
		deploymentProperties = new HashMap<>();

		cmdLineArgs = Arrays.asList("--commandLineArgValueThatMayNeedEscaping=" + "notWhatIsExpected");
		request =
				new AppDeploymentRequest(definition, testApplication(), deploymentProperties, cmdLineArgs);

		log.info("Deploying {}, expecting it to fail...", request.getDefinition().getName());

		deploymentId = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(failed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}


	/**
	 * Tests support for instance count support and individual instance status report.
	 */
	@Test
	public void testMultipleInstancesDeploymentAndPartialState() {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("matchInstances", "1"); // Only instance n°1 will kill itself
		appProperties.put("killDelay", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();

		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(AppDeployer.COUNT_PROPERTY_KEY, "3");
		deploymentProperties.put(AppDeployer.INDEXED_PROPERTY_KEY, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(partial))), timeout.maxAttempts, timeout.pause));

		// Assert individual instance state
		// Note we can't rely on instances order, neither on their id indicating their ordinal number
		List<DeploymentState> individualStates = new ArrayList<>();
		for (AppInstanceStatus status : appDeployer().status(deploymentId).getInstances().values()) {
			individualStates.add(status.getState());
		}
		assertThat(individualStates, containsInAnyOrder(
				is(deployed),
				is(deployed),
				is(failed)
		));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	/**
	 * Tests support for DeployerEnvironmentInfo is implemented.
	 */
	@Test
	public void testEnvironmentInfo() {
		RuntimeEnvironmentInfo info = appDeployer().environmentInfo();
		assertNotNull(info.getImplementationVersion());
		assertNotNull(info.getPlatformType());
		assertNotNull(info.getPlatformClientVersion());
		assertNotNull(info.getPlatformHostVersion());
	}

	/**
	 * A Hamcrest Matcher that queries the deployment status for some app id.
	 *
	 * @author Eric Bottard
	 */
	protected Matcher<String> hasStatusThat(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				status = appDeployer().status((String) item);
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
	 * A decorator for AppDeployer that keeps track of deployed/undeployed apps.
	 *
	 * @author Eric Bottard
	 */
	protected static class AppDeployerWrapper implements AppDeployer {

		private final AppDeployer wrapped;

		private final Set<String> deployments = new LinkedHashSet<>();

		public AppDeployerWrapper(AppDeployer wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public String deploy(AppDeploymentRequest request) {
			String deploymentId = wrapped.deploy(request);
			deployments.add(deploymentId);
			return deploymentId;
		}

		@Override
		public void undeploy(String id) {
			wrapped.undeploy(id);
			deployments.remove(id);
		}

		@Override
		public AppStatus status(String id) {
			return wrapped.status(id);
		}

		@Override
		public RuntimeEnvironmentInfo environmentInfo() {
			return wrapped.environmentInfo();
		}

	}


}

