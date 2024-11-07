/*
 * Copyright 2016-2020 the original author or authors.
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.AbstractCloudFoundryException;
import org.cloudfoundry.UnknownCloudFoundryException;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.retry.Retry;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Base class dealing with configuration overrides on a per-deployment basis, as well as common code for apps and tasks.
 *
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
class AbstractCloudFoundryDeployer {

	protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	protected final RuntimeEnvironmentInfo runtimeEnvironmentInfo;

	final CloudFoundryDeploymentProperties deploymentProperties;

	private final Logger logger = LoggerFactory.getLogger(AbstractCloudFoundryDeployer.class);


	AbstractCloudFoundryDeployer(CloudFoundryDeploymentProperties deploymentProperties, RuntimeEnvironmentInfo runtimeEnvironmentInfo) {
		this.deploymentProperties = deploymentProperties;
		this.runtimeEnvironmentInfo = runtimeEnvironmentInfo;
	}

	int memory(AppDeploymentRequest request) {
		String withUnit = request.getDeploymentProperties()
			.getOrDefault(AppDeployer.MEMORY_PROPERTY_KEY, this.deploymentProperties.getMemory());
		return (int) ByteSizeUtils.parseToMebibytes(withUnit);
	}

	int memory(AppScaleRequest request) {
		if (request.getProperties().isPresent() && request.getProperties().get() != null) {
			return (int) ByteSizeUtils.parseToMebibytes(request.getProperties().get().getOrDefault(AppDeployer.MEMORY_PROPERTY_KEY,
					this.deploymentProperties.getMemory()));
		}
		return (int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getMemory());
	}

	int diskQuota(AppScaleRequest request) {
		if (request.getProperties().isPresent() && request.getProperties().get() != null) {
			return (int) ByteSizeUtils.parseToMebibytes(request.getProperties().get().getOrDefault(AppDeployer.DISK_PROPERTY_KEY,
					this.deploymentProperties.getDisk()));
		}
		return (int) ByteSizeUtils.parseToMebibytes(this.deploymentProperties.getDisk());
	}

	Set<String> servicesToBind(AppDeploymentRequest request) {

		Set<String> services = this.deploymentProperties.getServices().stream().filter(s->!ServiceParser
			.getServiceParameters(s).isPresent())
			.collect(Collectors.toSet());

		Set<String> requestServices = ServiceParser.splitServiceProperties(request.getDeploymentProperties().get
			(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY))
			.stream()
			.filter(s-> !ServiceParser.getServiceParameters(s).isPresent())
			.collect(Collectors.toSet());

		services.addAll(requestServices);
		return services;
	}

	boolean includesServiceParameters(AppDeploymentRequest request) {
		return this.deploymentProperties.getServices().stream()
				.anyMatch(s -> ServiceParser.getServiceParameters(s).isPresent())
				|| ServiceParser
						.splitServiceProperties(request.getDeploymentProperties()
								.get(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY))
						.stream().anyMatch(s -> ServiceParser.getServiceParameters(s).isPresent());
	}

	Stream<BindServiceInstanceRequest> bindParameterizedServiceInstanceRequests(AppDeploymentRequest request,
		String deploymentId) {
		return ServiceParser.splitServiceProperties(request.getDeploymentProperties().get
			(CloudFoundryDeploymentProperties.SERVICES_PROPERTY_KEY)).stream()
			.filter(s-> ServiceParser.getServiceParameters(s).isPresent())
			.map(s->
				BindServiceInstanceRequest.builder()
					.applicationName(deploymentId)
					.serviceInstanceName(ServiceParser.getServiceInstanceName(s))
					.parameters(ServiceParser.getServiceParameters(s).get())
					.build()
			);
	}

	int diskQuota(AppDeploymentRequest request) {
		String withUnit = request.getDeploymentProperties()
			.getOrDefault(AppDeployer.DISK_PROPERTY_KEY, this.deploymentProperties.getDisk());
		return (int) ByteSizeUtils.parseToMebibytes(withUnit);
	}

	Set<String> buildpacks(AppDeploymentRequest request) {
		// TODO: When 'buildpack' setting gets removed due to deprecation,
		//       change this logic not ot fallback to it if 'buildpacks'
		//       is used.
		String buidpacksValue = request.getDeploymentProperties()
				.get(CloudFoundryDeploymentProperties.BUILDPACKS_PROPERTY_KEY);
		String buidpackValue = request.getDeploymentProperties()
				.get(CloudFoundryDeploymentProperties.BUILDPACK_PROPERTY_KEY);
		if (buidpacksValue != null) {
			return StringUtils.commaDelimitedListToSet(buidpacksValue);
		}
		else if (buidpackValue != null) {
			return new HashSet<>(Arrays.asList(buidpackValue));
		}
		else if (!ObjectUtils.isEmpty((this.deploymentProperties.getBuildpacks()))) {
			return this.deploymentProperties.getBuildpacks();
		}
		else {
			return new HashSet<>(Arrays.asList(this.deploymentProperties.getBuildpack()));
		}
	}

	String javaOpts(AppDeploymentRequest request) {
		return Optional
				.ofNullable(
						request.getDeploymentProperties().get(CloudFoundryDeploymentProperties.JAVA_OPTS_PROPERTY_KEY))
				.orElse(this.deploymentProperties.getJavaOpts());
	}

	Predicate<Throwable> isNotFoundError() {
		return t -> t instanceof AbstractCloudFoundryException && ((AbstractCloudFoundryException) t).getStatusCode() == HttpStatus.NOT_FOUND.value();
	}

	/**
	 * Return a Docker image identifier if the application Resource is for a Docker image, or {@literal null} otherwise.
	 *
	 * @see #getApplication(AppDeploymentRequest)
	 */
	String getDockerImage(AppDeploymentRequest request) {
		try {
			String uri = request.getResource().getURI().toString();
			if (uri.startsWith("docker:")) {
				return uri.substring("docker:".length());
			} else {
				return null;
			}
		} catch (IOException e) {
			throw Exceptions.propagate(e);
		}
	}

	/**
	 * Return a Path to the application Resource or {@literal null} if the request is for a Docker image.
	 *
	 * @see #getDockerImage(AppDeploymentRequest)
	 */
	Path getApplication(AppDeploymentRequest request) {
		try {
			logger.info(
				"Preparing to push an application from {}" +
					". This may take some time if the artifact must be downloaded from a remote host.",
				request.getResource());
			if (!request.getResource().getURI().toString().startsWith("docker:")) {
				return request.getResource().getFile().toPath();
			} else {
				return null;
			}
		} catch (IOException e) {
			throw Exceptions.propagate(e);
		}
	}

	/**
	 * Return a function usable in {@literal doOnError} constructs that will unwrap unrecognized Cloud Foundry Exceptions
	 * and log the text payload.
	 */
	protected Consumer<Throwable> logError(String msg) {
		return e -> {
			if (e instanceof UnknownCloudFoundryException) {
				logger.error(msg + "\nUnknownCloudFoundryException encountered, whose payload follows:\n" + ((UnknownCloudFoundryException)e).getPayload(), e);
			} else {
				logger.error(msg, e);
			}
		};
	}

	/**
	 * To be used in order to retry the status operation for an application or task.
	 * @param id The application id or the task id
	 * @param <T> The type of status object being queried for, usually AppStatus or TaskStatus
	 * @return The function that executes the retry logic around for determining App or Task Status
	 */
	<T> Function<Mono<T>, Mono<T>> statusRetry(String id) {
		long statusTimeout = this.deploymentProperties.getStatusTimeout();
		long requestTimeout = Math.round(statusTimeout * 0.40); // wait 500ms with default status timeout of 2000ms
		long initialRetryDelay = Math.round(statusTimeout * 0.10); // wait 200ms with status timeout of 2000ms

		if (requestTimeout < 500L) {
			logger.info("Computed statusRetry Request timeout = {} ms is below 500ms minimum value.  Setting to 500ms", requestTimeout);
			requestTimeout = 500L;
		}
		final long requestTimeoutToUse = requestTimeout;
		return m -> m.timeout(Duration.ofMillis(requestTimeoutToUse))
			.doOnError(e -> {
				// show real exception if it wasn't timeout
				if (e instanceof TimeoutException) {
					logger.warn("Error getting status for {} within {}ms, Retrying operation.", id, requestTimeoutToUse);
				}
				else if (e instanceof UnknownCloudFoundryException) {
					logger.warn("Received UnknownCloudFoundryException from cf with payload={}",
							((UnknownCloudFoundryException) e).getPayload());
				}
				else {
					logger.warn("Received error from cf", e);
				}
			})
			// let all other than timeout exception to propagate back to caller
			.retryWhen(reactor.util.retry.Retry.withThrowable(Retry.onlyIf(c -> {
					logger.debug("RetryContext for id {} iteration {} backoff {}", id,  c.iteration(), c.backoff());
					if (c.iteration() > 5) {
						logger.info("Stopping retry for id {} after {} iterations", id, c.iteration());
						return false;
					}
					if (c.exception().getClass().getName().contains("org.cloudfoundry.client")) {
						// most likely real error which is not worth to retry
						return false;
					}
					// might be some netty error for not connected client, etc, retry
					return true;
				})
				.exponentialBackoff(Duration.ofMillis(initialRetryDelay), Duration.ofMillis(statusTimeout))
				.doOnRetry(c -> logger.debug("Retrying cf call for {}", id))))
			.doOnError(TimeoutException.class, e -> {
				logger.error("Retry operation on getStatus failed for {}. Max retry time {}ms", id, statusTimeout);
			});
	}

	/**
	 * Always delete downloaded files for static http resources. Conditionally delete maven resources.
	 * @param appDeploymentRequest
	 */
	protected void deleteLocalApplicationResourceFile(AppDeploymentRequest appDeploymentRequest) {

		try {

			Optional<File> fileToDelete = fileToDelete(appDeploymentRequest.getResource());
			if (fileToDelete.isPresent()) {
				File applicationFile = fileToDelete.get();

				logger.info("Free Disk Space = {} bytes, Total Disk Space = {} bytes",
						applicationFile.getFreeSpace(),
						applicationFile.getTotalSpace());


				boolean deleted = deleteFileOrDirectory(applicationFile);
				logger.info((deleted) ? "Successfully deleted the application resource: " + applicationFile.getCanonicalPath() :
						"Could not delete the application resource: " + applicationFile.getCanonicalPath());
			}

		} catch(IOException e){
			logger.warn("Exception deleting the application resource after successful CF push request."
					+ " This could cause increase in disk space usage. Exception message: " + e.getMessage());
		}
	}

	/*
	 * Always delete files downloaded from http/s url.
	 * Delete maven resources if property is set.
	 */
	private Optional<File> fileToDelete(Resource resource) throws IOException {
		String scheme = resource.getURI().getScheme().toLowerCase(Locale.ROOT);
		if (scheme.startsWith("http")) {
			return Optional.of(resource.getFile());
		}
		if (scheme.equals("maven") && deploymentProperties.isAutoDeleteMavenArtifacts()) {
			return Optional.of(resource.getFile().getParentFile());
		}
		return Optional.empty();
	}

	private boolean deleteFileOrDirectory(File fileToDelete) {
		boolean deleted;
		if (fileToDelete.isDirectory())
			deleted  = FileSystemUtils.deleteRecursively(fileToDelete);
		else {
			deleted = fileToDelete.delete();
		}
		return deleted;
	}

	/*
	 * Merge environment variables from global DeploymentProperties, application properties, and
	 * environment variables declared in ApplicationDeploymentRequest
	 */
	protected Map<String, String> mergeEnvironmentVariables(String deploymentId, AppDeploymentRequest request) {
		Map<String, String> envVariables = new HashMap<>(deploymentProperties.getEnv());
		envVariables.putAll(getApplicationProperties(deploymentId, request));
		envVariables.putAll(getDeclaredEnvironmentVariables(request));
		String javaOpts = javaOpts(request);
		if (StringUtils.hasText(javaOpts)) {
			envVariables.put("JAVA_OPTS", javaOpts(request));
		}

		if (hasCfEnv(request.getResource())) {
			Map<String, String> env =
					CfEnvConfigurer.disableJavaBuildPackAutoReconfiguration(envVariables);
			//Only append to existing spring profiles active
			env.putAll(CfEnvConfigurer.activateCloudProfile(env, null));
			envVariables.putAll(env);
		}
		return envVariables;
	}

	private Map<? extends String,? extends String> getDeclaredEnvironmentVariables(AppDeploymentRequest request) {
		Map<String, String> env = new LinkedHashMap<>();
		request.getDeploymentProperties().entrySet().stream()
				.filter(e -> e.getKey().startsWith(CloudFoundryDeploymentProperties.ENV_KEY + "."))
				.forEach(e -> env.put(e.getKey().substring(CloudFoundryDeploymentProperties.ENV_KEY.length() + 1),
						e.getValue()));
		return env;
	}

	protected boolean hasCfEnv(Resource resource) {
		if (resource instanceof CfEnvAwareResource) {
			return ((CfEnvAwareResource)resource).hasCfEnv();
		}
		return CfEnvAwareResource.of(resource).hasCfEnv();
	}

	private Map<String, String> getApplicationProperties(String deploymentId, AppDeploymentRequest request) {
		Map<String, String> applicationProperties = getSanitizedApplicationProperties(deploymentId, request);

		if (!useSpringApplicationJson(request)) {
			return applicationProperties;
		}

		try {
			return Collections.singletonMap("SPRING_APPLICATION_JSON", OBJECT_MAPPER.writeValueAsString(applicationProperties));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, String> getSanitizedApplicationProperties(String deploymentId, AppDeploymentRequest request) {
		Map<String, String> applicationProperties = new HashMap<>(request.getDefinition().getProperties());

		// Remove server.port as CF assigns a port for us, and we don't want to override that
		Optional.ofNullable(applicationProperties.remove("server.port"))
				.ifPresent(port -> logger.warn("Ignoring 'server.port={}' for app {}, as Cloud Foundry will assign a local dynamic port. Route to the app will use port 80.", port, deploymentId));

		// Update active Spring Profiles given in application properties. Create a new entry with the given key if necessary
		if (hasCfEnv(request.getResource())) {
			applicationProperties = CfEnvConfigurer
					.activateCloudProfile(applicationProperties, CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN);
		}
		return applicationProperties;
	}

	private boolean useSpringApplicationJson(AppDeploymentRequest request) {
		return Optional
				.ofNullable(request.getDeploymentProperties()
						.get(CloudFoundryDeploymentProperties.USE_SPRING_APPLICATION_JSON_KEY))
				.map(Boolean::valueOf).orElse(this.deploymentProperties.isUseSpringApplicationJson());
	}



	public RuntimeEnvironmentInfo environmentInfo() {
		return runtimeEnvironmentInfo;
	}
}
