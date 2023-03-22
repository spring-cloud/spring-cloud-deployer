/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.scheduler.cloudfoundry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.scheduler.SchedulerClient;
import io.pivotal.scheduler.v1.Pagination;
import io.pivotal.scheduler.v1.calls.Calls;
import io.pivotal.scheduler.v1.jobs.CreateJobRequest;
import io.pivotal.scheduler.v1.jobs.CreateJobResponse;
import io.pivotal.scheduler.v1.jobs.DeleteJobRequest;
import io.pivotal.scheduler.v1.jobs.DeleteJobScheduleRequest;
import io.pivotal.scheduler.v1.jobs.ExecuteJobRequest;
import io.pivotal.scheduler.v1.jobs.ExecuteJobResponse;
import io.pivotal.scheduler.v1.jobs.GetJobRequest;
import io.pivotal.scheduler.v1.jobs.GetJobResponse;
import io.pivotal.scheduler.v1.jobs.Job;
import io.pivotal.scheduler.v1.jobs.JobSchedule;
import io.pivotal.scheduler.v1.jobs.Jobs;
import io.pivotal.scheduler.v1.jobs.ListJobHistoriesRequest;
import io.pivotal.scheduler.v1.jobs.ListJobHistoriesResponse;
import io.pivotal.scheduler.v1.jobs.ListJobScheduleHistoriesRequest;
import io.pivotal.scheduler.v1.jobs.ListJobScheduleHistoriesResponse;
import io.pivotal.scheduler.v1.jobs.ListJobSchedulesRequest;
import io.pivotal.scheduler.v1.jobs.ListJobSchedulesResponse;
import io.pivotal.scheduler.v1.jobs.ListJobsRequest;
import io.pivotal.scheduler.v1.jobs.ListJobsResponse;
import io.pivotal.scheduler.v1.jobs.ScheduleJobRequest;
import io.pivotal.scheduler.v1.jobs.ScheduleJobResponse;
import io.pivotal.scheduler.v1.schedules.ExpressionType;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v3.applications.ApplicationsV3;
import org.cloudfoundry.client.v3.tasks.Tasks;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.Applications;
import org.cloudfoundry.operations.spaces.SpaceSummary;
import org.cloudfoundry.operations.spaces.Spaces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryConnectionProperties;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryDeploymentProperties;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryTaskLauncher;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerException;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Test the core features of the Spring Cloud Scheduler implementation.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
public class CloudFoundryAppSchedulerTests {

	public static final String DEFAULT_CRON_EXPRESSION = "0/5 * ? * *";

	public static final String CRON_EXPRESSION_FOR_SIX_MIN = "0/6 * ? * *";

	public static final String BAD_CRON_EXPRESSION = "FOOBAD";

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Applications applications;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private CloudFoundryOperations operations;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Spaces spaces;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private ApplicationsV3 applicationsV3;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private CloudFoundryClient cloudFoundryClient;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private Tasks tasks;

	@Mock(answer = Answers.RETURNS_SMART_NULLS)
	private CloudFoundryTaskLauncher taskLauncher;

	private CloudFoundryAppScheduler deprecatedCloudFoundryAppScheduler;

	private CloudFoundryAppScheduler deprecatedNoServiceCloudFoundryAppScheduler;

	private CloudFoundryAppScheduler cloudFoundryAppScheduler;

	private CloudFoundryAppScheduler noServiceCloudFoundryAppScheduler;

	private SchedulerClient client;

	private SchedulerClient noServiceClient;

	private CloudFoundryConnectionProperties properties = new CloudFoundryConnectionProperties();

	private CloudFoundrySchedulerProperties schedulerProperties = new CloudFoundrySchedulerProperties();

	private CloudFoundryDeploymentProperties deploymentProperties = new CloudFoundryDeploymentProperties();

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		given(this.cloudFoundryClient.applicationsV3()).willReturn(this.applicationsV3);
		given(this.cloudFoundryClient.tasks()).willReturn(this.tasks);
		given(this.spaces.list()).willReturn(getTestSpaces());

		this.properties.setSpace("test-space");

		given(this.operations.applications()).willReturn(this.applications);
		given(this.operations.spaces()).willReturn(this.spaces);

		this.client = new TestSchedulerClient();
		this.noServiceClient = new NoServiceTestSchedulerClient();

