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

package org.springframework.cloud.deployer.spi.test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.test.app.DeployerIntegrationTestProperties;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
 * @author David Turanski
 * @author Corneil du Plessis
 */
public abstract class AbstractAppDeployerIntegrationJUnit5Tests extends AbstractIntegrationJUnit5Tests {
	final static int DESIRED_COUNT = 3;
	private AppDeployerWrapper deployerWrapper;

	/**
	 * To be implemented by subclasses, which should return the instance of AppDeployer that needs
	 * to be tested. If subclasses decide to add additional implementation-specific tests, they should
	 * interact with the deployer through {@link #appDeployer()}, and not directly via a field or a call
	 * to this method.
	 * @return the app deployer
	 */
	protected abstract AppDeployer provideAppDeployer();

	/**
	 * Subclasses should call this method to interact with the AppDeployer under test.
	 * Returns a wrapper around the deployer returned by {@link #provideAppDeployer()}, that keeps
	 * track of which apps have been deployed and undeployed.
	 * @return the app deployer
	 */
	protected AppDeployer appDeployer() {
		return deployerWrapper;
	}

	@BeforeEach
	public void wrapDeployer() {
		deployerWrapper = new AppDeployerWrapper(provideAppDeployer());
	}

	@AfterEach
	public void cleanupLingeringApps() {
		for (String id : deployerWrapper.deployments) {
			try {
				log.warn("Test named {} left behind an app for deploymentId '{}', trying to cleanup", this.testName, id);
				deployerWrapper.wrapped.undeploy(id);
			} catch (Exception e) {
				log.warn("Exception caught while trying to cleanup '{}'. Moving on...", id);
			}
		}
	}

