/*
 * Copyright 2016 the original author or authors.
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deploying;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.failed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Abstract base class for integration tests of
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer}
 * implementations.
 * <p>
 * Inheritors should setup an environment with a newly created
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer} that has no
 * pre-deployed applications. Tests in this class are independent and leave the
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
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractAppDeployerIntegrationTests {

	private static final Logger log = LoggerFactory.getLogger(AbstractAppDeployerIntegrationTests.class);

	protected abstract AppDeployer appDeployer();

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
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying " + request.getDefinition().getName() + "...");

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Deploying " + request.getDefinition().getName() + " again...");

		try {
			appDeployer().deploy(request);
			fail("Should have thrown an IllegalStateException");
		}
		catch (IllegalStateException ok) {
		}

		log.info("Undeploying " + deploymentId + "...");

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	/**
	 * An app deployer should be able to re-deploy an application after it has been un-deployed.
	 * This test makes sure the deployer does not leave things lying around for example.
	 */
	@Test
	public void testRedeploy() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying " + request.getDefinition().getName() + "...");

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying " + deploymentId + "...");

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		log.info("Deploying " + request.getDefinition().getName() + " again...");

		// Attempt re-deploy of SAME request
		deploymentId = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying " + deploymentId + "...");

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
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		log.info("Deploying " + request.getDefinition().getName() + "...");

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deploying))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying " + deploymentId + "...");

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
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		log.info("Deploying " + request.getDefinition().getName() + "...");

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(failed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying " + deploymentId + "...");

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	/**
	 * Tests that properties can be passed to a deployed app, including values that typically require special handling.
	 */
	@Test
	public void testParameterPassing() {
		Map<String, String> properties = new HashMap<>();
		properties.put("parameterThatMayNeedEscaping", "&'\"|< é\\(");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, integrationTestProcessor());

		log.info("Deploying " + request.getDefinition().getName() + "...");

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying " + deploymentId + "...");

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	protected String randomName() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	protected Timeout deploymentTimeout() {
		return new Timeout(12, 5000);
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being un-deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	protected Timeout undeploymentTimeout() {
		return new Timeout(20, 5000);
	}

	/**
	 * Return a resource corresponding to the integration-test-processor suitable for the target runtime.
	 *
	 * The default implementation returns an uber-jar fetched via Maven. Subclasses may override.
	 */
	protected Resource integrationTestProcessor() {
		return new MavenResource.Builder()
				.groupId("org.springframework.cloud.stream.module")
				.artifactId("integration-test-processor")
				.version("1.0.0.BUILD-SNAPSHOT")
				.extension("jar")
				.classifier("exec")
				.build();
	}

	/**
	 * Represents a timeout for querying status, with repetitive queries until a certain number have been made.
	 *
	 * @author Eric Bottard
	 */
	protected static class Timeout {

		public final int maxAttempts;

		public final int pause;

		public Timeout(int maxAttempts, int pause) {
			this.maxAttempts = maxAttempts;
			this.pause = pause;
		}
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

}

