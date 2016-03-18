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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
 */
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractAppDeployerIntegrationTests {

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

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		try {
			appDeployer().deploy(request);
			fail("Should have thrown an IllegalStateException");
		}
		catch (IllegalStateException ok) {
		}

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	@Test
	public void testSimpleDeployment1() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		Mono<String> deploymentIdMono = appDeployer().deployAsync1(request);
		String deploymentId = deploymentIdMono.get();
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		try {
			appDeployer().deployAsync1(request).get();
			fail("Should have thrown an IllegalStateException");
		}
		catch (IllegalStateException ok) {
		}

		timeout = undeploymentTimeout();
		appDeployer().undeployAsync1(deploymentId).get();
		assertThat(deploymentId, eventually(hasStatusThat1(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
		assertThat(deploymentId, eventually(hasStatusThat11(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
		assertThat(deploymentId, eventually(hasStatusThat111(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	@Test
	public void testSimpleDeployment2() throws InterruptedException, ExecutionException {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = integrationTestProcessor();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		CompletableFuture<String> deploymentIdCF = appDeployer().deployAsync2(request);
		String deploymentId = deploymentIdCF.get();
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		try {
			appDeployer().deployAsync2(request).get();
			fail("Should have thrown an IllegalStateException");
		}
		catch (ExecutionException ok) {
		}

		timeout = undeploymentTimeout();
		appDeployer().undeployAsync2(deploymentId).get();
		assertThat(deploymentId, eventually(hasStatusThat2(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
		assertThat(deploymentId, eventually(hasStatusThat22(
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

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		// Attempt re-deploy of SAME request
		deploymentId = appDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

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

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deploying))), timeout.maxAttempts, timeout.pause));

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

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(failed))), timeout.maxAttempts, timeout.pause));

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

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

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

	protected Matcher<String> hasStatusThat1(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				status = appDeployer().statusAsync1((String) item).get();
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

	protected Matcher<String> hasStatusThat11(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				Collection<String> ids = new ArrayList<>();
				ids.add((String) item);
				status = appDeployer().statusAsync1(ids).next().get();
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

	protected Matcher<String> hasStatusThat111(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				status = appDeployer().statusAsync1(Flux.just((String) item)).next().get();
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

	protected Matcher<String> hasStatusThat2(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				try {
					status = appDeployer().statusAsync2((String) item).get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
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

	protected Matcher<String> hasStatusThat22(final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private AppStatus status;

			@Override
			public boolean matches(Object item) {
				try {
					Collection<String> ids = new ArrayList<>();
					ids.add((String) item);
					status = appDeployer().statusAsync2(ids).findFirst().get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
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