	@Test
	public void testUnknownDeployment() {
		String unknownId = randomName();
		AppStatus status = appDeployer().status(unknownId);

		assertThat(status.getDeploymentId()).isEqualTo(unknownId);
		assertThat(status.getInstances()).as("The map was not empty: " + status.getInstances()).isEmpty();
		assertThat(status.getState()).isEqualTo(DeploymentState.unknown);
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

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await()
			.pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deployed)
			);


		log.info("Deploying {} again...", request.getDefinition().getName());

		assertThatThrownBy(() ->
			appDeployer.deploy(request)
		).isInstanceOf(IllegalStateException.class);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);

		assertThatThrownBy(() ->
			appDeployer.undeploy(deploymentId)
		).isInstanceOf(IllegalStateException.class);
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

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await()
			.pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deployed)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await()
			.pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);

		// Optionally pause before re-using request
		try {
			Thread.sleep(redeploymentPause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		log.info("Deploying {} again...", request.getDefinition().getName());

		// Attempt re-deploy of SAME request
		String deploymentId2 = appDeployer.deploy(request);
		timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId2).getState()).isEqualTo(DeploymentState.deployed)
			);

		log.info("Undeploying {}...", deploymentId2);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId2);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId2).getState()).isEqualTo(DeploymentState.unknown)
			);
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

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deploying)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);
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
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.failed)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);
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
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.deployed)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);


		// This second pass makes sure that properties are indeed passed

		properties.put("parameterThatMayNeedEscaping", "notWhatIsExpected");
		definition = new AppDefinition(randomName(), properties);

		request = new AppDeploymentRequest(definition, testApplication(), deploymentProperties);

		log.info("Deploying {}, expecting it to fail...", request.getDefinition().getName());

		String deploymentId2 = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId2).getState()).isEqualTo(DeploymentState.failed)
			);

		log.info("Undeploying {}...", deploymentId2);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId2);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer().status(deploymentId2).getState()).isEqualTo(DeploymentState.unknown)
			);
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

		List<String> cmdLineArgs = Collections.singletonList("--commandLineArgValueThatMayNeedEscaping=" + DeployerIntegrationTestProperties.FUNNY_CHARACTERS);
		AppDeploymentRequest request =
			new AppDeploymentRequest(definition, testApplication(), deploymentProperties, cmdLineArgs);

		log.info("Deploying {}...", request.getDefinition().getName());

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deployed)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);

		// This second pass makes sure that commandLine args are indeed understood
		properties = new HashMap<>();
		definition = new AppDefinition(randomName(), properties);
		deploymentProperties = new HashMap<>();

		cmdLineArgs = Collections.singletonList("--commandLineArgValueThatMayNeedEscaping=notWhatIsExpected");
		request = new AppDeploymentRequest(definition, testApplication(), deploymentProperties, cmdLineArgs);

		log.info("Deploying {}, expecting it to fail...", request.getDefinition().getName());

		String deploymentId2 = appDeployer.deploy(request);
		timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId2).getState()).isEqualTo(DeploymentState.failed)
			);

		log.info("Undeploying {}...", deploymentId2);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId2);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId2).getState()).isEqualTo(DeploymentState.unknown)
			);
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

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.partial)
			);

		// Assert individual instance state
		// Note we can't rely on instances order, neither on their id indicating their ordinal number
		List<DeploymentState> individualStates = new ArrayList<>();
		for (AppInstanceStatus status : appDeployer.status(deploymentId).getInstances().values()) {
			individualStates.add(status.getState());
		}

		assertThat(individualStates).containsExactlyInAnyOrder(DeploymentState.deployed, DeploymentState.deployed,
			DeploymentState.failed);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.unknown)
			);
	}

	/**
	 * Tests support for DeployerEnvironmentInfo is implemented.
	 */
	@Test
	public void testEnvironmentInfo() {
		RuntimeEnvironmentInfo info = appDeployer().environmentInfo();
		assertThat(info.getImplementationVersion()).isNotNull();
		assertThat(info.getPlatformType()).isNotNull();
		assertThat(info.getPlatformClientVersion()).isNotNull();
		assertThat(info.getPlatformHostVersion()).isNotNull();
	}

	@Test
	@Disabled("Disabled pending the implementation of this feature.")
	public void testScale() {
		doTestScale(false);
	}

	@Test
	@Disabled("Disabled pending the implementation of this feature.")
	public void testScaleWithIndex() {
		doTestScale(true);
	}

	protected void doTestScale(Boolean indexed) {


		Map<String, String> deploymentProperties =
			Collections.singletonMap(AppDeployer.INDEXED_PROPERTY_KEY, indexed.toString());

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);

		log.info("Deploying {} index={}...", request.getDefinition().getName(), indexed);

		AppDeployer appDeployer = appDeployer();
		String deploymentId = appDeployer.deploy(request);

		Timeout timeout = deploymentTimeout();
		log.info("DeploymentTimeout:{}", timeout);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deployed)
			);

		assertThat(appDeployer.status(deploymentId).getState()).isEqualTo(DeploymentState.deployed);

		log.info("Scaling {} to {} instances...", request.getDefinition().getName(), DESIRED_COUNT);

		appDeployer.scale(new AppScaleRequest(deploymentId, DESIRED_COUNT));

		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() -> {
				DeploymentState state = appDeployer.status(deploymentId).getState();
				log.info("Awaiting deployed. State={}", state);
				assertThat(state).isEqualTo(DeploymentState.deployed);
			});

		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() -> {
				Map<String, AppInstanceStatus> instances = appDeployer.status(deploymentId).getInstances();
				log.info("Awaiting {} instances. Instances={}", DESIRED_COUNT, instances.size());
				assertThat(instances).hasSize(DESIRED_COUNT);
			});

		List<DeploymentState> individualStates = new ArrayList<>();
		for (AppInstanceStatus status : appDeployer.status(deploymentId).getInstances().values()) {
			individualStates.add(status.getState());
		}

		assertThat(individualStates).allMatch(is -> is == DeploymentState.deployed);

		log.info("Scaling {} from {} to 1 instance...", request.getDefinition().getName(), DESIRED_COUNT);

		appDeployer.scale(new AppScaleRequest(deploymentId, 1));

		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() -> {
				DeploymentState state = appDeployer.status(deploymentId).getState();
				log.info("Awaiting deployed. State={}", state);
				assertThat(state).isEqualTo(DeploymentState.deployed);
			});

		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() ->
				assertThat(appDeployer.status(deploymentId).getInstances()).hasSize(1)
			);

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		await().pollInterval(Duration.ofMillis(timeout.pause))
			.atMost(Duration.ofMillis(timeout.totalTime))
			.untilAsserted(() -> {
				DeploymentState state = appDeployer.status(deploymentId).getState();
				log.info("Awaiting unknown. State={}", state);
				assertThat(state).isEqualTo(DeploymentState.unknown);
			});

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

		@Override
		public String getLog(String id) {
			return wrapped.getLog(id);
		}

		@Override
		public void scale(AppScaleRequest appScaleRequest) {
			wrapped.scale(appScaleRequest);
		}

	}
}
