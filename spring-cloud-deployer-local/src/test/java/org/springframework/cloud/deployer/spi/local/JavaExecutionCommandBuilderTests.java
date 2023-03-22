/*
 * Copyright 2015-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.local;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JavaExecutionCommandBuilderTests {

	private JavaCommandBuilder commandBuilder;
	private List<String> args;
	private Map<String, String> deploymentProperties;
	private LocalDeployerProperties localDeployerProperties;

	@BeforeEach
	public void setUp() {
		args = new ArrayList<>();
		deploymentProperties = new HashMap<>();
		localDeployerProperties = new LocalDeployerProperties();
		commandBuilder = new JavaCommandBuilder(localDeployerProperties);
	}

	@Test
	public void testDirectJavaMemoryOption() {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(1);
		assertThat(args.get(0)).isEqualTo("-Xmx1024m");
	}

	@Test
	public void testDirectJavaMemoryOptionWithG() {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1g");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(1);
		assertThat(args.get(0)).isEqualTo("-Xmx1024m");
	}

	@Test
	public void testJavaMemoryOption() {
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Xmx1024m");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(1);
		assertThat(args.get(0)).isEqualTo("-Xmx1024m");
	}

	@Test
	public void testJavaMemoryOptionWithKebabCase() {
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".java-opts", "-Xmx1024m");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(1);
		assertThat(args.get(0)).isEqualTo("-Xmx1024m");
	}

	@Test
	public void testJavaCmdOption() throws Exception {
		Map<String, String> properties = new HashMap<>();
		properties.put(LocalDeployerProperties.PREFIX + ".javaCmd", "/test/java");
		Resource resource = mock(Resource.class);
		when(resource.getFile()).thenReturn(new File("/"));
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(mock(AppDefinition.class), resource, properties);
		ProcessBuilder builder = commandBuilder.buildExecutionCommand(appDeploymentRequest, new HashMap<>(),
				"deployerId", Optional.of(1), new LocalDeployerProperties(), Optional.empty());
		assertThat(builder.command().get(0)).isEqualTo("/test/java");
	}

	@Test
	public void testJavaCmdOptionWithKebabCase() throws Exception {
		Map<String, String> properties = new HashMap<>();
		properties.put(LocalDeployerProperties.PREFIX + ".java-cmd", "/test/java");
		Resource resource = mock(Resource.class);
		when(resource.getFile()).thenReturn(new File("/"));
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(mock(AppDefinition.class), resource, properties);
		ProcessBuilder builder = commandBuilder.buildExecutionCommand(appDeploymentRequest, new HashMap<>(),
				"deployerId", Optional.of(1), new LocalDeployerProperties(), Optional.empty());
		assertThat(builder.command().get(0)).isEqualTo("/test/java");
	}

	@Test
	public void testOverrideMemoryOptions() {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Xmx2048m");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(1);
		assertThat(args.get(0)).isEqualTo("-Xmx2048m");
	}

	@Test
	public void testDirectMemoryOptionsWithOtherOptions() {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Dtest=foo");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(2);
		assertThat(args.get(0)).isEqualTo("-Xmx1024m");
		assertThat(args.get(1)).isEqualTo("-Dtest=foo");
	}

	@Test
	public void testMultipleOptions() {
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(2);
		assertThat(args.get(0)).isEqualTo("-Dtest=foo");
		assertThat(args.get(1)).isEqualTo("-Dbar=baz");
	}

	@Test
	public void testConfigurationPropertiesOverride() {
		localDeployerProperties.setJavaOpts("-Dfoo=test -Dbaz=bar");
		commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
		assertThat(args).hasSize(2);
		assertThat(args.get(0)).isEqualTo("-Dfoo=test");
		assertThat(args.get(1)).isEqualTo("-Dbaz=bar");
	}

	@Test
	public void testJarExecution() {
		AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
		AppDeploymentRequest appDeploymentRequest =
				new AppDeploymentRequest(definition, testResource(), deploymentProperties);
		commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
		assertThat(args).hasSize(2);
		assertThat(args.get(0)).isEqualTo("-jar");
		assertThat(args.get(1)).contains("testResource.txt");
	}

	@Test
	public void testBadResourceExecution() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
			deploymentProperties.put(LocalDeployerProperties.PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
			AppDeploymentRequest appDeploymentRequest =
					new AppDeploymentRequest(definition, new UrlResource("https://spring.io"), deploymentProperties);
			commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
		});
	}

	@Test
	public void testCommandBuilderSpringApplicationJson() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		LocalAppDeployer deployer = new LocalAppDeployer(properties);
		AppDefinition definition = new AppDefinition("foo", Collections.singletonMap("foo", "bar"));

		deploymentProperties.put(LocalDeployerProperties.DEBUG_PORT, "9999");
		deploymentProperties.put(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testResource(), deploymentProperties);

		ProcessBuilder builder = deployer.buildProcessBuilder(request, definition.getProperties(), Optional.of(1), "foo");
		assertThat(builder.environment().keySet()).contains(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON);
		assertThat(builder.environment().get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON)).isEqualTo("{\"foo\":\"bar\"}");
	}

	@Test
	public void testCommandBuilderWithSpringApplicationJson() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		LocalAppDeployer deployer = new LocalAppDeployer(properties);
		Map<String, String> applicationProperties = new HashMap<>();
		applicationProperties.put("foo", "bar");
		String SAJ = "{\"debug\":\"true\"}";
		applicationProperties.put(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON, SAJ);
		AppDefinition definition = new AppDefinition("foo", applicationProperties);

		deploymentProperties.put(LocalDeployerProperties.DEBUG_PORT, "9999");
		deploymentProperties.put(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, testResource(), deploymentProperties);


		ProcessBuilder builder = deployer.buildProcessBuilder(request, definition.getProperties(), Optional.of(1), "foo");
		assertThat(builder.environment().keySet()).contains(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON);
		assertThat(builder.environment().get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON)).isEqualTo("{\"foo\":\"bar\",\"debug\":\"true\"}");

	}

	@Test
	public void testRetainEnv() {
		LocalDeployerProperties properties1 = new LocalDeployerProperties();
		LocalAppDeployer deployer1 = new LocalAppDeployer(properties1);
		AppDefinition definition1 = new AppDefinition("foo", null);
		AppDeploymentRequest request1 = new AppDeploymentRequest(definition1, testResource(), deploymentProperties);
		ProcessBuilder builder1 = deployer1.buildProcessBuilder(request1, definition1.getProperties(), Optional.of(1), "foo");
		List<String> env1 = builder1.environment().keySet().stream().map(String::toLowerCase).collect(Collectors.toList());

		LocalDeployerProperties properties2 = new LocalDeployerProperties();
		properties2.setEnvVarsToInherit(new String[0]);
		LocalAppDeployer deployer2 = new LocalAppDeployer(properties2);
		AppDefinition definition2 = new AppDefinition("foo", null);
		AppDeploymentRequest request2 = new AppDeploymentRequest(definition2, testResource(), deploymentProperties);
		ProcessBuilder builder2 = deployer2.buildProcessBuilder(request2, definition2.getProperties(), Optional.of(1), "foo");
		List<String> env2 = builder2.environment().keySet().stream().map(String::toLowerCase).collect(Collectors.toList());

		if (env1.contains("path")) {
			// path should be there, and it was check that something were removed
			assertThat(builder1.environment().keySet().size()).isGreaterThan(builder2.environment().keySet().size());
		}
		assertThat(env2).doesNotContain("path");
	}

	protected Resource testResource() {
		return new ClassPathResource("testResource.txt");
	}

}
