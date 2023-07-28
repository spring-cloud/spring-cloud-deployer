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

package org.springframework.cloud.deployer.spi.local;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Michael Minella
 */
public class JavaCommandBuilder implements CommandBuilder {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LocalDeployerProperties properties;

	public JavaCommandBuilder(LocalDeployerProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getPortSuggestion(LocalDeployerProperties localDeployerProperties) {
		return ThreadLocalRandom.current().nextInt(localDeployerProperties.getPortRange().getLow(),
			localDeployerProperties.getPortRange().getHigh());
	}

	@Override
	public URL getBaseUrl(String deploymentId, int index, int port) {
		try {
			return new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public ProcessBuilder buildExecutionCommand(
		AppDeploymentRequest request, Map<String, String> appInstanceEnv,
		String deployerId, Optional<Integer> appInstanceNumber, LocalDeployerProperties localDeployerProperties,
		Optional<DebugAddress> debugAddressOption
	) {
		ArrayList<String> commands = new ArrayList<>();
		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		String bootVersion = deploymentProperties.get("spring.cloud.deployer.bootVersion");
		if (bootVersion == null) {
			Optional<String> bootArg = request.getCommandlineArguments()
				.stream()
				.filter(s ->
					s.startsWith("--spring.cloud.deployer.bootVersion=") || s.startsWith("--spring.cloud.deployer.boot-version="))
				.findFirst();
			if (bootArg.isPresent()) {
				int indexEq = bootArg.get().indexOf('=');
				Assert.isTrue(indexEq > 0, () -> "Expected = in argument:" + bootArg.get());
				bootVersion = bootArg.get().substring(indexEq + 1).trim();
			}
		}
		if (bootVersion == null) {
			bootVersion = "3"; // safe to launch boot 2 with Java 17
		}
		commands.add(bindDeploymentProperties(deploymentProperties).getJavaCmd(bootVersion));

		debugAddressOption.ifPresent(debugAddress -> commands.add(getJdwpOptions(debugAddress.getSuspend(), debugAddress.getAddress())));

		// Add Java System Properties (ie -Dmy.prop=val) before main class or -jar
		addJavaOptions(commands, deploymentProperties, properties);
		addJavaExecutionOptions(commands, request);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Java Command = {}", commands);

		ProcessBuilder builder = new ProcessBuilder(AbstractLocalDeployerSupport.windowsSupport(commands.toArray(new String[0])));

		// retain before we put in app related variables.
		retainEnvVars(builder.environment(), localDeployerProperties);
		builder.environment().putAll(appInstanceEnv);

		return builder;
	}

	/**
	 * Retain the environment variable strings in the provided set indicated by
	 * {@link LocalDeployerProperties#getEnvVarsToInherit}.
	 * This assumes that the provided set can be modified.
	 *
	 * @param vars                    set of environment variable strings
	 * @param localDeployerProperties local deployer properties
	 */
	protected void retainEnvVars(Map<String, String> vars, LocalDeployerProperties localDeployerProperties) {
		List<String> patterns = new ArrayList<>(Arrays.asList(localDeployerProperties.getEnvVarsToInherit()));
		for (Iterator<Entry<String, String>> iterator = vars.entrySet().iterator(); iterator.hasNext(); ) {
			Entry<String, String> entry = iterator.next();
			String var = entry.getKey();
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

	protected void addJavaOptions(
		List<String> commands, Map<String, String> deploymentProperties,
		LocalDeployerProperties localDeployerProperties
	) {
		String memory = null;
		if (deploymentProperties.containsKey(AppDeployer.MEMORY_PROPERTY_KEY)) {
			memory = "-Xmx" +
				ByteSizeUtils.parseToMebibytes(deploymentProperties.get(AppDeployer.MEMORY_PROPERTY_KEY)) + "m";
		}

		String javaOptsString = bindDeploymentProperties(deploymentProperties).getJavaOpts();
		if (javaOptsString == null && memory != null) {
			commands.add(memory);
		}

		if (javaOptsString != null) {
			String[] javaOpts = StringUtils.tokenizeToStringArray(javaOptsString, " ");
			boolean noJavaMemoryOption = Stream.of(javaOpts).noneMatch(s -> s.startsWith("-Xmx"));
			if (noJavaMemoryOption && memory != null) {
				commands.add(memory);
			}
			commands.addAll(Arrays.asList(javaOpts));
		} else {
			if (localDeployerProperties.getJavaOpts() != null) {
				String[] javaOpts = StringUtils.tokenizeToStringArray(localDeployerProperties.getJavaOpts(), " ");
				commands.addAll(Arrays.asList(javaOpts));
			}
		}
	}

	protected void addJavaExecutionOptions(List<String> commands, AppDeploymentRequest request) {
		commands.add("-jar");
		Resource resource = request.getResource();
		try {
			commands.add(resource.getFile().getAbsolutePath());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * This will merge the deployment properties that were passed in at runtime with the deployment properties
	 * of the Deployer instance.
	 *
	 * @param runtimeDeploymentProperties deployment properties passed in at runtime
	 * @return merged deployer properties
	 */
	protected LocalDeployerProperties bindDeploymentProperties(Map<String, String> runtimeDeploymentProperties) {
		LocalDeployerProperties copyOfDefaultProperties = new LocalDeployerProperties(this.properties);
		return new Binder(new MapConfigurationPropertySource(runtimeDeploymentProperties))
			.bind(LocalDeployerProperties.PREFIX, Bindable.ofInstance(copyOfDefaultProperties))
			.orElse(copyOfDefaultProperties);
	}

}
