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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.utils.Sets;
import org.assertj.core.data.MapEntry;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationHealthCheck;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.Docker;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.applications.Route;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.util.FluentMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the {@link CloudFoundryAppDeployer}.
 *
 * @author Greg Turnquist
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 * @author Ben Hale
 * @author David Turanski
 */
public class CloudFoundryAppDeployerTests extends AbstractAppDeployerTestSupport {

	@TempDir
	public Path folder;

	@Override
	protected void postSetUp() {
		this.deploymentProperties.setServices(new HashSet<>(Arrays.asList("test-service-1", "test-service-2")));
		this.deploymentProperties.setEnv(Collections.singletonMap("SOME_GLOBAL_PROPERTY", "someGlobalValue"));
		this.deploymentProperties.getAppAdmin().setUser("user");
		this.deploymentProperties.getAppAdmin().setPassword("password");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deploy() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariables(defaultEnvironmentVariables())
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.EMPTY_MAP));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@Test
	public void deployMavenArtifactShouldDeleteByDefault() throws IOException, URISyntaxException {

		MavenResource resource = mock(MavenResource.class);

		Path mavenPath = folder.resolve("maven");
		Path artifactPath = mavenPath.resolve("artifact.jar");
		File mavenArtifact = artifactPath.toFile();
		given(resource.getFile()).willReturn(mavenArtifact);
		given(resource.getURI()).willReturn(new URI("maven://test:demo:0.0.1"));
		assertThat(this.deploymentProperties.isAutoDeleteMavenArtifacts()).isTrue();
		deployResource(this.deployer, resource);
		assertThat(mavenArtifact.getParentFile().exists()).isFalse();

	}

	@Test
	public void deployMavenArtifactShouldNotDeleteIfConfigured() throws IOException, URISyntaxException {
		MavenResource resource = mock(MavenResource.class);
		Path mavenPath = folder.resolve("maven");
		Path artifactPath = mavenPath.resolve("artifact.jar");
		File mavenArtifact = artifactPath.toFile();
		Files.createDirectories(artifactPath.getParent());
		given(resource.getFile()).willReturn(mavenArtifact);
		given(resource.getURI()).willReturn(new URI("maven://test:demo:0.0.1"));

		CloudFoundryDeploymentProperties deploymentProperties = new CloudFoundryDeploymentProperties();
		deploymentProperties.setAutoDeleteMavenArtifacts(false);
		CloudFoundryAppDeployer deployer = new CloudFoundryAppDeployer(this.applicationNameGenerator,
				deploymentProperties,
				this.operations, this.runtimeEnvironmentInfo);

		deployResource(deployer, resource);
		assertThat(mavenArtifact.getParentFile().exists()).isTrue();
	}

	@Test
	public void deployHttpArtifactShouldDelete() throws IOException, URISyntaxException {

		UrlResource resource = mock(UrlResource.class);

		Path downloadPath = folder.resolve("download");
		Path artifactPath = downloadPath.resolve("artifact.jar");
		File downloadedArtifact = artifactPath.toFile();

		given(resource.getFile()).willReturn(downloadedArtifact);
		given(resource.getURI()).willReturn(new URI("http://somehost/artifact.jar"));
		assertThat(this.deploymentProperties.isAutoDeleteMavenArtifacts()).isTrue();
		deployResource(this.deployer, resource);
		assertThat(downloadedArtifact.exists()).isFalse();
	}

	@SuppressWarnings("unchecked")
	private void deployResource(CloudFoundryAppDeployer deployer, Resource resource) throws IOException {
		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.EMPTY_MAP));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithServiceParameters() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");
		given(this.services.bind(any(BindServiceInstanceRequest.class)))
				.willReturn(Mono.empty());
		given(this.applications.start(any(StartApplicationRequest.class)))
				.willReturn(Mono.empty());

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariables(defaultEnvironmentVariables())
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.singletonMap(
								CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY, "'test-service-3 foo:bar'")));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}


	@SuppressWarnings("unchecked")
	@Test
	public void deployWithServiceParametersAndBindingError() throws Exception {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");
		AtomicInteger count = new AtomicInteger();
		CountDownLatch okLatch = new CountDownLatch(1);
		ClientV2Exception e = new ClientV2Exception(500, 10001,
				"The service broker could not perform this operation in parallel with other running operations",
				"CF-ConcurrencyError");
		// fail 2 times
		given(this.services.bind(any(BindServiceInstanceRequest.class)))
			.will(x -> {
				if (count.getAndIncrement() < 2) {
					return Mono.error(e);
				}
				okLatch.countDown();
				return Mono.empty();
			});
		given(this.applications.start(any(StartApplicationRequest.class)))
				.willReturn(Mono.empty());

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariables(defaultEnvironmentVariables())
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.singletonMap(
								CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY, "'test-service-3 foo:bar'")));

		assertThat(deploymentId).isEqualTo("test-application-id");
		// deploy is actually subscribe and forget so need to wait and
		// check that we got retries.
		assertThat(okLatch.await(30, TimeUnit.SECONDS)).isTrue();
		assertThat(count.get()).isEqualTo(3);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithAdditionalProperties() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		Map<String, String> environmentVariables = new HashMap<>();
		environmentVariables.put("test-key-1", "test-value-1");
		addGuidAndIndex(environmentVariables);

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariables(environmentVariables)
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(new AppDeploymentRequest(
				new AppDefinition("test-application", Collections.singletonMap("test-key-1", "test-value-1")), resource,
				FluentMap.<String, String>builder().entry("test-key-2", "test-value-2")
						.entry(CloudFoundryDeploymentProperties.USE_SPRING_APPLICATION_JSON_KEY, String.valueOf(false))
						.build()));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithAdditionalPropertiesInSpringApplicationJson() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		Map<String, String> environmentVariables = new HashMap<>();
		environmentVariables.put("SPRING_APPLICATION_JSON", "{\"test-key-1\":\"test-value-1\"}");
		addGuidAndIndex(environmentVariables);

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariables(environmentVariables)
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(new AppDeploymentRequest(
				new AppDefinition("test-application", Collections.singletonMap("test-key-1", "test-value-1")), resource,
				Collections.singletonMap("test-key-2", "test-value-2")));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@Test
	public void deployWithDeployerEnvironmentVariables() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack("test-buildpack")
						.disk(0)
						.environmentVariables(defaultEnvironmentVariables())
						.healthCheckType(ApplicationHealthCheck.NONE)
						.instances(0)
						.memory(0)
						.name("test-application-id")
						.noRoute(false)
						.host("test-host")
						.domain("test-domain")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.build(), Mono.empty());

		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(new AppDefinition("test-application",
				Collections.emptyMap()), resource,
				FluentMap.<String, String>builder().entry(CloudFoundryDeploymentProperties.BUILDPACK_PROPERTY_KEY, "test-buildpack")
						.entry(AppDeployer.DISK_PROPERTY_KEY, "0")
						.entry(CloudFoundryDeploymentProperties.DOMAIN_PROPERTY, "test-domain")
						.entry(CloudFoundryDeploymentProperties.HEALTHCHECK_PROPERTY_KEY, "none")
						.entry(CloudFoundryDeploymentProperties.HOST_PROPERTY, "test-host")
						.entry(AppDeployer.COUNT_PROPERTY_KEY, "0")
						.entry(AppDeployer.MEMORY_PROPERTY_KEY, "0")
						.entry(CloudFoundryDeploymentProperties.NO_ROUTE_PROPERTY, "false")
						.entry(CloudFoundryDeploymentProperties.ROUTE_PATH_PROPERTY, "/test-route-path")
						.entry(CloudFoundryDeploymentProperties.ENV_KEY + ".JBP_CONFIG_SPRING_AUTO_RECONFIGURATION", CfEnvConfigurer.ENABLED_FALSE)
						.entry(CloudFoundryDeploymentProperties.ENV_KEY + ".SPRING_PROFILES_ACTIVE", "cloud,foo")
						.build());

		String deploymentId = deployer.deploy(appDeploymentRequest);

		Map<String, String> merged = this.deployer.mergeEnvironmentVariables("test-application-id", appDeploymentRequest);
		assertThat(merged).containsEntry("JBP_CONFIG_SPRING_AUTO_RECONFIGURATION", CfEnvConfigurer.ENABLED_FALSE);
		assertThat(merged).containsEntry("SPRING_PROFILES_ACTIVE", "cloud,foo");
		assertThat(merged).containsEntry("SOME_GLOBAL_PROPERTY", "someGlobalValue");
		assertThat(merged).containsKey("SPRING_APPLICATION_JSON");
		assertThat(merged.get("SPRING_CLOUD_STREAMAPP_SECURITY_ADMIN-USER")).isEqualTo("user");
		assertThat(merged.get("SPRING_CLOUD_STREAMAPP_SECURITY_ADMIN-PASSWORD")).isEqualTo("password");
		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@Test
	public void automaticallyConfigureForCfEnv() throws JsonProcessingException {
		Resource resource = new FileSystemResource("src/test/resources/log-sink-rabbit-3.0.0.BUILD-SNAPSHOT.jar");
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(new AppDefinition("test-application",
				Collections.emptyMap()), resource, Collections.emptyMap());

		Map<String, String> env = deployer.mergeEnvironmentVariables("test-application-id", appDeploymentRequest);
		assertThat(env).containsEntry("SOME_GLOBAL_PROPERTY", "someGlobalValue");
		assertThat(env).containsEntry(CfEnvConfigurer.JBP_CONFIG_SPRING_AUTO_RECONFIGURATION, CfEnvConfigurer.ENABLED_FALSE);
		assertThat(env).containsKey("SPRING_APPLICATION_JSON");
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, String> saj = objectMapper.readValue(env.get("SPRING_APPLICATION_JSON"), HashMap.class);
		assertThat(saj).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, CfEnvConfigurer.CLOUD_PROFILE_NAME);

		resource = new FileSystemResource("src/test/resources/log-sink-rabbit-2.1.5.RELEASE.jar");
		appDeploymentRequest = new AppDeploymentRequest(new AppDefinition("test-application",
				Collections.emptyMap()), resource, Collections.emptyMap());
		env = deployer.mergeEnvironmentVariables("test-application-id", appDeploymentRequest);
		assertThat(env).doesNotContainKey(CfEnvConfigurer.JBP_CONFIG_SPRING_AUTO_RECONFIGURATION);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithApplicationDeploymentProperties() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack("test-buildpack")
						.disk(0)
						.environmentVariables(defaultEnvironmentVariables())
						.healthCheckType(ApplicationHealthCheck.NONE)
						.instances(0)
						.memory(0)
						.name("test-application-id")
						.noRoute(false)
						.host("test-host")
						.domain("test-domain")
						.routePath("/test-route-path")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						FluentMap.<String, String>builder().entry(CloudFoundryDeploymentProperties.BUILDPACK_PROPERTY_KEY, "test-buildpack")
								.entry(AppDeployer.DISK_PROPERTY_KEY, "0")
								.entry(CloudFoundryDeploymentProperties.DOMAIN_PROPERTY, "test-domain")
								.entry(CloudFoundryDeploymentProperties.HEALTHCHECK_PROPERTY_KEY, "none")
								.entry(CloudFoundryDeploymentProperties.HOST_PROPERTY, "test-host")
								.entry(AppDeployer.COUNT_PROPERTY_KEY, "0")
								.entry(AppDeployer.MEMORY_PROPERTY_KEY, "0")
								.entry(CloudFoundryDeploymentProperties.NO_ROUTE_PROPERTY, "false")
								.entry(CloudFoundryDeploymentProperties.ROUTE_PATH_PROPERTY, "/test-route-path")
								.build()));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithInvalidRoutePathProperty() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id",
				Mono.error(new IllegalArgumentException()),
				Mono.just(ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack("test-buildpack")
						.disk(0)
						.environmentVariables(defaultEnvironmentVariables())
						.healthCheckType(ApplicationHealthCheck.NONE)
						.instances(0)
						.memory(0)
						.name("test-application-id")
						.noRoute(false)
						.host("test-host")
						.domain("test-domain")
						.routePath("/test-route-path")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());
		try {
			assertThatThrownBy(() -> {
				this.deployer.deploy(new AppDeploymentRequest(
					new AppDefinition("test-application", Collections.emptyMap()),
					resource,
					FluentMap.<String, String>builder()
							.entry(CloudFoundryDeploymentProperties.BUILDPACK_PROPERTY_KEY, "test-buildpack")
							.entry(AppDeployer.DISK_PROPERTY_KEY, "0")
							.entry(CloudFoundryDeploymentProperties.DOMAIN_PROPERTY, "test-domain")
							.entry(CloudFoundryDeploymentProperties.HEALTHCHECK_PROPERTY_KEY, "none")
							.entry(CloudFoundryDeploymentProperties.HOST_PROPERTY, "test-host")
							.entry(AppDeployer.COUNT_PROPERTY_KEY, "0")
							.entry(AppDeployer.MEMORY_PROPERTY_KEY, "0")
							.entry(CloudFoundryDeploymentProperties.NO_ROUTE_PROPERTY, "false")
							.entry(CloudFoundryDeploymentProperties.ROUTE_PATH_PROPERTY, "test-route-path")
							.build()));

			}).isInstanceOf(IllegalArgumentException.class).as("Illegal Argument exception is expected.");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("Cloud Foundry routes must start with \"/\". Route passed = [test-route-path].");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithCustomDeploymentProperties() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		this.deploymentProperties.setBuildpack("test-buildpack");
		this.deploymentProperties.setDisk("0");
		this.deploymentProperties.setDomain("test-domain");
		this.deploymentProperties.setHealthCheck(ApplicationHealthCheck.NONE);
		this.deploymentProperties.setHost("test-host");
		this.deploymentProperties.setInstances(0);
		this.deploymentProperties.setMemory("0");

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack("test-buildpack")
						.disk(0)
						.domain("test-domain")
						.environmentVariables(defaultEnvironmentVariables())
						.healthCheckType(ApplicationHealthCheck.NONE)
						.host("test-host")
						.instances(0)
						.memory(0)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.emptyMap()));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithMultipleRoutes() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id",
				Mono.error(new IllegalArgumentException()),
				Mono.just(ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		this.deploymentProperties.setBuildpack("test-buildpack");
		this.deploymentProperties.setDisk("0");
		this.deploymentProperties.setHealthCheck(ApplicationHealthCheck.NONE);
		this.deploymentProperties.setRoutes(Sets.newHashSet("route1.test-domain", "route2.test-domain"));
		this.deploymentProperties.setInstances(0);
		this.deploymentProperties.setMemory("0");

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack("test-buildpack")
						.disk(0)
						.routes(Sets.newHashSet(
								Route.builder().route("route1.test-domain").build(),
								Route.builder().route("route2.test-domain").build()))
						.environmentVariables(defaultEnvironmentVariables())
						.healthCheckType(ApplicationHealthCheck.NONE)
						.instances(0)
						.memory(0)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(new AppDeploymentRequest(
				new AppDefinition("test-application", Collections.emptyMap()),
				resource,
				Collections.emptyMap()));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithMultipleRoutesAndHostOrDomainMutuallyExclusive() {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		assertThatThrownBy(() -> {
			givenRequestGetApplication("test-application-id",
			Mono.error(new IllegalArgumentException()),
			Mono.just(ApplicationDetail.builder()
					.id("test-application-id")
					.name("test-application")
					.build()));
		}).isInstanceOf(IllegalStateException.class)
				.as("routes and hosts cannot be set, expect cf java client to throw an exception");

		this.deploymentProperties.setHost("route0");
		this.deploymentProperties.setDomain("test-domain");
		this.deploymentProperties.setRoutes(Sets.newHashSet("route1.test-domain", "route2.test-domain"));

		assertThatThrownBy(() -> {
			this.deployer.deploy(new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()),
					resource, Collections.emptyMap()));
		}).isInstanceOf(IllegalStateException.class)
				.as("routes and hosts cannot be set, expect cf java client to throw an exception");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployWithGroup() throws IOException {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		given(this.applicationNameGenerator.generateAppName("test-group-test-application")).willReturn(
				"test-group-test-application-id");

		givenRequestGetApplication("test-group-test-application-id", Mono.error(new IllegalArgumentException()),
				Mono.just(ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-group-test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-group-test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.disk(1024)
						.environmentVariable("SPRING_CLOUD_APPLICATION_GROUP", "test-group")
						.environmentVariable("SPRING_APPLICATION_JSON", "{}")
						.environmentVariable("SPRING_APPLICATION_INDEX", "${vcap.application.instance_index}")
						.environmentVariable("SPRING_CLOUD_APPLICATION_GUID",
								"${vcap.application.name}:${vcap.application.instance_index}")
						.instances(1)
						.memory(1024)
						.name("test-group-test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.singletonMap(AppDeployer.GROUP_PROPERTY_KEY, "test-group")));

		assertThat(deploymentId).isEqualTo("test-group-test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deployDockerResource() {
		Resource resource = new DockerResource("somecorp/someimage:latest");

		given(this.applicationNameGenerator.generateAppName("test-application")).willReturn("test-application-id");

		givenRequestGetApplication("test-application-id", Mono.error(new IllegalArgumentException()), Mono.just(
				ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(0)
						.stack("test-stack")
						.build()));

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.docker(Docker.builder().image("somecorp/someimage:latest").build())
						.disk(1024)
						.environmentVariables(defaultEnvironmentVariables())
						.instances(1)
						.memory(1024)
						.name("test-application-id")
						.service("test-service-2")
						.service("test-service-1")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		String deploymentId = this.deployer.deploy(
				new AppDeploymentRequest(new AppDefinition("test-application", Collections.emptyMap()), resource,
						Collections.emptyMap()));

		assertThat(deploymentId).isEqualTo("test-application-id");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfCrashedApplicationIsFailed() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("CRASHED").index("1").build())
				.build()));

		AppStatus status = this.deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.failed);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfDownApplicationIsDeploying() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("DOWN").index("1").build())
				.build()));

		AppStatus status = this.deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.deploying);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfFlappingApplicationIsDeployed() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("FLAPPING").index("1").build())
				.build()));

		AppStatus status = deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.deployed);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfRunningApplicationIsDeployed() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("RUNNING").index("1").build())
				.build()));

		AppStatus status = this.deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.deployed);
		assertThat(status.getInstances().get("test-application-0").toString())
				.isEqualTo("CloudFoundryAppInstanceStatus[test-application-0 : deployed]");
		assertThat(status.getInstances().get("test-application-0").getAttributes())
				.containsOnly(
						MapEntry.entry(CloudFoundryAppInstanceStatus.GUID, "test-application:0"),
						MapEntry.entry(CloudFoundryAppInstanceStatus.INDEX, "0"),
						MapEntry.entry(CloudFoundryAppInstanceStatus.CF_GUID, "test-application-id"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfStartingApplicationIsDeploying() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("STARTING").index("1").build())
				.build()));

		AppStatus status = this.deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.deploying);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusOfUnknownApplicationIsUnknown() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("UNKNOWN").index("1").build())
				.build()));

		AppStatus status = this.deployer.status("test-application-id");

		assertThat(status.getState()).isEqualTo(DeploymentState.unknown);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusWithAbnormalInstanceStateThrowsException() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("ABNORMAL").index("1").build())
				.build()));

		assertThatThrownBy(() -> {
			this.deployer.status("test-application-id").getState();
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unsupported CF state");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusWithFailingCAPICallRetries() {
		AtomicInteger i = new AtomicInteger();
		Mono<ApplicationDetail> m = Mono.create(s -> {
			if (i.incrementAndGet() == 2) {
				s.success(ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(1)
						.stack("test-stack")
						.instanceDetail(InstanceDetail.builder().state("UNKNOWN").index("1").build())
						.build());
			}
			else {
				s.error(new RuntimeException("Simulated Server Side error"));
			}
		});
		givenRequestGetApplication("test-application-id", m);

		DeploymentState state = this.deployer.status("test-application-id").getState();
		assertThat(state).isEqualTo(DeploymentState.unknown);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusWithFailingCAPICallRetriesEventualError() {
		AtomicInteger i = new AtomicInteger();
		Mono<ApplicationDetail> m = Mono.create(s -> {
			if (i.incrementAndGet() == 12) { // 12 is more than the number of retries
				s.success(ApplicationDetail.builder()
						.diskQuota(0)
						.id("test-application-id")
						.instances(1)
						.memoryLimit(0)
						.name("test-application")
						.requestedState("RUNNING")
						.runningInstances(1)
						.stack("test-stack")
						.instanceDetail(InstanceDetail.builder().state("UNKNOWN").index("1").build())
						.build());
			}
			else {
				s.error(new RuntimeException("Simulated Server Side error"));
			}
		});
		givenRequestGetApplication("test-application-id", m);
		this.deployer.deploymentProperties.setStatusTimeout(200); // Will cause wait of 20ms then 40ms,80ms

		DeploymentState state = this.deployer.status("test-application-id").getState();
		assertThat(state).isEqualTo(DeploymentState.error);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void statusWithErrorThrownOnBlocking() {
		AtomicInteger i = new AtomicInteger();
		Mono<ApplicationDetail> m = Mono.delay(Duration.ofSeconds(30)).then(Mono.create(s -> {
			i.incrementAndGet();
			s.success(ApplicationDetail.builder()
					.diskQuota(0)
					.id("test-application-id")
					.instances(1)
					.memoryLimit(0)
					.name("test-application")
					.requestedState("RUNNING")
					.runningInstances(1)
					.stack("test-stack")
					.instanceDetail(InstanceDetail.builder().state("UNKNOWN").index("1").build())
					.build());
		}));
		givenRequestGetApplication("test-application-id", m);
		this.deployer.deploymentProperties.setStatusTimeout(1);// Is less than the delay() above

		DeploymentState state = this.deployer.status("test-application-id").getState();
		assertThat(state).isEqualTo(DeploymentState.error);
		assertThat(i.get()).isEqualTo(0);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void undeploy() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("RUNNING").index("1").build())
				.build()));
		givenRequestDeleteApplication("test-application-id", Mono.empty());

		this.deployer.undeploy("test-application-id");

	}

	@SuppressWarnings("unchecked")
	@Test
	public void scale() {
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(2)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(2)
				.stack("test-stack")
				.instanceDetail(InstanceDetail.builder().state("RUNNING").index("1").build())
				.build()));
		givenRequestScaleApplication("test-application-id", 2, 1024, 1024, Mono.empty());
		this.deployer.scale(new AppScaleRequest("test-application-id", 2));
	}
}
