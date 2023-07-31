/*
 * Copyright 2019-2020 the original author or authors.
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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalDeployerPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	@EnabledOnOs(OS.LINUX)
	public void defaultNoPropertiesSet() {
		this.contextRunner
			.withUserConfiguration(Config1.class)
			.run((context) -> {
				LocalDeployerProperties properties = context.getBean(LocalDeployerProperties.class);
				assertThat(properties.getDebugPort()).isNull();
				assertThat(properties.getDebugSuspend()).isEqualTo(LocalDeployerProperties.DebugSuspendType.y);
				assertThat(properties.isDeleteFilesOnExit()).isTrue();
				assertThat(properties.getEnvVarsToInherit()).containsExactlyInAnyOrder("TMP", "LANG", "LANGUAGE", "LC_.*",
					"PATH", "SPRING_APPLICATION_JSON");
				assertThat(properties.isInheritLogging()).isFalse();
				assertThat(properties.getJavaCommand("2")).contains("java");
				assertThat(properties.getJavaOpts()).isNull();
				assertThat(properties.getMaximumConcurrentTasks()).isEqualTo(20);
				assertThat(properties.getPortRange()).isNotNull();
				assertThat(properties.getPortRange().getLow()).isEqualTo(20000);
				assertThat(properties.getPortRange().getHigh()).isEqualTo(61000);
				assertThat(properties.getShutdownTimeout()).isEqualTo(30);
				assertThat(properties.isUseSpringApplicationJson()).isTrue();
				assertThat(properties.getDocker().getNetwork()).isEqualTo("bridge");
				assertThat(properties.getStartupProbe()).isNotNull();
				assertThat(properties.getStartupProbe().getPath()).isNull();
				assertThat(properties.getHealthProbe()).isNotNull();
				assertThat(properties.getHealthProbe().getPath()).isNull();
			});
	}

	@Test
	public void setAllProperties() {
		this.contextRunner
			.withInitializer(context -> {
				Map<String, Object> map = new HashMap<>();
				map.put("spring.cloud.deployer.local.debug-port", "8888");
				map.put("spring.cloud.deployer.local.debug-suspend", "n");
				map.put("spring.cloud.deployer.local.delete-files-on-exit", false);
				map.put("spring.cloud.deployer.local.env-vars-to-inherit", "FOO,BAR");
				map.put("spring.cloud.deployer.local.inherit-logging", true);
				map.put("spring.cloud.deployer.local.java-home-path.2", "foobar1");
				map.put("spring.cloud.deployer.local.java-opts", "foobar2");
				map.put("spring.cloud.deployer.local.maximum-concurrent-tasks", 1234);
				map.put("spring.cloud.deployer.local.port-range.low", 2345);
				map.put("spring.cloud.deployer.local.port-range.high", 2346);
				map.put("spring.cloud.deployer.local.shutdown-timeout", 3456);
				map.put("spring.cloud.deployer.local.use-spring-application-json", false);
				map.put("spring.cloud.deployer.local.docker.network", "spring-cloud-dataflow-server_default");
				map.put("spring.cloud.deployer.local.docker.port-mappings", "9091:5678");
				map.put("spring.cloud.deployer.local.docker.volume-mounts", "/tmp:/opt");
				map.put("spring.cloud.deployer.local.startup-probe.path", "/path1");
				map.put("spring.cloud.deployer.local.health-probe.path", "/path2");
				map.put("spring.cloud.deployer.local.app-admin.user", "user");
				map.put("spring.cloud.deployer.local.app-admin.password", "password");

				context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, map));
			})
			.withUserConfiguration(Config1.class)
			.run((context) -> {
				LocalDeployerProperties properties = context.getBean(LocalDeployerProperties.class);
				assertThat(properties.getDebugPort()).isEqualTo(8888);
				assertThat(properties.getDebugSuspend()).isEqualTo(LocalDeployerProperties.DebugSuspendType.n);
				assertThat(properties.isDeleteFilesOnExit()).isFalse();
				assertThat(properties.getEnvVarsToInherit()).containsExactly("FOO", "BAR");
				assertThat(properties.isInheritLogging()).isTrue();
				assertThat(properties.getJavaHomePath().get("2")).contains("foobar1");
				assertThat(properties.getJavaOpts()).contains("foobar2");
				assertThat(properties.getMaximumConcurrentTasks()).isEqualTo(1234);
				assertThat(properties.getPortRange()).isNotNull();
				assertThat(properties.getPortRange().getLow()).isEqualTo(2345);
				assertThat(properties.getPortRange().getHigh()).isEqualTo(2346);
				assertThat(properties.getShutdownTimeout()).isEqualTo(3456);
				assertThat(properties.isUseSpringApplicationJson()).isFalse();
				assertThat(properties.getDocker().getNetwork()).isEqualTo("spring-cloud-dataflow-server_default");
				assertThat(properties.getDocker().getPortMappings()).isEqualTo("9091:5678");
				assertThat(properties.getDocker().getVolumeMounts()).isEqualTo("/tmp:/opt");
				assertThat(properties.getStartupProbe().getPath()).isEqualTo("/path1");
				assertThat(properties.getHealthProbe().getPath()).isEqualTo("/path2");
				assertThat(properties.getAppAdmin().getUser()).isEqualTo("user");
				assertThat(properties.getAppAdmin().getPassword()).isEqualTo("password");
			});
	}


	@Test
	public void setAllPropertiesCamelCase() {
		this.contextRunner
			.withInitializer(context -> {

				Map<String, Object> map = new HashMap<>();
				map.put("spring.cloud.deployer.local.debugPort", "8888");
				map.put("spring.cloud.deployer.local.debugSuspend", "n");
				map.put("spring.cloud.deployer.local.deleteFilesOnExit", false);
				map.put("spring.cloud.deployer.local.envVarsToInherit", "FOO,BAR");
				map.put("spring.cloud.deployer.local.inheritLogging", true);
				map.put("spring.cloud.deployer.local.javaHomePath.2", "foobar1");
				map.put("spring.cloud.deployer.local.javaOpts", "foobar2");
				map.put("spring.cloud.deployer.local.maximumConcurrentTasks", 1234);
				map.put("spring.cloud.deployer.local.portRange.low", 2345);
				map.put("spring.cloud.deployer.local.portRange.high", 2346);
				map.put("spring.cloud.deployer.local.shutdownTimeout", 3456);
				map.put("spring.cloud.deployer.local.useSpringApplicationJson", false);
				map.put("spring.cloud.deployer.local.docker.network", "spring-cloud-dataflow-server_default");
				map.put("spring.cloud.deployer.local.docker.portMappings", "9091:5678");
				map.put("spring.cloud.deployer.local.docker.volumeMounts", "/tmp:/opt");
				map.put("spring.cloud.deployer.local.appAdmin.user", "user");
				map.put("spring.cloud.deployer.local.appAdmin.password", "password");

				context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, map));
			})
			.withUserConfiguration(Config1.class)
			.run((context) -> {
				LocalDeployerProperties properties = context.getBean(LocalDeployerProperties.class);
				assertThat(properties.getDebugPort()).isEqualTo(8888);
				assertThat(properties.getDebugSuspend()).isEqualTo(LocalDeployerProperties.DebugSuspendType.n);
				assertThat(properties.isDeleteFilesOnExit()).isFalse();
				assertThat(properties.getEnvVarsToInherit()).containsExactly("FOO", "BAR");
				assertThat(properties.isInheritLogging()).isTrue();
				assertThat(properties.getJavaHomePath().get("2")).contains("foobar1");
				assertThat(properties.getJavaOpts()).contains("foobar2");
				assertThat(properties.getMaximumConcurrentTasks()).isEqualTo(1234);
				assertThat(properties.getPortRange()).isNotNull();
				assertThat(properties.getPortRange().getLow()).isEqualTo(2345);
				assertThat(properties.getPortRange().getHigh()).isEqualTo(2346);
				assertThat(properties.getShutdownTimeout()).isEqualTo(3456);
				assertThat(properties.isUseSpringApplicationJson()).isFalse();
				assertThat(properties.getDocker().getNetwork()).isEqualTo("spring-cloud-dataflow-server_default");
				assertThat(properties.getDocker().getPortMappings()).isEqualTo("9091:5678");
				assertThat(properties.getDocker().getVolumeMounts()).isEqualTo("/tmp:/opt");
				assertThat(properties.getAppAdmin().getUser()).isEqualTo("user");
				assertThat(properties.getAppAdmin().getPassword()).isEqualTo("password");
			});
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	public void testOnWindows() {
		this.contextRunner
			.withInitializer(context -> {
				Map<String, Object> map = new HashMap<>();
				map.put("spring.cloud.deployer.local.working-directories-root", "file:/C:/tmp");
				context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, map));
			})

			.withUserConfiguration(Config1.class)
			.run((context) -> {
				LocalDeployerProperties properties = context.getBean(LocalDeployerProperties.class);
				assertThat(properties.getWorkingDirectoriesRoot()).isNotNull();
				assertThat(properties.getWorkingDirectoriesRoot().toString()).isEqualTo("C:\\tmp");
			});
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	public void testOnLinux() {
		this.contextRunner
			.withInitializer(context -> {
				Map<String, Object> map = new HashMap<>();
				map.put("spring.cloud.deployer.local.working-directories-root", "/tmp");

				context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, map));
			})
			.withUserConfiguration(Config1.class)
			.run((context) -> {
				LocalDeployerProperties properties = context.getBean(LocalDeployerProperties.class);
				assertThat(properties.getWorkingDirectoriesRoot()).isNotNull();
				assertThat(properties.getWorkingDirectoriesRoot().toString()).isEqualTo("/tmp");
			});
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	public void testCopyProperties() {
		LocalDeployerProperties from = new LocalDeployerProperties();
		LocalDeployerProperties to = new LocalDeployerProperties(from);
		assertThat(from).isEqualTo(to);

		from = new LocalDeployerProperties();
		from.setDebugPort(1234);
		from.setDebugSuspend(LocalDeployerProperties.DebugSuspendType.y);
		from.getDocker().setNetwork("network");
		from.setDeleteFilesOnExit(true);
		from.setEnvVarsToInherit(new String[]{"foobar"});
		from.setInheritLogging(false);
		from.getJavaHomePath().put("2", "javaCmd");
		from.setJavaOpts("javaOpts");
		from.setMaximumConcurrentTasks(234);
		from.getPortRange().setHigh(2345);
		from.getPortRange().setLow(2344);
		from.setShutdownTimeout(345);
		from.setUseSpringApplicationJson(false);
		from.setWorkingDirectoriesRoot(Paths.get("/first"));
		to = new LocalDeployerProperties(from);
		assertThat(from).isEqualTo(to);
	}

	@EnableConfigurationProperties({LocalDeployerProperties.class})
	private static class Config1 {
	}
}
