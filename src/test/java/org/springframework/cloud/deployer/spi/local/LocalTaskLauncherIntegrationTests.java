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

package org.springframework.cloud.deployer.spi.local;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncherIntegrationTests.Config;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.test.AbstractIntegrationTests;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationJUnit5Tests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link LocalTaskLauncher}.
 *
 * Now supports running with Docker images for tests, just set this env var:
 *
 *   SPRING_CLOUD_DEPLOYER_SPI_TEST_USE_DOCKER=true
 *
 * @author Eric Bottard
 * @author Janne Valkealahti
 * @author David Turanski
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 *
 */
@SpringBootTest(classes = {Config.class, AbstractIntegrationTests.Config.class}, value = {
		"maven.remoteRepositories.springRepo.url=https://repo.spring.io/snapshot" })
@ExtendWith(OutputCaptureExtension.class)
public class LocalTaskLauncherIntegrationTests extends AbstractTaskLauncherIntegrationJUnit5Tests {

	// @Rule
	// public OutputCaptureRule outputCapture = new OutputCaptureRule();

	@Autowired
	private TaskLauncher taskLauncher;

	@Value("${spring-cloud-deployer-spi-test-use-docker:false}")
	private boolean useDocker;

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
	}

	@Override
	protected Resource testApplication() {
		if (useDocker) {
			log.info("Using Docker image for tests");
			return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
		}
		return super.testApplication();
	}

	@Override
	protected String randomName() {
		if (LocalDeployerUtils.isWindows()) {
			// tweak random dir name on win to be shorter
			String uuid = UUID.randomUUID().toString();
			long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
			return testName + Long.toString(l, Character.MAX_RADIX);
		}
		else {
			return super.randomName();
		}
	}

	@Test
	public void testPassingServerPortViaCommandLineArgs(CapturedOutput output){
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");

		AppDefinition definition = new AppDefinition(this.randomName(), appProperties);

		basicLaunchAndValidation(definition, null);
		assertThat(output).contains("Logs will be in");
	}


	@Test
	public void testInheritLoggingAndWorkDir(CapturedOutput output) throws IOException {

		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");

		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		Path tmpPath = new File(System.getProperty("java.io.tmpdir")).toPath();
		Path customWorkDirRoot = tmpPath.resolve("spring-cloud-deployer-task-workdir");
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".working-directories-root", customWorkDirRoot.toFile().getAbsolutePath());

		AppDefinition definition = new AppDefinition(this.randomName(), appProperties);

		List<Path> beforeDirs = new ArrayList<>();
		beforeDirs.add(customWorkDirRoot);
		if (Files.exists(customWorkDirRoot)) {
			beforeDirs = Files.walk(customWorkDirRoot, 1)
					.filter(path -> Files.isDirectory(path))
					.collect(Collectors.toList());
		}

		basicLaunchAndValidation(definition, deploymentProperties);
		assertThat(output).contains("Logs will be inherited.");

		List<Path> afterDirs = Files.walk(customWorkDirRoot, 1)
				.filter(path -> Files.isDirectory(path))
				.collect(Collectors.toList());
		assertThat(afterDirs).as("Additional working directory not created").hasSize(beforeDirs.size() + 1);

		// clean up if test passed
		FileSystemUtils.deleteRecursively(customWorkDirRoot);
	}

	@Test
	public void testAppLogRetrieval() {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		String launchId1 = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId1).getState()).isEqualTo(LaunchState.complete);
        });

		String logContent = taskLauncher().getLog(launchId1);
		assertThat(logContent).contains("Starting DeployerIntegrationTestApplication");
	}

	@Test
	public void testDeleteHistoryOnReLaunch() {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		String launchId1 = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId1).getState()).isEqualTo(LaunchState.complete);
        });

		String launchId2 = taskLauncher().launch(request);

		assertThat(launchId2).isNotEqualTo(launchId1);

		timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId2).getState()).isEqualTo(LaunchState.complete);
        });

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId1).getState()).isEqualTo(LaunchState.unknown);
        });

		String launchId3 = taskLauncher().launch(request);

		assertThat(launchId3).isNotEqualTo(launchId1);
		assertThat(launchId3).isNotEqualTo(launchId2);

		timeout = deploymentTimeout();

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId3).getState()).isEqualTo(LaunchState.complete);
        });

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId1).getState()).isEqualTo(LaunchState.unknown);
        });

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId2).getState()).isEqualTo(LaunchState.unknown);
        });

		taskLauncher().destroy(definition.getName());
	}

	private void basicLaunchAndValidation(AppDefinition definition, Map<String, String> deploymentProperties) {
		List<String> commandLineArgs = new ArrayList<>(1);
		// Test to ensure no issues parsing server.port command line arg.
		commandLineArgs.add(LocalTaskLauncher.SERVER_PORT_KEY_COMMAND_LINE_ARG + SocketUtils.findAvailableTcpPort(LocalTaskLauncher.DEFAULT_SERVER_PORT));

		AppDeploymentRequest request = new AppDeploymentRequest(definition, this.testApplication(), deploymentProperties, commandLineArgs);


		this.log.info("Launching {}...", request.getDefinition().getName());

		String launchId = this.taskLauncher().launch(request);
		Timeout timeout = this.deploymentTimeout();
		await().pollInterval(Duration.ofMillis(1))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
			assertThat(taskLauncher.getRunningTaskExecutionCount()).isEqualTo(1);
        });

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher().status(launchId).getState()).isEqualTo(LaunchState.complete);
        });

		this.taskLauncher().destroy(definition.getName());

		await().pollInterval(Duration.ofMillis(timeout.pause))
                .atMost(Duration.ofMillis(timeout.maxAttempts * timeout.pause))
                .untilAsserted(() -> {
			assertThat(taskLauncher.getRunningTaskExecutionCount()).isEqualTo(0);
        });
	}

	@Configuration
	@EnableConfigurationProperties(LocalDeployerProperties.class)
	public static class Config {

		@Bean
		public TaskLauncher taskLauncher(LocalDeployerProperties properties) {
			return new LocalTaskLauncher(properties);
		}
	}

}
