/*
 * Copyright 2016-2023 the original author or authors.
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.jsonwebtoken.lang.Assert;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v3.tasks.CancelTaskRequest;
import org.cloudfoundry.client.v3.tasks.CancelTaskResponse;
import org.cloudfoundry.client.v3.tasks.GetTaskRequest;
import org.cloudfoundry.client.v3.tasks.GetTaskResponse;
import org.cloudfoundry.client.v3.tasks.ListTasksRequest;
import org.cloudfoundry.client.v3.tasks.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * Abstract class to provide base functionality for launching Tasks on Cloud Foundry. This
 * class provides the base SPI for the {@link CloudFoundryTaskLauncher}.
 *
 * Does not override the default no-op implementation for
 * {@link TaskLauncher#cleanup(String)} and {@link TaskLauncher#destroy(String)}.
 */
abstract class AbstractCloudFoundryTaskLauncher extends AbstractCloudFoundryDeployer implements TaskLauncher {

	private static final Logger logger = LoggerFactory.getLogger(AbstractCloudFoundryTaskLauncher.class);

	private final CloudFoundryClient client;

	private final Mono<String> organizationId;

	private final Mono<String> spaceId;

	AbstractCloudFoundryTaskLauncher(CloudFoundryClient client,
			CloudFoundryDeploymentProperties deploymentProperties,
			RuntimeEnvironmentInfo runtimeEnvironmentInfo) {
		super(deploymentProperties, runtimeEnvironmentInfo);
		this.client = client;
		organizationId = organizationId();
		spaceId = spaceId();
	}

