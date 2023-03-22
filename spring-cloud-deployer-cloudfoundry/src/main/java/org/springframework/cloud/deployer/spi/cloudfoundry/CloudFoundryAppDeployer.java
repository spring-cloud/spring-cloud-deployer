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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationHealthCheck;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.Docker;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.cloudfoundry.operations.applications.LogsRequest;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.applications.Route;
import org.cloudfoundry.operations.applications.ScaleApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import reactor.cache.CacheMono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.retry.Retry;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.app.MultiStateAppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.util.StringUtils;

/**
 * A deployer that targets Cloud Foundry using the public API.
 *
 * @author Eric Bottard
 * @author Greg Turnquist
 * @author Ben Hale
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
public class CloudFoundryAppDeployer extends AbstractCloudFoundryDeployer implements MultiStateAppDeployer {

	private static final Logger logger = LoggerFactory.getLogger(CloudFoundryAppDeployer.class);

	private final AppNameGenerator applicationNameGenerator;

	private final CloudFoundryOperations operations;

	private final Cache<String, ApplicationDetail> cache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS)
			.build();

	public CloudFoundryAppDeployer(AppNameGenerator applicationNameGenerator,
		CloudFoundryDeploymentProperties deploymentProperties,
		CloudFoundryOperations operations,
		RuntimeEnvironmentInfo runtimeEnvironmentInfo
	) {
		super(deploymentProperties, runtimeEnvironmentInfo);
		this.operations = operations;
		this.applicationNameGenerator = applicationNameGenerator;
	}

	@Override
	public String deploy(AppDeploymentRequest appDeploymentRequest) {
		final AppDeploymentRequest request = CfEnvAwareAppDeploymentRequest.of(appDeploymentRequest);
		logger.trace("Entered deploy: Deploying AppDeploymentRequest: AppDefinition = {}, Resource = {}, Deployment Properties = {}",
			request.getDefinition(), request.getResource(), request.getDeploymentProperties());
		String deploymentId = deploymentId(request);

		logger.trace("deploy: Getting Status for Deployment Id = {}", deploymentId);
		getStatus(deploymentId)
			.doOnNext(status -> assertApplicationDoesNotExist(deploymentId, status))
			// Need to block here to be able to throw exception early
			.block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));

		logger.trace("deploy: Pushing application");
		pushApplication(deploymentId, request)
			.timeout(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()))
			.doOnSuccess(item -> {
				logger.info("Successfully deployed {}", deploymentId);
			})
			.doOnError(error -> {
				if (isNotFoundError().test(error)) {
					logger.warn("Unable to deploy application. It may have been destroyed before start completed: " + error.getMessage());
				}
				else {
					logError(String.format("Failed to deploy %s", deploymentId)).accept(error);
				}
			})
			.doOnSuccessOrError((r, e) -> {
				deleteLocalApplicationResourceFile(request);
			})
			.subscribe();

		logger.trace("Exiting deploy().  Deployment Id = {}", deploymentId);
		return deploymentId;
	}

	@Override
	public Map<String, DeploymentState> states(String... ids) {
		return requestSummary()
			.collect(Collectors.toMap(ApplicationSummary::getName, this::mapShallowAppState))
			.block();
	}

	@Override
	public Mono<Map<String, DeploymentState>> statesReactive(String... ids) {
		return requestSummary()
			.collect(Collectors.toMap(ApplicationSummary::getName, this::mapShallowAppState));
	}

	private DeploymentState mapShallowAppState(ApplicationSummary applicationSummary) {
		if (applicationSummary.getRunningInstances().equals(applicationSummary.getInstances())) {
			return DeploymentState.deployed;
		}
		else if (applicationSummary.getInstances() > 0) {
			return DeploymentState.partial;
		} else {
			return DeploymentState.undeployed;
		}
	}

	@Override
	protected Map<String, String> mergeEnvironmentVariables(String deploymentId, AppDeploymentRequest request) {
		Map<String, String> envVariables = super.mergeEnvironmentVariables(deploymentId, request);
		envVariables.putAll(getCommandLineArguments(request));
		String group = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
		if (StringUtils.hasText(group)) {
			envVariables.put("SPRING_CLOUD_APPLICATION_GROUP", group);
		}
		envVariables.put("SPRING_CLOUD_APPLICATION_GUID", "${vcap.application.name}:${vcap.application.instance_index}");
		envVariables.put("SPRING_APPLICATION_INDEX", "${vcap.application.instance_index}");
		this.deploymentProperties.getAppAdmin().addCredentialsToAppEnvironment(envVariables);
		return envVariables;
	}

	private Map<String, String> getCommandLineArguments(AppDeploymentRequest request) {
		if (request.getCommandlineArguments().isEmpty()) {
			return Collections.emptyMap();
		}

		String argumentsAsString = request.getCommandlineArguments().stream()
				.collect(Collectors.joining(" "));
		String yaml = new Yaml().dump(Collections.singletonMap("arguments", argumentsAsString));

		return Collections.singletonMap("JBP_CONFIG_JAVA_MAIN", yaml);
	}

	@Override
	public AppStatus status(String id) {
		try {
			return getStatus(id)
				.doOnSuccess(v -> logger.info("Successfully computed status [{}] for {}", v, id))
				.doOnError(logError(String.format("Failed to compute status for %s", id)))
				.block(Duration.ofMillis(this.deploymentProperties.getStatusTimeout()));
		}
		catch (Exception timeoutDueToBlock) {
			logger.error("Caught exception while querying for status of {}", id, timeoutDueToBlock);
			return createErrorAppStatus(id);
		}
	}

	@Override
	public Mono<AppStatus> statusReactive(String id) {
		return getStatus(id);
	}

	@Override
	public void undeploy(String id) {
		getStatus(id)
			.doOnNext(status -> assertApplicationExists(id, status))
				// Need to block here to be able to throw exception early
			.block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
		requestDeleteApplication(id)
			.timeout(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()))
			.doOnSuccess(v -> logger.info("Successfully undeployed app {}", id))
			.doOnError(logError(String.format("Failed to undeploy app %s", id)))
			.subscribe();
	}

	@Override
	public String getLog(String id) {
		List<LogMessage> logMessageList = getLogMessage(id).collectList().block(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()));
		StringBuilder stringBuilder = new StringBuilder();
		for (LogMessage logMessage: logMessageList) {
			stringBuilder.append(logMessage.getMessage() + System.lineSeparator());
		}
		return stringBuilder.toString();
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		logger.info("Scaling the application instance using ", appScaleRequest.toString());
		ScaleApplicationRequest scaleApplicationRequest = ScaleApplicationRequest.builder()
				.name(appScaleRequest.getDeploymentId())
				.instances(appScaleRequest.getCount())
				.memoryLimit(memory(appScaleRequest))
				.diskLimit(diskQuota(appScaleRequest))
				.stagingTimeout(this.deploymentProperties.getStagingTimeout())
				.startupTimeout(this.deploymentProperties.getStartupTimeout())
				.build();
		this.operations.applications().scale(scaleApplicationRequest)
				.timeout(Duration.ofSeconds(this.deploymentProperties.getApiTimeout()))
				.doOnSuccess(v -> logger.info("Scaled the application with deploymentId = {}",
						appScaleRequest.getDeploymentId()))
				.doOnError(e -> logger.error("Error: {} scaling the app instance {}", e.getMessage(),
						appScaleRequest.getDeploymentId()))
				.subscribe();
	}

	private Flux<LogMessage> getLogMessage(String deploymentId) {
		logger.info("Fetching log for "+ deploymentId);
		return this.operations.applications().logs(LogsRequest.builder().name(deploymentId).recent(true).build());
	}

	private void assertApplicationDoesNotExist(String deploymentId, AppStatus status) {
		DeploymentState state = status.getState();
		if (state != DeploymentState.unknown && state != DeploymentState.error) {
			throw new IllegalStateException(String.format("App %s is already deployed with state %s", deploymentId, state));
		}
	}

	private void assertApplicationExists(String deploymentId, AppStatus status) {
		DeploymentState state = status.getState();
		if (state == DeploymentState.unknown) {
			throw new IllegalStateException(String.format("App %s is not in a deployed state", deploymentId));
		}
	}

	private AppStatus createAppStatus(ApplicationDetail applicationDetail, String deploymentId) {
		logger.trace("Gathering instances for " + applicationDetail);
		logger.trace("InstanceDetails: " + applicationDetail.getInstanceDetails());

		AppStatus.Builder builder = AppStatus.of(deploymentId);

		int i = 0;
		for (InstanceDetail instanceDetail : applicationDetail.getInstanceDetails()) {
			builder.with(new CloudFoundryAppInstanceStatus(applicationDetail, instanceDetail, i++));
		}
		for (; i < applicationDetail.getInstances(); i++) {
			builder.with(new CloudFoundryAppInstanceStatus(applicationDetail, null, i));
		}

		return builder.build();
	}

	private AppStatus createEmptyAppStatus(String deploymentId) {
		return AppStatus.of(deploymentId)
			.build();
	}

	private AppStatus createErrorAppStatus(String deploymentId) {
		return AppStatus.of(deploymentId)
			.generalState(DeploymentState.error)
			.build();
	}

	private String deploymentId(AppDeploymentRequest request) {
		String prefix = Optional.ofNullable(request.getDeploymentProperties().get(GROUP_PROPERTY_KEY))
			.map(group -> String.format("%s-", group))
			.orElse("");

		String appName = String.format("%s%s", prefix, request.getDefinition().getName());

		return this.applicationNameGenerator.generateAppName(appName);
	}

	private String domain(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.DOMAIN_PROPERTY))
				.orElse(this.deploymentProperties.getDomain());
	}


	private Mono<AppStatus> getStatus(String deploymentId) {
		return requestGetApplication(deploymentId)
			.map(applicationDetail -> createAppStatus(applicationDetail, deploymentId))
			.onErrorResume(IllegalArgumentException.class, t -> {
				logger.debug("Application for {} does not exist.", deploymentId);
				return Mono.just(createEmptyAppStatus(deploymentId));
			})
			.transform(statusRetry(deploymentId))
			.onErrorReturn(createErrorAppStatus(deploymentId));
	}

	private ApplicationHealthCheck healthCheck(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties()
						.get(CloudFoundryDeploymentProperties.HEALTHCHECK_PROPERTY_KEY))
				.map(this::toApplicationHealthCheck).orElse(this.deploymentProperties.getHealthCheck());
	}

	private String healthCheckEndpoint(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties()
						.get(CloudFoundryDeploymentProperties.HEALTHCHECK_HTTP_ENDPOINT_PROPERTY_KEY))
				.orElse(this.deploymentProperties.getHealthCheckHttpEndpoint());
	}

	private Integer healthCheckTimeout(AppDeploymentRequest request) {
		String timeoutString = request.getDeploymentProperties().getOrDefault(
				CloudFoundryDeploymentProperties.HEALTHCHECK_TIMEOUT_PROPERTY_KEY,
				this.deploymentProperties.getHealthCheckTimeout());
		return Integer.parseInt(timeoutString);
	}

	private String host(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.HOST_PROPERTY))
				.orElse(this.deploymentProperties.getHost());
	}

	private int instances(AppDeploymentRequest request) {
		return Optional.ofNullable(request.getDeploymentProperties().get(AppDeployer.COUNT_PROPERTY_KEY))
			.map(Integer::parseInt)
			.orElse(this.deploymentProperties.getInstances());
	}

	private Mono<Void> pushApplication(String deploymentId, AppDeploymentRequest request) {
		ApplicationManifest.Builder manifest = ApplicationManifest.builder()
			.path(getApplication(request)) // Only one of the two is non-null
			.disk(diskQuota(request))
			.environmentVariables(mergeEnvironmentVariables(deploymentId, request))
			.healthCheckType(healthCheck(request))
			.healthCheckHttpEndpoint(healthCheckEndpoint(request))
			.timeout(healthCheckTimeout(request))
			.instances(instances(request))
			.memory(memory(request))
			.name(deploymentId)
			.noRoute(toggleNoRoute(request))
			.services(servicesToBind(request));

		Optional.ofNullable(host(request)).ifPresent(manifest::host);
		Optional.ofNullable(domain(request)).ifPresent(manifest::domain);
		Optional.ofNullable(routePath(request)).ifPresent(manifest::routePath);
		if (route(request) != null) {
			manifest.route(Route.builder().route(route(request)).build());
		}
		if (! routes(request).isEmpty()){
		    Set<Route> routes = routes(request).stream()
                    .map(r -> Route.builder().route(r).build())
                    .collect(Collectors.toSet());
		    manifest.routes(routes);
        }
		if(getDockerImage(request) != null){
			logger.info("Preparing to run a container from  " + request.getResource()
					+ ". This may take some time if the image must be downloaded from a remote container registry.");
			manifest.docker(Docker.builder().image(getDockerImage(request)).build());
		} else {
			manifest.buildpacks(buildpacks(request));
		}

		if (!includesServiceParameters(request)) {
			return pushApplicationWithNoServiceParameters(manifest.build(), deploymentId);
		} else {
			return pushApplicationWithServiceParameters(manifest.build(), request, deploymentId);
		}
	}

	private Mono<Void> pushApplicationWithNoServiceParameters(ApplicationManifest manifest, String deploymentId) {
		logger.debug("Pushing application manifest");
		return requestPushApplication(PushApplicationManifestRequest.builder()
			.manifest(manifest)
			.stagingTimeout(this.deploymentProperties.getStagingTimeout())
			.startupTimeout(this.deploymentProperties.getStartupTimeout())
			.build())
			.doOnSuccess(v -> logger.info("Done uploading bits for {}", deploymentId))
			.doOnError(e -> logger.error("Error: {} creating app {}", e.getMessage(), deploymentId));
	}

	private Mono<Void> requestBind(BindServiceInstanceRequest bindRequest) {
		// defer so that new reques gets created when retry does re-sub
		return Mono.defer(() -> this.operations.services().bind(bindRequest))
			.doOnError(e -> {
				if (e instanceof ClientV2Exception) {
					ClientV2Exception ce = (ClientV2Exception) e;
					if (ce.getCode() == 10001) {
						logger.warn("Retry service bind due to concurrency error");
					}
					else {
						logger.warn("Received ClientV2Exception error from cf", ce);
					}
				}
				else {
					logger.warn("Received error from cf", e);
				}
			})
			// check expected code indicating concurrency error, aka
			// CF-ConcurrencyError(10001): The service broker could not perform this operation in parallel with other running operations
			// and retry those and let other errors to pass and fail fast
			.retryWhen(Retry.withThrowable(reactor.retry.Retry.onlyIf(c -> {
					if (c.exception() instanceof ClientV2Exception) {
						ClientV2Exception e = (ClientV2Exception) c.exception();
						if (e.getCode() == 10001) {
							return true;
						}
					}
					return false;
				})
				// for now try 30 seconds and do some jitter to limit concurrency issues
				.timeout(Duration.ofSeconds(30))
				.randomBackoff(Duration.ofSeconds(1), Duration.ofSeconds(5))
				.doOnRetry(c -> logger.debug("Retrying cf call for {}", bindRequest))
			));
	}

	private Mono<Void> pushApplicationWithServiceParameters(ApplicationManifest manifest,
		AppDeploymentRequest request, String deploymentId) {

		logger.debug("Pushing application manifest with no start");

		return requestPushApplication(PushApplicationManifestRequest.builder()
			.manifest(manifest)
			.noStart(true)
			.build())
			.doOnSuccess(v -> logger.info("Done uploading bits for {}", deploymentId))
			.doOnError(e -> logger.error(String.format("Error creating app %s.  Exception Message %s", deploymentId, e.getMessage())))

			.thenMany(Flux.fromStream(bindParameterizedServiceInstanceRequests(request, deploymentId)))
			.flatMap(bindRequest -> this.requestBind(bindRequest)
				.doOnSuccess(bv -> logger.info("Done binding service {} for {}", bindRequest.getServiceInstanceName(), deploymentId))
				.doOnError(e -> logger.error("Error: {} binding service {}", e.getMessage(), bindRequest.getServiceInstanceName())))

			.then(this.operations.applications()
				.start(StartApplicationRequest.builder()
					.name(deploymentId)
					.stagingTimeout(this.deploymentProperties.getStagingTimeout())
					.startupTimeout(this.deploymentProperties.getStartupTimeout())
					.build())
				.doOnSuccess(sv -> logger.info("Started app for {} ", deploymentId))
				.doOnError(e -> logger.error("Error: {} starting app for {}.", e.getMessage(), deploymentId)))

			.doOnError(e -> logger.error(String.format("Error: %s creating app %s", e.getMessage(), deploymentId), e));
	}

	private Mono<Void> requestDeleteApplication(String id) {
		return this.operations.applications()
			.delete(DeleteApplicationRequest.builder()
				.deleteRoutes(deploymentProperties.isDeleteRoutes())
				.name(id)
				.build());
	}

	private Mono<ApplicationDetail> requestGetApplication(String id) {
		return CacheMono
			.lookup(k -> Mono.defer(() -> {
				ApplicationDetail ifPresent = cache.getIfPresent(id);
				logger.debug("Cache get {}", ifPresent);
				return Mono.justOrEmpty(ifPresent).map(Signal::next);
			}), id)
			.onCacheMissResume(Mono.defer(() -> {
				logger.debug("Cache miss {}", id);
				return getApplicationDetail(id);
			}))
			.andWriteWith((k, sig) -> Mono.fromRunnable(() -> {
				ApplicationDetail ap = sig.get();
				if (ap != null) {
					logger.debug("Cache put {} {}", k, ap);
					cache.put(k, ap);
				}
			}));
	}

	private Mono<ApplicationDetail> getApplicationDetail(String id) {
		return this.operations.applications()
			.get(GetApplicationRequest.builder()
				.name(id)
				.build());
	}

	private Mono<Void> requestPushApplication(PushApplicationManifestRequest request) {
		return this.operations.applications()
			.pushManifest(request);
	}

	private Flux<ApplicationSummary> requestSummary() {
		return this.operations.applications().list();
	}

	private String routePath(AppDeploymentRequest request) {
		String routePath = request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.ROUTE_PATH_PROPERTY);
		if (StringUtils.hasText(routePath) && !routePath.startsWith("/")) {
			throw new IllegalArgumentException(
					"Cloud Foundry routes must start with \"/\". Route passed = [" + routePath + "].");
		}
		return routePath;
	}

	private String route(AppDeploymentRequest request) {
		return request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.ROUTE_PROPERTY);
	}

    private Set<String> routes(AppDeploymentRequest request) {
        Set<String> routes = new HashSet<>();
        routes.addAll(this.deploymentProperties.getRoutes());
		routes.addAll(StringUtils.commaDelimitedListToSet(
				request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.ROUTES_PROPERTY)));
        return routes;
    }

	private ApplicationHealthCheck toApplicationHealthCheck(String raw) {
		try {
			return ApplicationHealthCheck.from(raw);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(String.format("Unsupported health-check value '%s'. Available values are %s", raw,
				StringUtils.arrayToCommaDelimitedString(ApplicationHealthCheck.values())), e);
		}
	}

	private Boolean toggleNoRoute(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.NO_ROUTE_PROPERTY))
				.map(Boolean::valueOf).orElse(null);
	}
}
