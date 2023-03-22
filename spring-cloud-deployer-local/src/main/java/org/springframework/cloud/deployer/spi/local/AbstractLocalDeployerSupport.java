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

package org.springframework.cloud.deployer.spi.local;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for local app deployer and task launcher providing support for common
 * functionality.
 *
 * @author Janne Valkealahti
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Vinicius Carvalho
 * @author Michael Minella
 * @author David Turanski
 * @author Christian Tzolov
 */
public abstract class AbstractLocalDeployerSupport {

	protected static Set<Integer> usedPorts = Collections.newSetFromMap(new LinkedHashMap<Integer, Boolean>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
			return size() > 1000;
		}
	});

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	public static final String SPRING_APPLICATION_JSON = "SPRING_APPLICATION_JSON";

	public static final int DEFAULT_SERVER_PORT = 8080;

	private static final String USE_SPRING_APPLICATION_JSON_KEY = LocalDeployerProperties.PREFIX
			+ ".use-spring-application-json";

	static final String SERVER_PORT_KEY = "server.port";

	static final String SERVER_PORT_KEY_COMMAND_LINE_ARG = "--" + SERVER_PORT_KEY + "=";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final LocalDeployerProperties localDeployerProperties;

	private final RestTemplate restTemplate;

	private final JavaCommandBuilder javaCommandBuilder;

	private final DockerCommandBuilder dockerCommandBuilder;

	/**
	 * Instantiates a new abstract deployer support.
	 *
	 * @param localDeployerProperties the local deployer properties
	 */
	public AbstractLocalDeployerSupport(LocalDeployerProperties localDeployerProperties) {
		Assert.notNull(localDeployerProperties, "LocalDeployerProperties must not be null");
		this.localDeployerProperties = localDeployerProperties;
		this.javaCommandBuilder = new JavaCommandBuilder(localDeployerProperties);
		this.dockerCommandBuilder = new DockerCommandBuilder(localDeployerProperties.getDocker().getNetwork());
		this.restTemplate = buildRestTemplate(localDeployerProperties);
	}

	/**
	 * Builds a {@link RestTemplate} used for calling app's shutdown endpoint. If needed can
	 * be overridden from an implementing class. This default implementation sets connection
	 * and read timeouts for {@link SimpleClientHttpRequestFactory} and configures
	 * {@link RestTemplate} to use that factory. If shutdown timeout in properties negative,
	 * returns default {@link RestTemplate} which doesn't use timeouts.
	 *
	 * @param properties the local deployer properties
	 * @return the rest template
	 */
	protected RestTemplate buildRestTemplate(LocalDeployerProperties properties) {
		if (properties != null && properties.getShutdownTimeout() > -1) {
			SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
			clientHttpRequestFactory.setConnectTimeout(properties.getShutdownTimeout() * 1000);
			clientHttpRequestFactory.setReadTimeout(properties.getShutdownTimeout() * 1000);
			return new RestTemplate(clientHttpRequestFactory);
		}
		// fall back to plain default constructor
		return new RestTemplate();
	}

	/**
	 * Create the RuntimeEnvironmentInfo.
	 *
	 * @return the local runtime environment info
	 */
	protected RuntimeEnvironmentInfo createRuntimeEnvironmentInfo(Class<?> spiClass, Class<?> implementationClass) {
		return new RuntimeEnvironmentInfo.Builder().spiClass(spiClass)
				.implementationName(implementationClass.getSimpleName())
				.implementationVersion(RuntimeVersionUtils.getVersion(implementationClass)).platformType("Local")
				.platformApiVersion(System.getProperty("os.name") + " " + System.getProperty("os.version"))
				.platformClientVersion(System.getProperty("os.version"))
				.platformHostVersion(System.getProperty("os.version")).build();
	}

	/**
	 * Gets the local deployer properties.
	 *
	 * @return the local deployer properties
	 */
	final protected LocalDeployerProperties getLocalDeployerProperties() {
		return localDeployerProperties;
	}

	/**
	 * Builds the process builder. Application properties are expected to be calculated prior
	 * to this method. No additional consolidation of application properties is done while
	 * creating the {@code ProcessBuilder}.
	 *
	 * @param request the request
	 * @param appInstanceEnv the instance environment variables
	 * @return the process builder
	 */
	protected ProcessBuilder buildProcessBuilder(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
			Optional<Integer> appInstanceNumber, String deploymentId) {
		Assert.notNull(request, "AppDeploymentRequest must be set");

		Map<String, String> appPropertiesToUse = formatApplicationProperties(request, appInstanceEnv);
		if (logger.isInfoEnabled()) {
			logger.info("Preparing to run an application from {}. This may take some time if the artifact must be " +
					"downloaded from a remote host.", request.getResource());
		}

		LocalDeployerProperties localDeployerProperties = bindDeploymentProperties(request.getDeploymentProperties());

		Optional<DebugAddress> debugAddressOption = DebugAddress.from(localDeployerProperties, appInstanceNumber.orElse(0));

		ProcessBuilder builder = getCommandBuilder(request)
				.buildExecutionCommand(request, appPropertiesToUse, deploymentId, appInstanceNumber,
						localDeployerProperties, debugAddressOption);

		logger.info(String.format("Command to be executed: %s", String.join(" ", builder.command())));
		//logger.debug(String.format("Environment Variables to be used : %s", builder.environment().entrySet().stream()
		//		.map(entry -> entry.getKey() + " : " + entry.getValue()).collect(Collectors.joining(", "))));
		return builder;
	}

	/**
	 * Detects the Command builder by the type of the resource in the requests.
	 * @param request deployment request containing information about the resource to be deployed.
	 * @return Returns a command builder compatible with the type of the resource set in the request.
	 */
	protected CommandBuilder getCommandBuilder(AppDeploymentRequest request) {
		return (request.getResource() instanceof DockerResource) ? this.dockerCommandBuilder : this.javaCommandBuilder;
	}

	/**
	 * tweak escaping double quotes needed for windows
	 * @param commands
	 * @return
	 */
	public static String[] windowsSupport(String[] commands) {
		if (LocalDeployerUtils.isWindows()) {
			for (int i = 0; i < commands.length; i++) {
				commands[i] = commands[i].replace("\"", "\\\"");
			}
		}
		return commands;
	}

	/**
	 * This will merge the deployment properties that were passed in at runtime with the
	 * deployment properties of the Deployer instance.
	 * @param runtimeDeploymentProperties deployment properties passed in at runtime
	 * @return merged deployer properties
	 */
	protected LocalDeployerProperties bindDeploymentProperties(Map<String, String> runtimeDeploymentProperties) {
		LocalDeployerProperties copyOfDefaultProperties = new LocalDeployerProperties(this.localDeployerProperties);
		return new Binder(new MapConfigurationPropertySource(runtimeDeploymentProperties))
				.bind(LocalDeployerProperties.PREFIX, Bindable.ofInstance(copyOfDefaultProperties))
				.orElse(copyOfDefaultProperties);
	}

	protected Map<String, String> formatApplicationProperties(AppDeploymentRequest request,
			Map<String, String> appInstanceEnvToUse) {
		Map<String, String> applicationPropertiesToUse = new HashMap<>(appInstanceEnvToUse);

		if (useSpringApplicationJson(request)) {
			try {
				// If SPRING_APPLICATION_JSON is found, explode it and merge back into appProperties
				if (applicationPropertiesToUse.containsKey(SPRING_APPLICATION_JSON)) {
					applicationPropertiesToUse
							.putAll(OBJECT_MAPPER.readValue(applicationPropertiesToUse.get(SPRING_APPLICATION_JSON),
									new TypeReference<HashMap<String, String>>() {
									}));
					applicationPropertiesToUse.remove(SPRING_APPLICATION_JSON);
				}
			}
			catch (IOException e) {
				throw new IllegalArgumentException(
						"Unable to read existing SPRING_APPLICATION_JSON to merge properties", e);
			}

			try {
				String saj = OBJECT_MAPPER.writeValueAsString(applicationPropertiesToUse);

				applicationPropertiesToUse = new HashMap<>(1);

				applicationPropertiesToUse.put(SPRING_APPLICATION_JSON, saj);
			}
			catch (JsonProcessingException e) {
				throw new IllegalArgumentException(
						"Unable to create SPRING_APPLICATION_JSON from application properties", e);
			}
		}

		return applicationPropertiesToUse;
	}

	/**
	 * Shut down the {@link Process} backing the application {@link Instance}. If the
	 * application exposes a {@code /shutdown} endpoint, that will be invoked followed by a
	 * wait that will not exceed the number of seconds indicated by
	 * {@link LocalDeployerProperties#getShutdownTimeout()} . If the timeout period is exceeded (or
	 * if the {@code /shutdown} endpoint is not exposed), the process will be shut down via
	 * {@link Process#destroy()}.
	 *
	 * @param instance the application instance to shut down
	 */
	protected void shutdownAndWait(Instance instance) {
		try {
			int timeout = getLocalDeployerProperties().getShutdownTimeout();
			if (timeout > 0) {
				logger.debug("About to call shutdown endpoint for the instance {}", instance);
				ResponseEntity<String> response = restTemplate.postForEntity(
						instance.getBaseUrl() + "/shutdown", null, String.class);
				logger.debug("Response for shutdown endpoint completed for the instance {} with response {}", instance,
						response);
				if (response.getStatusCode().is2xxSuccessful()) {
					long timeoutTimestamp = System.currentTimeMillis() + (timeout * 1000);
					while (isAlive(instance.getProcess()) && System.currentTimeMillis() < timeoutTimestamp) {
						Thread.sleep(1000);
					}
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (Exception e) {
			// ignore all other errors as we're going to
			// destroy process if it's alive
		}
		finally {
			if (isAlive(instance.getProcess())) {
				logger.debug("About to call destroy the process for the instance {}", instance);
				instance.getProcess().destroy();
				logger.debug("Call completed to destroy the process for the instance {}", instance);
			}
		}
	}

	// Copy-pasting of JDK8+ isAlive method to retain JDK7 compatibility
	protected boolean isAlive(Process process) {
		try {
			logger.debug("About to call exitValue of the process {}", process);
			process.exitValue();
			logger.debug("Call to exitValue of the process {} complete, return false", process);
			return false;
		}
		catch (IllegalThreadStateException e) {
			logger.debug("Call to exitValue of the process {} threw exception, return true", process);
			return true;
		}
	}

	protected boolean useSpringApplicationJson(AppDeploymentRequest request) {
		return request.getDefinition().getProperties().containsKey(USE_SPRING_APPLICATION_JSON_KEY)
				|| this.localDeployerProperties.isUseSpringApplicationJson();
	}

	// TODO (tzolov): This method has a treacherous side affect! Apart from returning the computed Port it also modifies
	//  the appInstanceEnvVars map! Later is used for down stream app deployment configuration.
	//  As a consequence if you place the calcServerPort in wrong place (for example after the buildProcessBuilder(..)
	//  call then the Port configuration won't be known to the command builder).
	//  Proper solution is to (1) either make the method void and rename it to calcAndSetServerPort or (2) make the
	//  method return the mutated appInstanceEnvVars. Sync with the SCT team because the method is used by the
	//  LocalTaskLauncher (e.g. prod. grade)
	protected int calcServerPort(AppDeploymentRequest request, boolean useDynamicPort,
			Map<String, String> appInstanceEnvVars) {

		int port = DEFAULT_SERVER_PORT;
		Integer commandLineArgPort = isServerPortKeyPresentOnArgs(request);

		if (useDynamicPort) {
			port = getRandomPort(request);
			appInstanceEnvVars.put(LocalAppDeployer.SERVER_PORT_KEY, String.valueOf(port));
		}
		else if (commandLineArgPort != null) {
			port = commandLineArgPort;
		}
		else if (request.getDefinition().getProperties().containsKey(LocalAppDeployer.SERVER_PORT_KEY)) {
			port = Integer.parseInt(request.getDefinition().getProperties().get(LocalAppDeployer.SERVER_PORT_KEY));
		}

		return port;
	}

	/**
	 * Will check if {@link LocalDeployerProperties#INHERIT_LOGGING} is set by checking
	 * deployment properties.
	 */
	protected boolean shouldInheritLogging(AppDeploymentRequest request) {
		LocalDeployerProperties bindDeployerProperties = bindDeploymentProperties(request.getDeploymentProperties());
		return bindDeployerProperties.isInheritLogging();
	}

	public synchronized int getRandomPort(AppDeploymentRequest request) {
		Set<Integer> availPorts = new HashSet<>();
		// SocketUtils.findAvailableTcpPorts retries 6 times, add additional retry on top.
		for (int retryCount = 0; retryCount < 5; retryCount++) {
			int randomInt = getCommandBuilder(request).getPortSuggestion(localDeployerProperties);
			try {
				availPorts = DeployerSocketUtils.findAvailableTcpPorts(5, randomInt, randomInt + 5);
				try {
					// Give some time for the system to release up ports that were scanned.
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					logger.debug(e.getMessage() + "Retrying to find available ports.");
				}
				break;
			}
			catch (IllegalStateException e) {
				logger.debug(e.getMessage() + "  Retrying to find available ports.");
			}
		}
		if (availPorts.isEmpty()) {
			throw new IllegalStateException(
					"Could not find an available TCP port in the range" + localDeployerProperties.getPortRange());
		}

		int finalPort = -1;
		logger.debug("Available Ports: " + availPorts);
		for (Integer freePort : availPorts) {
			if (!usedPorts.contains(freePort)) {
				finalPort = freePort;
				usedPorts.add(finalPort);
				break;
			}
		}
		if (finalPort == -1) {
			throw new IllegalStateException(
					"Could not find a free random port range " + localDeployerProperties.getPortRange());
		}
		logger.debug("Using Port: " + finalPort);
		return finalPort;
	}

	protected Integer isServerPortKeyPresentOnArgs(AppDeploymentRequest request) {
		return request.getCommandlineArguments().stream()
				.filter(argument -> argument.startsWith(SERVER_PORT_KEY_COMMAND_LINE_ARG))
				.map(argument -> Integer.parseInt(argument.replace(SERVER_PORT_KEY_COMMAND_LINE_ARG, "").trim()))
				.findFirst()
				.orElse(null);
	}

	protected interface Instance {

		URL getBaseUrl();

		Process getProcess();
	}
}
