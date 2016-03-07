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
import static org.springframework.cloud.deployer.spi.process.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.process.DeploymentState.deploying;
import static org.springframework.cloud.deployer.spi.process.DeploymentState.failed;
import static org.springframework.cloud.deployer.spi.process.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.process.ProcessDeployer;
import org.springframework.cloud.deployer.spi.process.ProcessStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Abstract base class for integration tests of
 * {@link org.springframework.cloud.deployer.spi.process.ProcessDeployer} implementations.
 *
 * <p>Inheritors should setup an environment with a newly created {@link org.springframework.cloud.deployer.spi.process.ProcessDeployer}
 * that has no pre-deployed applications. Tests in this class are independent and leave the deployer in a clean state after they successfully
 * run.</p>
 *
 * <p>As deploying an application is often quite time consuming, some tests often test various aspects of deployment in a
 * row, to avoid re-deploying apps over and over again.</p>
 *
 * @author Eric Bottard
 */
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractProcessDeployerTests {

	protected abstract ProcessDeployer processDeployer();

	@Test
	public void testUnknownDeployment() {
		String unknownId = randomName();
		ProcessStatus status = processDeployer().status(unknownId);

		assertThat(status.getProcessDeploymentId(), is(unknownId));
		assertThat("The map was not empty: " + status.getInstances(), status.getInstances().isEmpty(), is(true));
		assertThat(status.getState(), is(unknown));
	}

	/**
	 * Tests a simple deploy-undeploy cycle.
	 */
	@Test
	public void testSimpleDeployment() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = mavenResource("org.springframework.cloud.stream.module", "time-source","jar","exec","1.0.0.BUILD-SNAPSHOT");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		String deploymentId = processDeployer().deploy(request);
		Attempts timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(deployed))), timeout.noAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		processDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(unknown))), timeout.noAttempts, timeout.pause));
	}

	/**
	 * A process deployer should be able to re-deploy an application after it has been un-deployed.
	 * This test makes sure the deployer does not leave things lying around for example.
	 */
	@Test
	public void testRedeploy() {
		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = mavenResource("org.springframework.cloud.stream.module", "time-source","jar","exec","1.0.0.BUILD-SNAPSHOT");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		String deploymentId = processDeployer().deploy(request);
		Attempts timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(deployed))), timeout.noAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		processDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(unknown))), timeout.noAttempts, timeout.pause));

		// Attempt re-deploy of SAME request
		deploymentId = processDeployer().deploy(request);
		timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(deployed))), timeout.noAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		processDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(unknown))), timeout.noAttempts, timeout.pause));

	}

	/**
	 * Tests that a module which takes a long time to deploy is correctly reported as deploying.
	 * Test that such a module can be killed (undeployed).
	 */
	@Test
	public void testDeployingStateCalculationAndCancel() {
		Map<String, String> properties = new HashMap<>();
		properties.put("initDelay", "" + 1000 * 60 * 60); // 1hr
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = mavenResource("org.springframework.cloud.stream.module", "integration-test-processor","jar","exec","1.0.0.BUILD-SNAPSHOT");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		String deploymentId = processDeployer().deploy(request);
		Attempts timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(deploying))), timeout.noAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		processDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(unknown))), timeout.noAttempts, timeout.pause));

	}

	@Test
	public void testFailedDeployment() {
		Map<String, String> properties = new HashMap<>();
		properties.put("killDelay", "0");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = mavenResource("org.springframework.cloud.stream.module", "integration-test-processor","jar","exec","1.0.0.BUILD-SNAPSHOT");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

		String deploymentId = processDeployer().deploy(request);
		Attempts timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(failed))), timeout.noAttempts, timeout.pause));

		timeout = undeploymentTimeout();
		processDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<ProcessStatus>hasProperty("state", is(unknown))), timeout.noAttempts, timeout.pause));
	}

	protected String randomName() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Return the timeout to use for repeatedly querying a module while it is being deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	protected Attempts deploymentTimeout() {
		return new Attempts(12, 5000);
	}

	/**
	 * Return the timeout to use for repeatedly querying a module while it is being un-deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	protected Attempts undeploymentTimeout() {
		return new Attempts(20, 5000);
	}

	private Resource mavenResource(String groupId, String artifactId, String ext, String classifier, String version) {
		// TODO: actually resolve using Aether,
		// or use integration-test-processor in every test and bring at build time in src/test/resources?
		File localRepository = new File(System.getProperty("user.home") + File.separator + ".m2" +
				File.separator + "repository");
		String path = groupId.replace(".", File.separator) + File.separator
				+ artifactId + File.separator + version + File.separator
				+ artifactId + "-" + version + "-" + classifier + "." + ext;
		File jarFile = new File(localRepository, path);
		return new FileSystemResource(jarFile);
	}



	/**
	 * Represents a timeout for querying status, with repetitive queries until a certain number have been made.
	 *
	 * @author Eric Bottard
	 */
	protected static class Attempts {

		public final int noAttempts;

		public final int pause;

		public Attempts(int noAttempts, int pause) {
			this.noAttempts = noAttempts;
			this.pause = pause;
		}
	}

	/**
	 * A Hamcrest Matcher that queries the deployment status for some process id.
	 *
	 * @author Eric Bottard
	 */
	protected Matcher<String> hasStatusThat(final Matcher<ProcessStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private ProcessStatus status;

			@Override
			public boolean matches(Object item) {
				status = processDeployer().status((String) item);
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

