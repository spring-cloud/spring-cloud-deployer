/*
 * Copyright 2020-2021 the original author or authors.
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.organizations.Organizations;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v2.spaces.Spaces;
import org.cloudfoundry.client.v3.Pagination;
import org.cloudfoundry.client.v3.tasks.ListTasksResponse;
import org.cloudfoundry.client.v3.tasks.TaskResource;
import org.cloudfoundry.client.v3.tasks.TaskState;
import org.cloudfoundry.client.v3.tasks.Tasks;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class CloudFoundryTaskLauncherCachingTests {

	@Test
	public void testOrgSpaceCachingRetries() {
		CloudFoundryClient client = mock(CloudFoundryClient.class);
		AtomicBoolean spaceError = new AtomicBoolean(true);
		AtomicBoolean orgError = new AtomicBoolean(true);

		Spaces spaces = mock(Spaces.class);
		given(client.spaces()).willReturn(spaces);
		given(spaces.list(any())).willReturn(listSpacesResponse(spaceError));

		Organizations organizations = mock(Organizations.class);
		given(client.organizations()).willReturn(organizations);
		given(organizations.list(any())).willReturn(listOrganizationsResponse(orgError));

		Tasks tasks = mock(Tasks.class);
		given(client.tasks()).willReturn(tasks);
		given(tasks.list(any())).willReturn(runningTasksResponse());

		ApplicationContext applicationContext = mock(ApplicationContext.class);

		CloudFoundryDeploymentProperties deploymentProperties = new CloudFoundryDeploymentProperties();
		CloudFoundryOperations operations = mock(CloudFoundryOperations.class);
		RuntimeEnvironmentInfo runtimeEnvironmentInfo = mock(RuntimeEnvironmentInfo.class);
		Map<String, String> orgAndSpace = new HashMap<>();
		orgAndSpace.put(CloudFoundryPlatformSpecificInfo.ORG, "this-org");
		orgAndSpace.put(CloudFoundryPlatformSpecificInfo.SPACE, "this-space");
		given(runtimeEnvironmentInfo.getPlatformSpecificInfo()).willReturn(orgAndSpace);

		CloudFoundryTaskLauncher launcher = new CloudFoundryTaskLauncher(client, deploymentProperties, operations,
				runtimeEnvironmentInfo, applicationContext);

		Throwable thrown1 = catchThrowable(() -> {
			launcher.getRunningTaskExecutionCount();
		});
		assertThat(thrown1).isInstanceOf(RuntimeException.class).hasNoCause();

		// space should still error
		orgError.set(false);
		Throwable thrown2 = catchThrowable(() -> {
			launcher.getRunningTaskExecutionCount();
		});
		assertThat(thrown2).isInstanceOf(RuntimeException.class).hasNoCause();

		// cache should now be getting cleared as space doesn't error
		spaceError.set(false);
		Throwable thrown3 = catchThrowable(() -> {
			launcher.getRunningTaskExecutionCount();
		});
		assertThat(thrown3).doesNotThrowAnyException();
		assertThat(launcher.getRunningTaskExecutionCount()).isEqualTo(1);
	}

	private Mono<ListOrganizationsResponse> listOrganizationsResponse(AtomicBoolean error) {
		// defer so that we can conditionally throw within mono
		return Mono.defer(() -> {
			if (error.get()) {
				throw new RuntimeException();
			}
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
				.addAllResources(Collections.<OrganizationResource>singletonList(
					OrganizationResource.builder()
							.metadata(Metadata.builder().id("123").build()).build())
				)
				.build();
			return Mono.just(response);
		});
	}

	private Mono<ListSpacesResponse> listSpacesResponse(AtomicBoolean error) {
		// defer so that we can conditionally throw within mono
		return Mono.defer(() -> {
			if (error.get()) {
				throw new RuntimeException();
			}
			ListSpacesResponse response = ListSpacesResponse.builder()
				.addAllResources(Collections.<SpaceResource>singletonList(
					SpaceResource.builder()
							.metadata(Metadata.builder().id("123").build()).build())
			)
			.build();
			return Mono.just(response);
		});
	}

	private Mono<ListTasksResponse> runningTasksResponse() {
		List<TaskResource> taskResources = new ArrayList<>();
		for (int i = 0; i < 1; i++) {
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
}
