/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.local;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for local app deployer and task launcher providing
 * support for common functionality.
 *
 * @author Janne Valkealahti
 * @author Mark Fisher
 */
public abstract class AbstractLocalDeployerSupport {

	private static final Logger logger = LoggerFactory.getLogger(LocalAppDeployer.class);

	private final LocalDeployerProperties properties;

	private final RestTemplate restTemplate = new RestTemplate();

	/**
	 * Instantiates a new abstract deployer support.
	 *
	 * @param properties the local deployer properties
	 */
	public AbstractLocalDeployerSupport(LocalDeployerProperties properties) {
		Assert.notNull(properties, "LocalDeployerProperties must not be null");
		this.properties = properties;
	}

	/**
	 * Gets the local deployer properties.
	 *
	 * @return the local deployer properties
	 */
	final protected LocalDeployerProperties getLocalDeployerProperties() {
		return properties;
	}

	/**
	 * Builds the execution command for an application.
	 *
	 * @param request the request for the application to execute
	 * @return the build command as a string array
	 */
	private String[] buildExecutionCommand(AppDeploymentRequest request) {
		ArrayList<String> commands = new ArrayList<String>();
		commands.add(properties.getJavaCmd());
		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		ExecutionCommandBuilder commandBuilder = new ExecutionCommandBuilder();
		// Add Java System Properties (ie -Dmy.prop=val) before main class or -jar
		commandBuilder.addJavaOptions(commands, deploymentProperties, properties);
		commandBuilder.addJavaExecutionOptions(commands, request);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Java Commands = " + commands);
		return commands.toArray(new String[0]);
	}



	/**
	 * Retain the environment variable strings in the provided set indicated by
	 * {@link LocalDeployerProperties#getEnvVarsToInherit}.
	 * This assumes that the provided set can be modified.
	 *
	 * @param vars set of environment variable strings
	 */
	protected void retainEnvVars(Set<String> vars) {
		String[] patterns = getLocalDeployerProperties().getEnvVarsToInherit();

		for (Iterator<String> iterator = vars.iterator(); iterator.hasNext();) {
			String var = iterator.next();
			boolean retain = false;
			for (String pattern : patterns) {
				if (Pattern.matches(pattern, var)) {
					retain = true;
					break;
				}
			}
			if (!retain) {
				iterator.remove();
			}
		}
	}

	/**
	 * Builds the process builder.
	 *
	 * @param request the request
	 * @param args the args
	 * @return the process builder
	 */
	protected ProcessBuilder buildProcessBuilder(AppDeploymentRequest request, Map<String, String> args) {
		Assert.notNull(request, "AppDeploymentRequest must be set");
		Assert.notNull(args, "Args must be set");
		ProcessBuilder builder = new ProcessBuilder(buildExecutionCommand(request));
		retainEnvVars(builder.environment().keySet());
		builder.environment().putAll(args);
		return builder;
	}

	/**
	 * Shut down the {@link Process} backing the application {@link Instance}.
	 * If the application exposes a {@code /shutdown} endpoint, that will be
	 * invoked followed by a wait that will not exceed the number of seconds
	 * indicated by {@link LocalDeployerProperties#shutdownTimeout}. If the
	 * timeout period is exceeded (or if the {@code /shutdown} endpoint is not exposed),
	 * the process will be shut down via {@link Process#destroy()}.
	 *
	 * @param instance the application instance to shut down
	 */
	protected void shutdownAndWait(Instance instance) {
		try {
			int timeout = getLocalDeployerProperties().getShutdownTimeout();
			if (timeout > 0) {
				ResponseEntity<String> response = restTemplate.postForEntity(
						instance.getBaseUrl() + "/shutdown", null, String.class);
				if (response.getStatusCode().is2xxSuccessful()) {
					long timeoutTimestamp = System.currentTimeMillis() + (timeout * 1000);
					while (isAlive(instance.getProcess()) && System.currentTimeMillis() < timeoutTimestamp) {
						Thread.sleep(1000);
					}
				}
			}
		}
		catch (ResourceAccessException e) {
			// ignore I/O errors
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			if (isAlive(instance.getProcess())) {
				instance.getProcess().destroy();
			}
		}
	}

	// Copy-pasting of JDK8+ isAlive method to retain JDK7 compatibility
	protected static boolean isAlive(Process process) {
		try {
			process.exitValue();
			return false;
		}
		catch (IllegalThreadStateException e) {
			return true;
		}
	}

	protected interface Instance {

		URL getBaseUrl();

		Process getProcess();
	}
}
