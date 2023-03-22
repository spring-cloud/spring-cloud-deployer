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

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.SummaryApplicationResponse;
import org.cloudfoundry.client.v3.tasks.CreateTaskRequest;
import org.cloudfoundry.client.v3.tasks.CreateTaskResponse;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.AbstractApplicationSummary;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationHealthCheck;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.Docker;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.LogsRequest;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.applications.StopApplicationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;

/**
 * {@link TaskLauncher} implementation for CloudFoundry.  When a task is launched, if it has not previously been
 * deployed, the app is created, the package is uploaded, and the droplet is created before launching the actual
 * task.  If the app has been deployed previously, the app/package/droplet is reused and a new task is created.
 *
 * @author Greg Turnquist
 * @author Michael Minella
 * @author Ben Hale
 * @author Ilayaperumal Gopinathan
 * @author Glenn Renfro
 * @author David Turanski
 * @author David Bernard
 */
public class CloudFoundryTaskLauncher extends AbstractCloudFoundryTaskLauncher {

	private static final Logger logger = LoggerFactory.getLogger(CloudFoundryTaskLauncher.class);

	private final CloudFoundryClient client;

	private final CloudFoundryDeploymentProperties deploymentProperties;

	private final CloudFoundryOperations operations;

	public CloudFoundryTaskLauncher(CloudFoundryClient client,
												CloudFoundryDeploymentProperties deploymentProperties,
												CloudFoundryOperations operations,
											    RuntimeEnvironmentInfo runtimeEnvironmentInfo) {
		super(client, deploymentProperties, runtimeEnvironmentInfo);
		this.client = client;
		this.deploymentProperties = deploymentProperties;
		this.operations = operations;
	}

	/**
	 * Set up a reactor flow to launch a task. Before launch, check if the base application exists. If not, deploy
	 * then launch task.
	 *
	 * @param appDeploymentRequest description of the application to be launched
	 * @return name of the launched task, returned without waiting for reactor pipeline to complete
	 */
	@Override
	public String launch(AppDeploymentRequest appDeploymentRequest) {
		final AppDeploymentRequest request = CfEnvAwareAppDeploymentRequest.of(appDeploymentRequest);
		if (this.maxConcurrentExecutionsReached()) {
			throw new IllegalStateException(
				String.format("Cannot launch task %s. The maximum concurrent task executions is at its limit [%d].",
					request.getDefinition().getName(), this.getMaximumConcurrentTasks())
			);
		}

		return getOrDeployApplication(request)
			.flatMap(application -> launchTask(application, request))
			.doOnSuccess(r -> {
				logger.info("Task {} launch successful", request.getDefinition().getName());
			})
			.doOnError(logError(String.format("Task %s launch failed", request.getDefinition().getName())))
			.doOnTerminate(() -> {
				if (pushTaskAppsEnabled()) {
					deleteLocalApplicationResourceFile(request);
				}
			})
			.block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
	}