		this.deprecatedCloudFoundryAppScheduler = new CloudFoundryAppScheduler(this.client, this.operations,
				this.properties, taskLauncher, schedulerProperties);
		this.deprecatedNoServiceCloudFoundryAppScheduler = new CloudFoundryAppScheduler(this.noServiceClient, this.operations,
				this.properties, taskLauncher, schedulerProperties);
		this.cloudFoundryAppScheduler = new CloudFoundryAppScheduler(this.client, this.operations,
				this.properties, taskLauncher, deploymentProperties);
		this.noServiceCloudFoundryAppScheduler = new CloudFoundryAppScheduler(this.noServiceClient, this.operations,
				this.properties, taskLauncher, deploymentProperties);

	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testEmptySchedulerProperties(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");
		AppDefinition definition = new AppDefinition("bar", null);
		ScheduleRequest request = (isDeprecated) ? new ScheduleRequest(definition, null, null, null, "testschedule", resource)
		: new ScheduleRequest(definition, null, (List<String>) null, "testschedule", resource);
		assertThatThrownBy(() -> {
			getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		}).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testCreateNoCommandLineArgs(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		ScheduleRequest request = (isDeprecated) ? new ScheduleRequest(definition, getDefaultScheduleProperties(),null, null, "test-schedule", resource)
				: new ScheduleRequest(definition, getDefaultDeploymentProperties(), (List<String>) null, "test-schedule", resource);

		getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse().getId()).isEqualTo("test-job-id-1");
		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse().getApplicationId()).isEqualTo("test-application-id-1");
		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse().getCommand()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testInvalidCron(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		Map<String, String> badCronMap = new HashMap<>();
		badCronMap.put(SchedulerPropertyKeys.CRON_EXPRESSION, BAD_CRON_EXPRESSION);

		ScheduleRequest request = (isDeprecated) ? new ScheduleRequest(definition,
				Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, BAD_CRON_EXPRESSION), null, null, "test-schedule", resource)
		: new ScheduleRequest(definition, Collections.singletonMap(CloudFoundryAppScheduler.CRON_EXPRESSION_KEY, BAD_CRON_EXPRESSION),
				(List<String>) null, "test-schedule", resource);