	/**
	 * Setup a reactor flow to cancel a running task. This implementation opts to be
	 * asynchronous.
	 *
	 * @param id the task's id to be canceled as returned from the
	 *     {@link TaskLauncher#launch(AppDeploymentRequest)}
	 */
	@Override
	public void cancel(String id) {
		requestCancelTask(id)
				.timeout(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()))
				.doOnSuccess(r -> logger.info("Task {} cancellation successful", id))
				.doOnError(logError(String.format("Task %s cancellation failed", id)))
				.subscribe();
	}

	/**
	 * Lookup the current status based on task id.
	 *
	 * @param id taskId as returned from the {@link TaskLauncher#launch(AppDeploymentRequest)}
	 * @return the current task status
	 */
	@Override
	public TaskStatus status(String id) {
		try {
			return getStatus(id)
					.doOnSuccess(v -> logger.info("Successfully computed status [{}] for id={}", v, id))
					.doOnError(logError(String.format("Failed to compute status for %s", id)))
					.block(Duration.ofMillis(this.deploymentProperties.getStatusTimeout()));
		}
		catch (Exception timeoutDueToBlock) {
			logger.error("Caught exception while querying for status of id={}", id, timeoutDueToBlock);
			return createErrorTaskStatus(id);
		}
	}

	@Override
	public int getRunningTaskExecutionCount() {

		Mono<Tuple2<String,String>> orgAndSpace = Mono.zip(organizationId, spaceId);

		Mono<ListTasksRequest> listTasksRequest = orgAndSpace.map(tuple->
				ListTasksRequest.builder()
				.state(TaskState.RUNNING)
				.organizationId(tuple.getT1())
				.spaceId(tuple.getT2())
				.build());

		return listTasksRequest.flatMap(request-> this.client.tasks().list(request))
				.map(listTasksResponse -> listTasksResponse.getPagination().getTotalResults())
				.doOnError(logError("Failed to list running tasks"))
				.doOnSuccess(count -> logger.info(String.format("There are %d running tasks", count)))
				.block(Duration.ofMillis(this.deploymentProperties.getStatusTimeout()));
	}

	@Override
	public int getMaximumConcurrentTasks() {
		return this.deploymentProperties.getMaximumConcurrentTasks();
	}

	protected boolean maxConcurrentExecutionsReached() {
		return this.getRunningTaskExecutionCount() >= this.getMaximumConcurrentTasks();
	}

	private Mono<TaskStatus> getStatus(String id) {
		return requestGetTask(id)
				.map(this::toTaskStatus)
				.onErrorResume(isNotFoundError(), t -> {
					logger.debug("Task for id={} does not exist", id);
					return Mono.just(new TaskStatus(id, LaunchState.unknown, null));
				})
				.transform(statusRetry(id))
				.onErrorReturn(createErrorTaskStatus(id));
	}

	private TaskStatus createErrorTaskStatus(String id) {
		return new TaskStatus(id, LaunchState.error, null);
	}

	protected TaskStatus toTaskStatus(GetTaskResponse response) {
		Map<String, String> attributes = new HashMap<>();
		//retrieve CF-GUID for retrieving logs
		Assert.notNull(response.getTaskRelationships(), "response must contain task relationships.");
		Assert.notNull(response.getTaskRelationships().getApp(), "app in the taskRelationships of the response must not be null");
		Assert.notNull(response.getTaskRelationships().getApp().getData(), "data in the app of the task relationships within the response must not be null");
		attributes.put("app-cf-guid", response.getTaskRelationships().getApp().getData().getId());
		switch (response.getState()) {
		case SUCCEEDED:
			return new TaskStatus(response.getId(), LaunchState.complete, attributes);
		case RUNNING:
			return new TaskStatus(response.getId(), LaunchState.running, attributes);
		case PENDING:
			return new TaskStatus(response.getId(), LaunchState.launching, attributes);
		case CANCELING:
			return new TaskStatus(response.getId(), LaunchState.cancelled, attributes);
		case FAILED:
			return new TaskStatus(response.getId(), LaunchState.failed, attributes);
		default:
			throw new IllegalStateException(String.format("Unsupported CF task state %s", response.getState()));
		}
	}

	private Mono<CancelTaskResponse> requestCancelTask(String taskId) {
		return this.client.tasks()
				.cancel(CancelTaskRequest.builder()
						.taskId(taskId)
						.build());
	}

	private Mono<GetTaskResponse> requestGetTask(String taskId) {
		return this.client.tasks()
				.get(GetTaskRequest.builder()
						.taskId(taskId)
						.build());
	}

	private Mono<String> organizationId() {
		String org = this.runtimeEnvironmentInfo.getPlatformSpecificInfo().get(CloudFoundryPlatformSpecificInfo.ORG);
		Assert.hasText(org,"Missing runtimeEnvironmentInfo : 'org' required.");
		ListOrganizationsRequest listOrganizationsRequest =  ListOrganizationsRequest.builder()
				.name(org).build();
		return this.client.organizations().list(listOrganizationsRequest)
				.doOnError(logError("Failed to list organizations"))
				.map(listOrganizationsResponse -> listOrganizationsResponse.getResources().get(0).getMetadata().getId())
				.cache(aValue -> Duration.ofMillis(Long.MAX_VALUE), aValue -> Duration.ZERO, () -> Duration.ZERO);
	}

	private Mono<String> spaceId() {
		String space = this.runtimeEnvironmentInfo.getPlatformSpecificInfo().get(CloudFoundryPlatformSpecificInfo.SPACE);
		Assert.hasText(space,"Missing runtimeEnvironmentInfo : 'space' required.");
		ListSpacesRequest listSpacesRequest = ListSpacesRequest.builder()
				.name(space).build();
		return this.client.spaces().list(listSpacesRequest)
				.doOnError(logError("Failed to list spaces"))
				.map(listSpacesResponse -> listSpacesResponse.getResources().get(0).getMetadata().getId())
				.cache(aValue -> Duration.ofMillis(Long.MAX_VALUE), aValue -> Duration.ZERO, () -> Duration.ZERO);
	}

	@Override
	public void cleanup(String id) {

	}

	@Override
	public void destroy(String appName) {

	}
}
