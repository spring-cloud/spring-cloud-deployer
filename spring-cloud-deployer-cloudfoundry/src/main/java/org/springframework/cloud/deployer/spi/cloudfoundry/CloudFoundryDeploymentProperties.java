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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.applications.ApplicationHealthCheck;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.validation.annotation.Validated;


/**
 * Holds configuration properties for specifying what resources and services an app
 * deployed to a Cloud Foundry runtime will get.
 *
 * @author Eric Bottard
 * @author Greg Turnquist
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
@Validated
public class CloudFoundryDeploymentProperties {

	private static final Log log = LogFactory.getLog(CloudFoundryDeploymentProperties.class);

	public static final String SERVICES_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".services";

	public static final String HEALTHCHECK_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".health-check";

	public static final String HEALTHCHECK_HTTP_ENDPOINT_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES
			+ ".health-check-http-endpoint";

	public static final String HEALTHCHECK_TIMEOUT_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".health-check-timeout";

	public static final String ROUTE_PATH_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".route-path";

	public static final String ROUTE_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".route";

	public static final String ROUTES_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".routes";

	public static final String NO_ROUTE_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".no-route";

	public static final String HOST_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".host";

	public static final String DOMAIN_PROPERTY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".domain";

	public static final String BUILDPACK_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".buildpack";

	public static final String BUILDPACKS_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".buildpacks";

	public static final String JAVA_OPTS_PROPERTY_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".javaOpts";

	public static final String USE_SPRING_APPLICATION_JSON_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES
			+ ".use-spring-application-json";

	public static final String ENV_KEY = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".env";

	private static final String DEFAULT_BUILDPACK = "https://github.com/cloudfoundry/java-buildpack.git#v4.29.1";

	/**
	 * The names of services to bind to all applications deployed as a module. This should
	 * typically contain a service capable of playing the role of a binding transport.
	 */
	private Set<String> services = new HashSet<>();

	/**
	 * The host name to use as part of the route. Defaults to hostname derived by Cloud
	 * Foundry.
	 */
	private String host = null;

	/**
	 * The domain to use when mapping routes for applications.
	 */
	private String domain;

	/**
	 * The routes that the application should be bound to. Mutually exclusive with host and
	 * domain.
	 */
	private Set<String> routes = new HashSet<>();

	/**
	 * The buildpack to use for deploying the application.
	 */
	@Deprecated
	private String buildpack = DEFAULT_BUILDPACK;

	/**
	 * The buildpacks to use for deploying the application.
	 */
	private Set<String> buildpacks = new HashSet<>();

	/**
	 * The amount of memory to allocate, if not overridden per-app. Default unit is mebibytes,
	 * 'M' and 'G" suffixes supported.
	 */
	private String memory = "1024m";

	/**
	 * The amount of disk space to allocate, if not overridden per-app. Default unit is
	 * mebibytes, 'M' and 'G" suffixes supported.
	 */
	private String disk = "1024m";

	/**
	 * The type of health check to perform on deployed application, if not overridden per-app.
	 * Defaults to PORT
	 */
	private ApplicationHealthCheck healthCheck = ApplicationHealthCheck.PORT;

	/**
	 * The path that the http health check will use, defaults to @{code /health}
	 */
	private String healthCheckHttpEndpoint = "/health";

	/**
	 * The timeout value for health checks in seconds. Defaults to 120 seconds.
	 */
	private String healthCheckTimeout = "120";

	/**
	 * The number of instances to run.
	 */
	private int instances = 1;

	/**
	 * Flag to enable prefixing the app name with a random prefix.
	 */
	private boolean enableRandomAppNamePrefix = true;

	/**
	 * Timeout for blocking API calls, in seconds.
	 */
	private long apiTimeout = 360L;

	/**
	 * Timeout for status API operations in milliseconds
	 */
	private long statusTimeout = 30_000L;

	/**
	 * Flag to indicate whether application properties are fed into SPRING_APPLICATION_JSON or
	 * ENVIRONMENT VARIABLES.
	 */
	private boolean useSpringApplicationJson = true;

	/**
	 * If set, override the timeout allocated for staging the app by the client.
	 */
	private Duration stagingTimeout = Duration.ofMinutes(15L);

	/**
	 * If set, override the timeout allocated for starting the app by the client.
	 */
	private Duration startupTimeout = Duration.ofMinutes(5L);

	/**
	 * String to use as prefix for name of deployed app. Defaults to spring.application.name.
	 */
	@Value("${spring.application.name:}")
	private String appNamePrefix;

	/**
	 * Whether to also delete routes when un-deploying an application.
	 */
	private boolean deleteRoutes = true;

	/**
	 * Whether to push task apps
	 */
	private boolean pushTaskAppsEnabled = true;

	/**
	 * Whether to automatically delete cached Maven artifacts after deployment.
	 */
	private boolean autoDeleteMavenArtifacts = true;

	/**
	 * The maximum concurrent tasks allowed.
	 */
	@Min(1)
	private int maximumConcurrentTasks = 20;

	private String javaOpts;

	private Optional<Map<String, String>> env = Optional.empty();
	
	/**
	 * Location of the PCF scheduler REST API enpoint ot use.
	 */
	private String schedulerUrl;

	/**
	 * The number of retries allowed when scheduling a task if an {@link javax.net.ssl.SSLException} is thrown.
	 */
	private int scheduleSSLRetryCount = 5;

	/**
	 * The number of seconds to wait for a unSchedule to complete.
	 */
	private int unScheduleTimeoutInSeconds = 30;

	/**
	 * The number of seconds to wait for a schedule to complete.
	 * This excludes the time it takes to stage the application on Cloud Foundry.
	 */
	private int scheduleTimeoutInSeconds = 30;

	/**
	 * The number of seconds to wait for a list of schedules to be returned.
	 */
	private int listTimeoutInSeconds = 60;

	private AppAdmin appAdmin = new AppAdmin();

	public Set<String> getServices() {
		return services;
	}

	public void setServices(Set<String> services) {
		this.services = services;
	}

	@Deprecated
	public String getBuildpack() {
		return buildpack;
	}

	@Deprecated
	public void setBuildpack(String buildpack) {
		this.buildpack = buildpack;
	}

	public Set<String> getBuildpacks() {
		return buildpacks;
	}

	public void setBuildpacks(Set<String> buildpacks) {
		this.buildpacks = buildpacks;
	}

	public String getMemory() {
		return memory;
	}

	public void setMemory(String memory) {
		this.memory = memory;
	}

	public String getDisk() {
		return disk;
	}

	public void setDisk(String disk) {
		this.disk = disk;
	}

	public int getInstances() {
		return instances;
	}

	public void setInstances(int instances) {
		this.instances = instances;
	}

	public boolean isEnableRandomAppNamePrefix() {
		return enableRandomAppNamePrefix;
	}

	public void setEnableRandomAppNamePrefix(boolean enableRandomAppNamePrefix) {
		this.enableRandomAppNamePrefix = enableRandomAppNamePrefix;
	}

	public String getAppNamePrefix() {
		return appNamePrefix;
	}

	public void setAppNamePrefix(String appNamePrefix) {
		this.appNamePrefix = appNamePrefix;
	}

	public long getApiTimeout() {
		return apiTimeout;
	}

	public void setApiTimeout(long apiTimeout) {
		this.apiTimeout = apiTimeout;
	}

	public boolean isUseSpringApplicationJson() {
		return useSpringApplicationJson;
	}

	public void setUseSpringApplicationJson(boolean useSpringApplicationJson) {
		this.useSpringApplicationJson = useSpringApplicationJson;
	}

	public ApplicationHealthCheck getHealthCheck() {
		return healthCheck;
	}

	public void setHealthCheck(ApplicationHealthCheck healthCheck) {
		this.healthCheck = healthCheck;
	}

	public String getHealthCheckHttpEndpoint() {
		return healthCheckHttpEndpoint;
	}

	public void setHealthCheckHttpEndpoint(String healthCheckHttpEndpoint) {
		this.healthCheckHttpEndpoint = healthCheckHttpEndpoint;
	}

	public String getHealthCheckTimeout() {
		return healthCheckTimeout;
	}

	public void setHealthCheckTimeout(String healthCheckTimeout) {
		this.healthCheckTimeout = healthCheckTimeout;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Set<String> getRoutes() {
		return routes;
	}

	public void setRoutes(Set<String> routes) {
		this.routes = routes;
	}

	public Duration getStagingTimeout() {
		return stagingTimeout;
	}

	public void setStagingTimeout(Duration stagingTimeout) {
		this.stagingTimeout = stagingTimeout;
	}

	public Duration getStartupTimeout() {
		return startupTimeout;
	}

	public void setStartupTimeout(Duration startupTimeout) {
		this.startupTimeout = startupTimeout;
	}

	public long getStatusTimeout() {
		return statusTimeout;
	}

	public void setStatusTimeout(long statusTimeout) {
		this.statusTimeout = statusTimeout;
	}

	public boolean isDeleteRoutes() {
		return deleteRoutes;
	}

	public void setDeleteRoutes(boolean deleteRoutes) {
		this.deleteRoutes = deleteRoutes;
	}

	public String getJavaOpts() {
		return javaOpts;
	}

	public void setJavaOpts(String javaOpts) {
		this.javaOpts = javaOpts;
	}

	public int getMaximumConcurrentTasks() {
		return maximumConcurrentTasks;
	}

	public void setMaximumConcurrentTasks(int maximumConcurrentTasks) {
		this.maximumConcurrentTasks = maximumConcurrentTasks;
	}

	public boolean isPushTaskAppsEnabled() {
		return pushTaskAppsEnabled;
	}

	public void setPushTaskAppsEnabled(boolean pushTaskAppsEnabled) {
		this.pushTaskAppsEnabled = pushTaskAppsEnabled;
	}

	public boolean isAutoDeleteMavenArtifacts() {
		return autoDeleteMavenArtifacts;
	}

	public void setAutoDeleteMavenArtifacts(boolean autoDeleteMavenArtifacts) {
		this.autoDeleteMavenArtifacts = autoDeleteMavenArtifacts;
	}

	public Map<String, String> getEnv() {
		return env.orElseGet(Collections::emptyMap);
	}

	public void setEnv(@NotNull Map<String, String> env) {
		this.env.map(e -> {
					log.error("Environment is immutable. New entries have not been applied");
					return this.env;
				}
		).orElse(this.env = Optional.of(Collections.unmodifiableMap(env)));
	}

	public String getSchedulerUrl() {
		return schedulerUrl;
	}

	public void setSchedulerUrl(String schedulerUrl) {
		this.schedulerUrl = schedulerUrl;
	}

	public int getScheduleSSLRetryCount() {
		return scheduleSSLRetryCount;
	}

	public void setScheduleSSLRetryCount(int scheduleSSLRetryCount) {
		this.scheduleSSLRetryCount = scheduleSSLRetryCount;
	}

	public int getUnScheduleTimeoutInSeconds() {
		return unScheduleTimeoutInSeconds;
	}

	public void setUnScheduleTimeoutInSeconds(int unScheduleTimeoutInSeconds) {
		this.unScheduleTimeoutInSeconds = unScheduleTimeoutInSeconds;
	}

	public int getScheduleTimeoutInSeconds() {
		return scheduleTimeoutInSeconds;
	}

	public void setScheduleTimeoutInSeconds(int scheduleTimeoutInSeconds) {
		this.scheduleTimeoutInSeconds = scheduleTimeoutInSeconds;
	}

	public int getListTimeoutInSeconds() {
		return listTimeoutInSeconds;
	}

	public void setListTimeoutInSeconds(int listTimeoutInSeconds) {
		this.listTimeoutInSeconds = listTimeoutInSeconds;
	}

	public AppAdmin getAppAdmin() {
		return appAdmin;
	}

	public void setAppAdmin(AppAdmin appAdmin) {
		this.appAdmin = appAdmin;
	}
}