		assertThatThrownBy(() -> {
			getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		}).isInstanceOf(CreateScheduleException.class).hasMessageContaining(
				"Illegal characters for this position: 'FOO'");
		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse()).isNull();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testNameTooLong(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		Map<String, String> cronMap = new HashMap<>();
		cronMap.put(SchedulerPropertyKeys.CRON_EXPRESSION, DEFAULT_CRON_EXPRESSION);

		ScheduleRequest request = new ScheduleRequest(definition, cronMap, (List<String>) null,
				"j1-scdf-itcouldbesaidthatthisislongtoowaytoo-oopsitcouldbesaidthatthisis" +
						"longtoowaytoo-oopsitcouldbesaidthatthisislongtoowaytoo-oopsitcouldbe" +
						"saidthatthisislongtoowaytoo-oopsitcouldbesaidthatthisislongtoowaytoo-" +
						"oopsitcouldbesaidthatthisislongtoowaytoo-oops12", resource);

		assertThatThrownBy(() -> {
			getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		}).isInstanceOf(CreateScheduleException.class).hasMessageContaining(
			"Schedule can not be created because its name " +
			"'j1-scdf-itcouldbesaidthatthisislongtoowaytoo-oopsitcouldbesaidthatthisis" +
			"longtoowaytoo-oopsitcouldbesaidthatthisislongtoowaytoo-oopsitcouldbe" +
			"saidthatthisislongtoowaytoo-oopsitcouldbesaidthatthisislongtoowaytoo-" +
			"oopsitcouldbesaidthatthisislongtoowaytoo-oops12' has too many characters.  " +
			"Schedule name length must be 255 characters or less");

		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse()).isNull();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testSuccessJobCreateFailedSchedule(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		ScheduleRequest request = (isDeprecated) ? new ScheduleRequest(definition,
				Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, CRON_EXPRESSION_FOR_SIX_MIN), null, null, "test-schedule", resource) :
				new ScheduleRequest(definition, Collections.singletonMap(CloudFoundryAppScheduler.CRON_EXPRESSION_KEY, CRON_EXPRESSION_FOR_SIX_MIN),
						(List<String>) null, "test-schedule", resource);

		assertThatThrownBy(() -> {
			getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		}).isInstanceOf(CreateScheduleException.class);

		assertThat(((TestJobs) this.client.jobs()).getCreateJobResponse()).isNull();
	}


	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testCreateWithCommandLineArgs(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		ScheduleRequest request = isDeprecated ? new ScheduleRequest(definition,
				getDefaultScheduleProperties(), null,
				Collections.singletonList("TestArg"), "test-schedule", resource) :
				new ScheduleRequest(definition,
						getDefaultDeploymentProperties(),
						Collections.singletonList("TestArg"), "test-schedule", resource);
		getCloudFoundryAppScheduler(isDeprecated).schedule(request);
		ArgumentCaptor<AppDeploymentRequest> argumentCaptor = ArgumentCaptor.forClass(AppDeploymentRequest.class);
		verify(this.taskLauncher).stage(argumentCaptor.capture());
		assertThat(argumentCaptor.getValue().getCommandlineArguments().get(0)).isEqualTo("TestArg");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testList(boolean isDeprecated) {
		setupMockResults();
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list();
		assertThat(result.size()).isEqualTo(2);
		verifyScheduleInfo(result.get(0), "test-application-1", "test-job-name-1", DEFAULT_CRON_EXPRESSION);
		verifyScheduleInfo(result.get(1), "test-application-2", "test-job-name-2", DEFAULT_CRON_EXPRESSION);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testListWithJobsNoAssociatedSchedule(boolean isDeprecated) {
		setupMockResultsNoScheduleForJobs();
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list();
		assertThat(result.size()).isEqualTo(2);
		verifyScheduleInfo(result.get(0), "test-application-1", "test-job-name-1", null);
		verifyScheduleInfo(result.get(1), "test-application-2", "test-job-name-2", null);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testListWithNoSchedules(boolean isDeprecated) {
		given(this.operations.applications()
				.list())
				.willReturn(Flux.empty());
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list();
		assertThat(result.size()).isEqualTo(0);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testListSchedulesWithAppName(boolean isDeprecated) {
		setupMockResults();
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list("test-application-2");
		assertThat(result.size()).isEqualTo(1);
		verifyScheduleInfo(result.get(0), "test-application-2", "test-job-name-2", DEFAULT_CRON_EXPRESSION);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testListSchedulesWithInvalidAppName(boolean isDeprecated) {
		setupMockResults();
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list("not-here");
		assertThat(result.size()).isEqualTo(0);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testUnschedule(boolean isDeprecated) {
		setupMockResults();
		List<ScheduleInfo> result = getCloudFoundryAppScheduler(isDeprecated).list();
		assertThat(result.size()).isEqualTo(2);
		getCloudFoundryAppScheduler(isDeprecated).unschedule("test-job-name-1");
		result = getCloudFoundryAppScheduler(isDeprecated).list();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get(0).getScheduleName()).isEqualTo("test-job-name-2");
		assertThat(result.get(0).getTaskDefinitionName()).isEqualTo("test-application-2");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testMissingScheduleDelete(boolean isDeprecated) {
		boolean exceptionFired = false;
		setupMockResults();
		try {
			getCloudFoundryAppScheduler(isDeprecated).unschedule("test-job-name-3");
		}
		catch (SchedulerException se) {
			assertThat(se.getMessage()).isEqualTo("Failed to unschedule schedule test-job-name-3 does not exist.");
			exceptionFired = true;
		}
		assertThat(exceptionFired).isTrue();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testNoServiceList(boolean isDeprecated) {
		assertThatThrownBy(() -> {
			getNoServiceCloudFoundryAppScheduler(isDeprecated).list();
		}).isInstanceOf(SchedulerException.class).hasMessageContaining(
				"Scheduler Service returned a null response.");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testNoServiceListSchedulesWithAppName(boolean isDeprecated) {
		assertThatThrownBy(() -> {
			getNoServiceCloudFoundryAppScheduler(isDeprecated).list("test-application-2");
		}).isInstanceOf(SchedulerException.class).hasMessageContaining(
				"Scheduler Service returned a null response.");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testNoServiceCreate(boolean isDeprecated) {
		Resource resource = new FileSystemResource("src/test/resources/demo-0.0.1-SNAPSHOT.jar");

		mockAppResultsInAppList();
		AppDefinition definition = new AppDefinition("test-application-1", null);
		ScheduleRequest request = (isDeprecated) ? new ScheduleRequest(definition, getDefaultScheduleProperties(), null, null, "test-schedule", resource) :
		new ScheduleRequest(definition, getDefaultDeploymentProperties(), (List<String>) null, "test-schedule", resource);

		assertThatThrownBy(() -> {
			getNoServiceCloudFoundryAppScheduler(isDeprecated).schedule(request);
		}).isInstanceOf(SchedulerException.class).hasMessageContaining(
				"Scheduler Service returned a null response.");
	}

	private void givenRequestListApplications(Flux<ApplicationSummary> response) {
		given(this.operations.applications()
				.list())
				.willReturn(response);
	}

	private void verifyScheduleInfo(ScheduleInfo scheduleInfo, String taskDefinitionName, String scheduleName, String expression) {
		assertThat(scheduleInfo.getTaskDefinitionName()).isEqualTo(taskDefinitionName);
		assertThat(scheduleInfo.getScheduleName()).isEqualTo(scheduleName);
		if (expression != null) {
			assertThat(scheduleInfo.getScheduleProperties().size()).isEqualTo(1);
			assertThat(scheduleInfo.getScheduleProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION)).isEqualTo(expression);
		}
		else {
			assertThat(scheduleInfo.getScheduleProperties().size()).isEqualTo(0);
		}
	}

	private static class TestSchedulerClient implements SchedulerClient {
		private Jobs jobs;

		public TestSchedulerClient() {
			jobs = new TestJobs();
		}

		@Override
		public Calls calls() {
			return null;
		}

		@Override
		public Jobs jobs() {
			return jobs;
		}
	}

	private static class NoServiceTestSchedulerClient implements SchedulerClient {
		private Jobs jobs;

		public NoServiceTestSchedulerClient() {
			jobs = new NoServiceTestJobs();
		}

		@Override
		public Calls calls() {
			return null;
		}

		@Override
		public Jobs jobs() {
			return jobs;
		}
	}

	private static class NoServiceTestJobs extends TestJobs {
		@Override
		public Mono<ListJobsResponse> list(ListJobsRequest request) {
			return Mono.justOrEmpty(null);
		}

		@Override
		public Mono<CreateJobResponse> create(CreateJobRequest request) {
			return Mono.justOrEmpty(null);
		}
	}

	private static class TestJobs implements Jobs {
		private CreateJobResponse createJobResponse;

		private List<Job> jobResources = new ArrayList<>();

		private List<JobSchedule> jobScheduleResources = new ArrayList<>();

		@Override
		public Mono<CreateJobResponse> create(CreateJobRequest request) {
			this.createJobResponse = CreateJobResponse.builder()
					.applicationId(request.getApplicationId())
					.name(request.getName())
					.id("test-job-id-1")
					.command(request.getCommand())
					.build();
			this.jobResources.add(Job.builder().applicationId(request.getApplicationId())
					.command(request.getCommand())
					.id("test-job-1")
					.name(request.getName())
					.build());
			return Mono.just(createJobResponse);
		}

		@Override
		public Mono<Void> delete(DeleteJobRequest request) {
			for (int i = 0; i < this.jobResources.size(); i++) {
				if (this.jobResources.get(i).getId().equals(request.getJobId())) {
					jobResources.remove(i);
					break;
				}
			}
			return Mono.justOrEmpty(null);
		}

		@Override
		public Mono<Void> deleteSchedule(DeleteJobScheduleRequest request) {
			return null;
		}

		@Override
		public Mono<ExecuteJobResponse> execute(ExecuteJobRequest request) {
			return null;
		}

		@Override
		public Mono<GetJobResponse> get(GetJobRequest request) {
			return null;
		}

		@Override
		public Mono<ListJobsResponse> list(ListJobsRequest request) {
			ListJobsResponse response = ListJobsResponse.builder()
					.addAllResources(jobResources)
					.pagination(Pagination.builder().totalPages(1).build())
					.build();
			return Mono.just(response);
		}

		@Override
		public Mono<ListJobHistoriesResponse> listHistories(ListJobHistoriesRequest request) {
			return null;
		}

		@Override
		public Mono<ListJobScheduleHistoriesResponse> listScheduleHistories(ListJobScheduleHistoriesRequest request) {
			return null;
		}

		@Override
		public Mono<ListJobSchedulesResponse> listSchedules(ListJobSchedulesRequest request) {

			ListJobSchedulesResponse response = ListJobSchedulesResponse.builder()
					.addAllResources(jobScheduleResources.stream().filter(jobScheduleResource -> jobScheduleResource.getJobId().equals(request.getJobId())).collect(Collectors.toList()))
					.build();
			return Mono.just(response);
		}

		@Override
		public Mono<ScheduleJobResponse> schedule(ScheduleJobRequest request) {
			if(request.getExpression().equals(CRON_EXPRESSION_FOR_SIX_MIN)) {
				throw new IllegalStateException();
			}
			return Mono.just(ScheduleJobResponse.builder().expression(request.getExpression())
					.expressionType(request.getExpressionType())
					.enabled(true)
					.jobId(request.getJobId())
					.id("schedule-1234")
					.build());
		}

		public CreateJobResponse getCreateJobResponse() {
			if(this.jobResources.size() == 0) {
				this.createJobResponse = null;
			}
			return createJobResponse;
		}
	}

	private Flux<SpaceSummary> getTestSpaces() {
		return Flux.just(SpaceSummary.builder().id("test-space-1")
				.name("test-space")
				.build());
	}

	private void setupMockResults() {
		mockJobsInJobList();
		mockAppResultsInAppList();
	}

	private void setupMockResultsNoScheduleForJobs() {
		mockJobsInJobListNoSchedule();
		mockAppResultsInAppList();
	}

	private void mockAppResultsInAppList() {
		givenRequestListApplications(Flux.just(ApplicationSummary.builder()
						.diskQuota(0)
						.id("test-application-id-1")
						.instances(1)
						.memoryLimit(0)
						.name("test-application-1")
						.requestedState("RUNNING")
						.runningInstances(1)
						.build(),
				ApplicationSummary.builder()
						.diskQuota(0)
						.id("test-application-id-2")
						.instances(1)
						.memoryLimit(0)
						.name("test-application-2")
						.requestedState("RUNNING")
						.runningInstances(1)
						.build()));
	}

	private void mockJobsInJobListNoSchedule() {
		TestJobs localJobs = (TestJobs) client.jobs();
		localJobs.jobResources.add(Job.builder().applicationId("test-application-id-1")
				.command("test-command")
				.id("test-job-1")
				.name("test-job-name-1")
				.build());
		localJobs.jobResources.add(Job.builder().applicationId("test-application-id-2")
				.command("test-command")
				.id("test-job-2")
				.name("test-job-name-2")
				.build());
	}

	private void mockJobsInJobList() {
		TestJobs localJobs = (TestJobs) client.jobs();
		localJobs.jobResources.add(Job.builder().applicationId("test-application-id-1")
				.command("test-command")
				.id("test-job-1")
				.name("test-job-name-1")
				.jobSchedules(createJobScheduleList("test-job-1", "test-schedule-1"))
				.build());
		localJobs.jobResources.add(Job.builder().applicationId("test-application-id-2")
				.command("test-command")
				.id("test-job-2")
				.name("test-job-name-2")
				.jobSchedules(createJobScheduleList("test-job-2", "test-schedule-2"))
				.build());
	}

	private List<JobSchedule> createJobScheduleList(String jobId, String scheduleId) {
		List<JobSchedule> jobSchedules = new ArrayList<>();
		jobSchedules.add(JobSchedule.builder()
				.enabled(true)
				.expression(DEFAULT_CRON_EXPRESSION)
				.expressionType(ExpressionType.CRON)
				.id(scheduleId)
				.jobId(jobId)
				.build());
		return jobSchedules;
	}

	private Map<String, String> getDefaultScheduleProperties() {
		Map<String, String> result = new HashMap<>();
		result.put(SchedulerPropertyKeys.CRON_EXPRESSION, DEFAULT_CRON_EXPRESSION);
		return result;
	}

	private Map<String, String> getDefaultDeploymentProperties() {
		Map<String, String> result = new HashMap<>();
		result.put(CloudFoundryAppScheduler.CRON_EXPRESSION_KEY, DEFAULT_CRON_EXPRESSION);
		return result;
	}

	private CloudFoundryAppScheduler getCloudFoundryAppScheduler(boolean isDeprecated) {
		return isDeprecated ? this.deprecatedCloudFoundryAppScheduler : this.cloudFoundryAppScheduler;
	}

	private CloudFoundryAppScheduler getNoServiceCloudFoundryAppScheduler(boolean isDeprecated) {
		return isDeprecated ? this.deprecatedNoServiceCloudFoundryAppScheduler : this.noServiceCloudFoundryAppScheduler;
	}
}
