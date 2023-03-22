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

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationsV2;
import org.cloudfoundry.client.v2.applications.SummaryApplicationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.organizations.Organizations;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v2.spaces.Spaces;
import org.cloudfoundry.client.v3.Pagination;
import org.cloudfoundry.client.v3.tasks.CancelTaskRequest;
import org.cloudfoundry.client.v3.tasks.CancelTaskResponse;
import org.cloudfoundry.client.v3.tasks.CreateTaskRequest;
import org.cloudfoundry.client.v3.tasks.CreateTaskResponse;
import org.cloudfoundry.client.v3.tasks.GetTaskRequest;
import org.cloudfoundry.client.v3.tasks.GetTaskResponse;
import org.cloudfoundry.client.v3.tasks.ListTasksResponse;
import org.cloudfoundry.client.v3.tasks.TaskResource;
import org.cloudfoundry.client.v3.tasks.TaskState;
import org.cloudfoundry.client.v3.tasks.Tasks;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationHealthCheck;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.Applications;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.applications.StopApplicationRequest;
import org.cloudfoundry.operations.services.Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Michael Minella
 * @author Ben Hale
 * @author Glenn Renfro
 * @author David Turanski
 * @author David Bernard
 */
public class CloudFoundryTaskLauncherTests {
	private final static int TASK_EXECUTION_COUNT = 10;

	private final CloudFoundryDeploymentProperties deploymentProperties = new CloudFoundryDeploymentProperties();

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Applications applications;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private ApplicationsV2 applicationsV2;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private CloudFoundryClient client;

	private CloudFoundryTaskLauncher launcher;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private CloudFoundryOperations operations;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Services services;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Spaces spaces;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Organizations organizations;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Tasks tasks;

	private Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		given(this.tasks.list(any())).willReturn(this.runningTasksResponse());
		given(this.client.applicationsV2()).willReturn(this.applicationsV2);
		given(this.client.tasks()).willReturn(this.tasks);

		given(this.operations.applications()).willReturn(this.applications);
		given(this.operations.services()).willReturn(this.services);
		given(this.client.spaces()).willReturn(this.spaces);
		given(this.client.organizations()).willReturn(this.organizations);

		RuntimeEnvironmentInfo runtimeEnvironmentInfo = mock(RuntimeEnvironmentInfo.class);
		Map<String, String> orgAndSpace = new HashMap<>();
		orgAndSpace.put(CloudFoundryPlatformSpecificInfo.ORG, "this-org");
		orgAndSpace.put(CloudFoundryPlatformSpecificInfo.SPACE, "this-space");
		given(runtimeEnvironmentInfo.getPlatformSpecificInfo()).willReturn(orgAndSpace);
		given(this.organizations.list(any())).willReturn(listOrganizationsResponse());
		given(this.spaces.list(any())).willReturn(listSpacesResponse());