	@Override
	public void destroy(String appName) {
		if (!pushTaskAppsEnabled()) {
			logger.warn("The application {} will not be deleted since 'pushTaskApps' is not enabled.", appName);

			return;
		}
		requestDeleteApplication(appName)
			.timeout(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()))
			.doOnSuccess(v -> logger.info("Successfully destroyed app {}", appName))
			.doOnError(logError(String.format("Failed to destroy app %s", appName)))
			.block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
	}

	@Override
	public String getLog(String taskAppName) {
		List<LogMessage> logMessageList = getLogMessage(taskAppName).collectList().block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
		StringBuilder stringBuilder = new StringBuilder();
		for (LogMessage logMessage: logMessageList) {
			stringBuilder.append(logMessage.getMessage() + System.lineSeparator());
		}
		return stringBuilder.toString();
	}

	private Flux<LogMessage> getLogMessage(String taskAppName) {
		logger.info("Fetching log for {}", taskAppName);
		return this.operations.applications().logs(LogsRequest.builder().name(taskAppName).recent(true).build());
	}

	/**
	 * Set up a reactor flow to stage a task. Before staging check if the base
	 * application exists. If not, then stage it.
	 *
	 * @param request description of the application to be staged.
	 * @return SummaryApplicationResponse containing the status of the staging.
	 */
	public SummaryApplicationResponse stage(AppDeploymentRequest request) {
		return getOrDeployApplication(request).doOnSuccess(r ->
					logger.info("Task {} staged successfully", request.getDefinition().getName()))
				.doOnError(logError(String.format("Task %s stage failed", request.getDefinition().getName())))
				.block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
	}

	/**
	 * Creates the command string required to launch a task by a service on Cloud Foundry.
	 * @param application the {@link SummaryApplicationResponse} containing the result of the requested staging.
	 * @param request The {@link AppDeploymentRequest} associated with the task staging.
	 * @return the command string
	 */
	public String getCommand(SummaryApplicationResponse application, AppDeploymentRequest request) {
		final boolean appHasCfEnv = hasCfEnv(request.getResource());
		return Stream.concat(Stream.of(application.getDetectedStartCommand()), request.getCommandlineArguments().stream())
				.map( arg-> {
					int indexOfEquals = arg.indexOf("=");
					if(indexOfEquals > -1) {
						String key = arg.substring(0, indexOfEquals);
						String value = arg.substring(indexOfEquals);
						key = escapeChar(key, "(");
						key = escapeChar(key, ")");
						arg = key + value;
					}
					return appHasCfEnv ? CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg(arg) : arg;
				})
				.collect(Collectors.joining(" "));
	}

	private String escapeChar(String value, String character) {
		if(value.contains(character)) {
			value = value.replace(character, "\\\\\\" + character);
		}
		return value;
	}

	private boolean pushTaskAppsEnabled() {
		return deploymentProperties.isPushTaskAppsEnabled();
	}

	private Mono<AbstractApplicationSummary> deployApplication(AppDeploymentRequest request) {

		String name = request.getDefinition().getName();

		return pushApplication(name, request)
			.then(requestStopApplication(name))
			.then(requestGetApplication(name))
			.cast(AbstractApplicationSummary.class);
	}


	private Mono<AbstractApplicationSummary> getOptionalApplication(AppDeploymentRequest request) {
		String name = request.getDefinition().getName();

		Flux<ApplicationSummary> applications = requestListApplications()
				.filter(application -> name.equals(application.getName()));

		if (!pushTaskAppsEnabled()) {
			return applications
					.single()
					.onErrorMap(t->
						t instanceof NoSuchElementException ?
								new IllegalStateException(String.format("Application %s does not exist", name)) : t)
					.cast(AbstractApplicationSummary.class);
		}

		return applications
				.singleOrEmpty()
				.cast(AbstractApplicationSummary.class);
	}

	private Mono<SummaryApplicationResponse> getOrDeployApplication(AppDeploymentRequest request) {
		return getOptionalApplication(request)
			.switchIfEmpty(deployApplication(request))
			.flatMap(application -> requestGetApplicationSummary(application.getId()));
	}

	private Mono<String> launchTask(SummaryApplicationResponse application, AppDeploymentRequest request) {
		return requestCreateTask(application.getId(),
				getCommand(application, request),
				memory(request),
				diskQuota(request),
				request.getDefinition().getName())
			.map(CreateTaskResponse::getId);
	}

	private Mono<Void> pushApplication(String name, AppDeploymentRequest request) {
		if (!pushTaskAppsEnabled()) {
			return Mono.empty();
		}

		return requestPushApplication(PushApplicationManifestRequest.builder()
			.manifest(ApplicationManifest.builder()
				.path(getApplication(request))
				.docker(Docker.builder().image(getDockerImage(request)).build())
				.buildpacks(buildpacks(request))
				.command("echo '*** First run of container to allow droplet creation.***' && sleep 300")
				.disk(diskQuota(request))
				.environmentVariables(mergeEnvironmentVariables(name, request))
				.healthCheckType(ApplicationHealthCheck.NONE)
				.memory(memory(request))
				.name(name)
				.noRoute(true)
				.services(servicesToBind(request))
				.build())
			.stagingTimeout(this.deploymentProperties.getStagingTimeout())
			.startupTimeout(this.deploymentProperties.getStartupTimeout())
			.build());
	}

	private Mono<CreateTaskResponse> requestCreateTask(String applicationId, String command, int memory, int disk, String name) {
		return this.client.tasks()
			.create(CreateTaskRequest.builder()
				.applicationId(applicationId)
				.command(command)
				.memoryInMb(memory)
				.diskInMb(disk)
				.name(name)
				.build());
	}

	private Mono<Void> requestDeleteApplication(String name) {
		return this.operations.applications()
			.delete(DeleteApplicationRequest.builder()
				.deleteRoutes(deploymentProperties.isDeleteRoutes())
				.name(name)
				.build());
	}

	private Mono<ApplicationDetail> requestGetApplication(String name) {
		return this.operations.applications()
			.get(GetApplicationRequest.builder()
				.name(name)
				.build());
	}

	private Mono<SummaryApplicationResponse> requestGetApplicationSummary(String applicationId) {
		return this.client.applicationsV2()
			.summary(org.cloudfoundry.client.v2.applications.SummaryApplicationRequest.builder()
				.applicationId(applicationId)
				.build());
	}

	private Flux<ApplicationSummary> requestListApplications() {
		return this.operations.applications()
			.list();
	}

	private Mono<Void> requestPushApplication(PushApplicationManifestRequest request) {
		return this.operations.applications()
			.pushManifest(request);
	}

	private Mono<Void> requestStopApplication(String name) {
		return this.operations.applications()
			.stop(StopApplicationRequest.builder()
				.name(name)
				.build());
	}

}