		this.deploymentProperties.setApiTimeout(1);
		this.deploymentProperties.setStatusTimeout(1_250L);
		this.launcher = new CloudFoundryTaskLauncher(this.client,
				this.deploymentProperties,
				this.operations,
				runtimeEnvironmentInfo);

	}

	@Test
	public void cancel() {
		givenRequestCancelTask("test-task-id", Mono.just(CancelTaskResponse.builder()
			.id("test-task-id")
			.memoryInMb(1024)
			.diskInMb(1024)
			.dropletId("1")
			.createdAt(new Date().toString())
			.updatedAt(new Date().toString())
			.sequenceId(1)
			.name("test-task-id")
			.state(TaskState.CANCELING)
			.build()));

		this.launcher.cancel("test-task-id");
	}

	@Test
	public void currentExecutionCount() {
		assertThat(this.launcher.getRunningTaskExecutionCount()).isEqualTo(this.TASK_EXECUTION_COUNT);
	}

	@Test
	public void launchTaskApplicationExists() {
		setupExistingAppSuccessful();
		String taskId = this.launcher.launch(defaultRequest());

		assertThat(taskId).isEqualTo("test-task-id");
	}

	@Test
	public void stageTaskApplicationExists() {
		setupExistingAppSuccessful();
		SummaryApplicationResponse response = this.launcher.stage(defaultRequest());

		assertThat(response.getId()).isEqualTo("test-application-id");
		assertThat(response.getDetectedStartCommand()).isEqualTo("test-command");
	}

	@Test
	public void launchTaskWithNonExistentApplication() throws IOException {
		setupTaskWithNonExistentApplication(this.resource);
		String taskId = this.launcher.launch(defaultRequest());
		assertThat(taskId).isEqualTo("test-task-id");
	}

	@Test
	public void launchExistingTaskApplicationWithPushDisabled() {
		setupExistingAppSuccessful();
		deploymentProperties.setPushTaskAppsEnabled(false);
		String taskId = this.launcher.launch(defaultRequest());
		assertThat(taskId).isEqualTo("test-task-id");
	}

	@Test
	public void launchNonExistingTaskApplicationWithPushDisabled() throws IOException {
		setupTaskWithNonExistentApplication(this.resource);
		deploymentProperties.setPushTaskAppsEnabled(false);
		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplication() throws IOException {
		setupTaskWithNonExistentApplication(this.resource);

		SummaryApplicationResponse response = this.launcher.stage(defaultRequest());
		assertThat(response.getId()).isEqualTo("test-application-id");
		assertThat(response.getDetectedStartCommand()).isEqualTo("test-command");
	}

	@Test
	public void automaticallyConfigureForCfEnv() throws JsonProcessingException {
		Resource resource = new FileSystemResource("src/test/resources/log-sink-rabbit-3.0.0.BUILD-SNAPSHOT.jar");
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(new AppDefinition("test-application",
					Collections.emptyMap()), resource, Collections.emptyMap());

		Map<String, String> env =  launcher.mergeEnvironmentVariables("test-application-id", appDeploymentRequest);
		// MatcherAssert.assertThat(env, hasEntry(CfEnvConfigurer.JBP_CONFIG_SPRING_AUTO_RECONFIGURATION, CfEnvConfigurer.ENABLED_FALSE));
		// MatcherAssert.assertThat(env, hasKey(CoreMatchers.equalTo("SPRING_APPLICATION_JSON")));
		// ObjectMapper objectMapper = new ObjectMapper();
		// Map<String, String> saj = objectMapper.readValue(env.get("SPRING_APPLICATION_JSON"),HashMap.class);
		// MatcherAssert.assertThat(saj, hasEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, CfEnvConfigurer.CLOUD_PROFILE_NAME));
		assertThat(env).containsEntry(CfEnvConfigurer.JBP_CONFIG_SPRING_AUTO_RECONFIGURATION, CfEnvConfigurer.ENABLED_FALSE);
		assertThat(env).containsKeys("SPRING_APPLICATION_JSON");
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, String> saj = objectMapper.readValue(env.get("SPRING_APPLICATION_JSON"), HashMap.class);
		assertThat(saj).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, CfEnvConfigurer.CLOUD_PROFILE_NAME);
	}


	@Test
	public void launchTaskWithNonExistentApplicationAndApplicationListingFails() {
		givenRequestListApplications(Flux.error(new UnsupportedOperationException()));

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplicationAndApplicationListingFails() {
		givenRequestListApplications(Flux.error(new UnsupportedOperationException()));

		assertThatThrownBy(() -> {
			this.launcher.stage(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void launchTaskWithNonExistentApplicationAndPushFails() throws IOException {
		setupFailedPush(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplicationAndPushFails() throws IOException {
		setupFailedPush(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.stage(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void launchTaskWithNonExistentApplicationAndRetrievingApplicationSummaryFails() throws IOException {
		setupTaskWithNonExistentApplicationAndRetrievingApplicationSummaryFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplicationAndRetrievingApplicationSummaryFails() throws IOException {
		setupTaskWithNonExistentApplicationAndRetrievingApplicationSummaryFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.stage(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void launchTaskWithNonExistentApplicationAndStoppingApplicationFails() throws IOException {
		setupTaskWithNonExistentApplicationAndStoppingApplicationFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplicationAndStoppingApplicationFails() throws IOException {
		setupTaskWithNonExistentApplicationAndStoppingApplicationFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.stage(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void launchTaskWithNonExistentApplicationAndTaskCreationFails() throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
			.manifest(ApplicationManifest.builder()
				.path(this.resource.getFile().toPath())
				.buildpack(deploymentProperties.getBuildpack())
				.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
				.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
				.environmentVariable("SPRING_APPLICATION_JSON", "{}")
				.healthCheckType(ApplicationHealthCheck.NONE)
				.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
				.name("test-application")
				.noRoute(true)
				.services(Collections.emptySet())
				.build())
			.stagingTimeout(this.deploymentProperties.getStagingTimeout())
			.startupTimeout(this.deploymentProperties.getStartupTimeout())
			.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
			.diskQuota(0)
			.id("test-application-id")
			.instances(1)
			.memoryLimit(0)
			.name("test-application")
			.requestedState("RUNNING")
			.runningInstances(1)
			.stack("test-stack")
			.build()));

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplicationSummary("test-application-id",
				Mono.just(SummaryApplicationResponse.builder()
			.id("test-application-id")
			.detectedStartCommand("test-command")
			.build()));

		givenRequestCreateTask("test-application-id",
				"test-command",
				this.deploymentProperties.getMemory(),
				this.deploymentProperties.getDisk(),
				"test-application",
				Mono.error(new UnsupportedOperationException()));

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void launchTaskWithNonExistentApplicationBindingOneService() throws IOException {
		setupTaskWithNonExistentApplicationBindingOneService(this.resource);
		AppDeploymentRequest request = deploymentRequest(this.resource,
			Collections.singletonMap(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY, "test-service-instance-2"));

		String taskId = this.launcher.launch(request);
		assertThat(taskId).isEqualTo("test-task-id");
	}

	@Test
	public void stageTaskWithNonExistentApplicationBindingOneService() throws IOException {
		setupTaskWithNonExistentApplicationBindingOneService(this.resource);
		AppDeploymentRequest request = deploymentRequest(this.resource,
				Collections.singletonMap(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY, "test-service-instance-2"));

		SummaryApplicationResponse response = this.launcher.stage(request);
		assertThat(response.getId()).isEqualTo("test-application-id");
		assertThat(response.getDetectedStartCommand()).isEqualTo("test-command");
	}

	@Test
	public void launchTaskWithNonExistentApplicationBindingThreeServices() throws IOException {
		setupTaskWithNonExistentApplicationBindingThreeServices(this.resource);
		AppDeploymentRequest request = deploymentRequest(this.resource,
			Collections.singletonMap(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY,
					"test-service-instance-1,test-service-instance-2,test-service-instance-3"));

		String taskId = this.launcher.launch(request);

		assertThat(taskId).isEqualTo("test-task-id");
	}
	@Test
	public void stageTaskWithNonExistentApplicationBindingThreeServices() throws IOException {
		setupTaskWithNonExistentApplicationBindingThreeServices(this.resource);
		AppDeploymentRequest request = deploymentRequest(this.resource,
				Collections.singletonMap(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY,
						"test-service-instance-1,test-service-instance-2,test-service-instance-3"));

		SummaryApplicationResponse response = this.launcher.stage(request);
		assertThat(response.getId()).isEqualTo("test-application-id");
		assertThat(response.getDetectedStartCommand()).isEqualTo("test-command");
	}

	@Test
	public void launchTaskWithNonExistentApplicationRetrievalFails() throws IOException {
		setupTaskWithNonExistentApplicationRetrievalFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.launch(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void stageTaskWithNonExistentApplicationRetrievalFails() throws IOException {
		setupTaskWithNonExistentApplicationRetrievalFails(this.resource);

		assertThatThrownBy(() -> {
			this.launcher.stage(defaultRequest());
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void status() {
		givenRequestGetTask("test-task-id", Mono.just(GetTaskResponse.builder()
			.id("test-task-id")
				.memoryInMb(1024)
				.diskInMb(1024)
				.dropletId("1")
				.createdAt(new Date().toString())
				.updatedAt(new Date().toString())
				.sequenceId(1)
				.name("test-task-id")
			.state(TaskState.SUCCEEDED)
			.build()));

		TaskStatus status = this.launcher.status("test-task-id");

		assertThat(status.getState()).isEqualTo(LaunchState.complete);
	}

	@Test
	public void testStatusTimeout() {
		// Delay twice as much as 40% of statusTimeout, which is what the deployer uses
		long delay = (long) (this.deploymentProperties.getStatusTimeout() * .4f * 2);

		givenRequestGetTask("test-task-id", Mono
			.delay(Duration.ofMillis(delay))
			.then(Mono.just(GetTaskResponse.builder()
				.id("test-task-id")
					.memoryInMb(1024)
					.diskInMb(1024)
					.dropletId("1")
					.createdAt(new Date().toString())
					.updatedAt(new Date().toString())
					.sequenceId(1)
					.name("test-task-id")
				.state(TaskState.SUCCEEDED)
				.build())));

		assertThat(this.launcher.status("test-task-id").getState()).isEqualTo(LaunchState.error);
	}

	@Test
	public void testDestroy() {
		givenRequestDeleteApplication("test-application");

		this.launcher.destroy("test-application");
	}

	@Test
	public void testCommand() {
		AppDeploymentRequest request = new AppDeploymentRequest(new AppDefinition(
				"test-app-1", null),
				this.resource,
				Collections.singletonMap("test-key-1", "test-val-1"),
				Collections.singletonList("test-command-arg-1"));
		String command = this.launcher.getCommand(SummaryApplicationResponse
				.builder()
				.detectedStartCommand("command-val")
				.build(),
				request);
		assertThat(command).isEqualTo("command-val test-command-arg-1");

		List<String> args = new ArrayList<>();
		args.add("test-command-arg-1");
		args.add("a=b");
		args.add("run.id=1");
		args.add("run.id(long)=1");
		args.add("run.id(long=1");
		args.add("run.id)=1");
		request = new AppDeploymentRequest(new AppDefinition(
				"test-app-1", null),
				this.resource,
				Collections.singletonMap("test-key-1", "test-val-1"),
				args);
		command = this.launcher.getCommand(SummaryApplicationResponse
						.builder()
						.detectedStartCommand("command-val")
						.build(),
				request);
		assertThat(command).isEqualTo("command-val test-command-arg-1 a=b run.id=1 run.id\\\\\\(long\\\\\\)=1 run.id\\\\\\(long=1 run.id\\\\\\)=1");
	}

	private void givenRequestCancelTask(String taskId, Mono<CancelTaskResponse> response) {
		given(this.client.tasks()
			.cancel(CancelTaskRequest.builder()
				.taskId(taskId)
				.build()))
			.willReturn(response);
	}

	private void givenRequestCreateTask(String applicationId,
										String command,
										String memory,
										String disk,
										String name,
										Mono<CreateTaskResponse> response) {

		given(this.client.tasks()
			.create(CreateTaskRequest.builder()
				.applicationId(applicationId)
				.command(command)
				.memoryInMb((int) ByteSizeUtils.parseToMebibytes(memory))
				.diskInMb((int) ByteSizeUtils.parseToMebibytes(disk))
				.name(name)
				.build()))
			.willReturn(response);
	}

	private void givenRequestDeleteApplication(String appName) {
		given(this.operations.applications()
			.delete(DeleteApplicationRequest.builder()
				.name(appName)
				.deleteRoutes(true)
				.build()))
			.willReturn(Mono.empty());
	}

	private void givenRequestGetApplication(String name, Mono<ApplicationDetail> response) {
		given(this.operations.applications()
			.get(GetApplicationRequest.builder()
				.name(name)
				.build()))
			.willReturn(response);
	}

	private void givenRequestGetApplicationSummary(String applicationId, Mono<SummaryApplicationResponse> response) {
		given(this.client.applicationsV2()
			.summary(org.cloudfoundry.client.v2.applications.SummaryApplicationRequest.builder()
				.applicationId(applicationId)
				.build()))
			.willReturn(response);
	}

	private void givenRequestGetTask(String taskId, Mono<GetTaskResponse> response) {
		given(this.client.tasks()
			.get(GetTaskRequest.builder()
				.taskId(taskId)
				.build()))
			.willReturn(response);
	}

	private void givenRequestListApplications(Flux<ApplicationSummary> response) {
		given(this.operations.applications()
			.list())
			.willReturn(response);
	}

	private void givenRequestPushApplication(PushApplicationManifestRequest request, Mono<Void> response) {
		given(this.operations.applications()
			.pushManifest(any(PushApplicationManifestRequest.class)))
			.willReturn(response);
	}

	private void givenRequestStopApplication(String name, Mono<Void> response) {
		given(this.operations.applications()
			.stop(StopApplicationRequest.builder()
				.name(name)
				.build()))
			.willReturn(response);
	}

	private void setupExistingAppSuccessful() {
			givenRequestListApplications(Flux.just(ApplicationSummary.builder()
					.diskQuota(0)
					.id("test-application-id")
					.instances(1)
					.memoryLimit(0)
					.name("test-application")
					.requestedState("RUNNING")
					.runningInstances(1)
					.build()));

			givenRequestGetApplicationSummary("test-application-id", Mono.just(SummaryApplicationResponse.builder()
					.id("test-application-id")
					.detectedStartCommand("test-command")
					.build()));

			givenRequestCreateTask("test-application-id",
						"test-command",
						this.deploymentProperties.getMemory(),
					this.deploymentProperties.getDisk(),
					"test-application",
						Mono.just(CreateTaskResponse.builder()
					.id("test-task-id")
					.memoryInMb(1024)
					.diskInMb(1024)
					.dropletId("1")
					.createdAt(new Date().toString())
					.updatedAt(new Date().toString())
					.sequenceId(1)
					.name("test-task-id")
					.state(TaskState.FAILED)
					.build()));
	}

	private void setupTaskWithNonExistentApplication(Resource resource) throws IOException{
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(
				PushApplicationManifestRequest.builder()
						.manifest(ApplicationManifest.builder()
								.path(resource.getFile().toPath())
								.buildpack(deploymentProperties.getBuildpack())
								.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
								.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
								.environmentVariable("SPRING_APPLICATION_JSON", "{}")
								.healthCheckType(ApplicationHealthCheck.NONE)
								.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
								.name("test-application")
								.noRoute(true)
								.services(Collections.emptySet())
								.build())
						.stagingTimeout(this.deploymentProperties.getStagingTimeout())
						.startupTimeout(this.deploymentProperties.getStartupTimeout())
						.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.build()));

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplicationSummary("test-application-id", Mono.just(SummaryApplicationResponse.builder()
				.id("test-application-id")
				.detectedStartCommand("test-command")
				.build()));

		givenRequestCreateTask("test-application-id",
					"test-command",
					this.deploymentProperties.getMemory(),
				this.deploymentProperties.getDisk(),
				"test-application",
					Mono.just(CreateTaskResponse.builder()
				.id("test-task-id")
				.memoryInMb(1024)
				.diskInMb(1024)
				.dropletId("1")
				.createdAt(new Date().toString())
				.updatedAt(new Date().toString())
				.sequenceId(1)
				.name("test-task-id")
				.state(TaskState.SUCCEEDED)
				.build()));
	}

	private void setupFailedPush(Resource resource) throws IOException{
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(
				PushApplicationManifestRequest.builder()
						.manifest(ApplicationManifest.builder()
								.path(resource.getFile().toPath())
								.buildpack(deploymentProperties.getBuildpack())
								.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
								.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
								.environmentVariable("SPRING_APPLICATION_JSON", "{}")
								.healthCheckType(ApplicationHealthCheck.NONE)
								.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
								.name("test-application")
								.noRoute(true)
								.services(Collections.emptySet())
								.build())
						.stagingTimeout(this.deploymentProperties.getStagingTimeout())
						.startupTimeout(this.deploymentProperties.getStartupTimeout())
						.build(), Mono.error(new UnsupportedOperationException()));
	}

	private void setupTaskWithNonExistentApplicationAndRetrievingApplicationSummaryFails(Resource resource) throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(
				PushApplicationManifestRequest.builder()
						.manifest(ApplicationManifest.builder()
								.path(resource.getFile().toPath())
								.buildpack(deploymentProperties.getBuildpack())
								.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
								.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
								.environmentVariable("SPRING_APPLICATION_JSON", "{}")
								.healthCheckType(ApplicationHealthCheck.NONE)
								.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
								.name("test-application")
								.noRoute(true)
								.services(Collections.emptySet())
								.build())
						.stagingTimeout(this.deploymentProperties.getStagingTimeout())
						.startupTimeout(this.deploymentProperties.getStartupTimeout())
						.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.build()));

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplicationSummary("test-application-id", Mono.error(new UnsupportedOperationException()));

	}

	private void setupTaskWithNonExistentApplicationAndStoppingApplicationFails(Resource resource) throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(
				PushApplicationManifestRequest.builder()
						.manifest(ApplicationManifest.builder()
								.path(resource.getFile().toPath())
								.buildpack(deploymentProperties.getBuildpack())
								.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
								.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
								.environmentVariable("SPRING_APPLICATION_JSON", "{}")
								.healthCheckType(ApplicationHealthCheck.NONE)
								.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
								.name("test-application")
								.noRoute(true)
								.services(Collections.emptySet())
								.build())
						.stagingTimeout(this.deploymentProperties.getStagingTimeout())
						.startupTimeout(this.deploymentProperties.getStartupTimeout())
						.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.build()));

		givenRequestStopApplication("test-application", Mono.error(new UnsupportedOperationException()));
	}


	private void setupTaskWithNonExistentApplicationBindingOneService(Resource resource) throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
						.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
						.environmentVariable("SPRING_APPLICATION_JSON", "{}")
						.healthCheckType(ApplicationHealthCheck.NONE)
						.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
						.name("test-application")
						.noRoute(true)
						.service("test-service-instance-2")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.build()));

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplicationSummary("test-application-id", Mono.just(SummaryApplicationResponse.builder()
				.id("test-application-id")

				.detectedStartCommand("test-command")
				.build()));

		givenRequestCreateTask("test-application-id",
					"test-command",
					this.deploymentProperties.getMemory(),
				this.deploymentProperties.getDisk(),
				"test-application",
					Mono.just(CreateTaskResponse.builder()
				.id("test-task-id")
				.memoryInMb(1024)
				.diskInMb(1024)
				.dropletId("1")
				.createdAt(new Date().toString())
				.updatedAt(new Date().toString())
				.sequenceId(1)
				.name("test-task-id")
				.state(TaskState.SUCCEEDED)
				.build()));
	}

	private void setupTaskWithNonExistentApplicationBindingThreeServices(Resource resource) throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
						.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
						.environmentVariable("SPRING_APPLICATION_JSON", "{}")
						.healthCheckType(ApplicationHealthCheck.NONE)
						.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
						.name("test-application")
						.noRoute(true)
						.service("test-service-instance-1")
						.service("test-service-instance-2")
						.service("test-service-instance-3")
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		givenRequestGetApplication("test-application", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.build()));

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplicationSummary("test-application-id", Mono.just(SummaryApplicationResponse.builder()
				.id("test-application-id")
				.detectedStartCommand("test-command")
				.build()));

		givenRequestCreateTask("test-application-id",
					"test-command",
					this.deploymentProperties.getMemory(),
				this.deploymentProperties.getDisk(),
				"test-application",
					Mono.just(CreateTaskResponse.builder()
				.id("test-task-id")
				.memoryInMb(1024)
				.diskInMb(1024)
				.dropletId("1")
				.createdAt(new Date().toString())
				.updatedAt(new Date().toString())
				.sequenceId(1)
				.name("test-task-id")
				.state(TaskState.SUCCEEDED)
				.build()));
	}

	public void setupTaskWithNonExistentApplicationRetrievalFails(Resource resource) throws IOException {
		givenRequestListApplications(Flux.empty());

		givenRequestPushApplication(PushApplicationManifestRequest.builder()
				.manifest(ApplicationManifest.builder()
						.path(resource.getFile().toPath())
						.buildpack(deploymentProperties.getBuildpack())
						.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
						.disk((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk()))
						.environmentVariable("SPRING_APPLICATION_JSON", "{}")
						.healthCheckType(ApplicationHealthCheck.NONE)
						.memory((int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory()))
						.name("test-application")
						.noRoute(true)
						.services(Collections.emptySet())
						.build())
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build(), Mono.empty());

		givenRequestStopApplication("test-application", Mono.empty());

		givenRequestGetApplication("test-application", Mono.error(new UnsupportedOperationException()));
	}

	private AppDeploymentRequest defaultRequest() {
		return deploymentRequest(this.resource, Collections.emptyMap());
	}
	private AppDeploymentRequest deploymentRequest(Resource resource, Map<String,String> deploymentProperties) {
		AppDefinition definition = new AppDefinition("test-application", null);
		return new AppDeploymentRequest(definition, resource, deploymentProperties);
	}

	private Mono<ListTasksResponse> runningTasksResponse() {
		List<TaskResource> taskResources = new ArrayList<>();
		for (int i=0; i< TASK_EXECUTION_COUNT; i++) {
			taskResources.add(TaskResource.builder()
				.name("task-" + i)
				.dropletId(UUID.randomUUID().toString())
				.id(UUID.randomUUID().toString())
				.diskInMb(2048)
				.sequenceId(i)
				.state(TaskState.RUNNING)
				.memoryInMb(2048)
				.createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
				.build());
		}
		ListTasksResponse listTasksResponse = ListTasksResponse.builder().resources(taskResources)
			.pagination(Pagination.builder().totalResults(taskResources.size()).build())
			.build();
		return Mono.just(listTasksResponse);
	}

	private Mono<ListOrganizationsResponse> listOrganizationsResponse() {
		ListOrganizationsResponse response = ListOrganizationsResponse.builder()
		.addAllResources(Collections.<OrganizationResource>singletonList(
				OrganizationResource.builder()
						.metadata(Metadata.builder().id("123").build()).build())
		).build();
		return Mono.just(response);
	}

	private Mono<ListSpacesResponse> listSpacesResponse() {
		ListSpacesResponse response = ListSpacesResponse.builder()
				.addAllResources(Collections.<SpaceResource>singletonList(
						SpaceResource.builder()
								.metadata(Metadata.builder().id("123").build()).build())
				).build();
		return Mono.just(response);
	}
}
