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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.util.SocketUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * An {@link AppDeployer} implementation that spins off a new JVM process per app instance.
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Janne Valkealahti
 */
public class LocalAppDeployer extends AbstractDeployerSupport implements AppDeployer {

	private Path logPathRoot;

	private static final Logger logger = LoggerFactory.getLogger(LocalAppDeployer.class);

	private static final String SERVER_PORT_KEY = "server.port";

	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";

	private static final int DEFAULT_SERVER_PORT = 8080;

	private static final String GROUP_DEPLOYMENT_ID = "dataflow.group-deployment-id";

	private static final Set<String> ENV_VARS_TO_INHERIT = new HashSet<>();

	static {
		// TMP controls the location of java.io.tmpDir on Windows
		if (System.getProperty("os.name").startsWith("Windows")) {
			ENV_VARS_TO_INHERIT.add("TMP");
		}
	}

	private final Map<String, List<Instance>> running = new ConcurrentHashMap<>();

	private final RestTemplate restTemplate = new RestTemplate();

	/**
	 * Instantiates a new local app deployer.
	 *
	 * @param properties the properties
	 */
	public LocalAppDeployer(LocalDeployerProperties properties) {
		super(properties);
		try {
			this.logPathRoot = Files.createTempDirectory(properties.getWorkingDirectoriesRoot(), "spring-cloud-dataflow-");
		}
		catch (IOException e) {
			throw new RuntimeException("Could not create workdir root: " + properties.getWorkingDirectoriesRoot(), e);
		}
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		Resource resource = request.getResource();
		String jarPath;
		try {
			jarPath = resource.getFile().getAbsolutePath();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		String group = request.getEnvironmentProperties().get(GROUP_PROPERTY_KEY);
		String deploymentId = String.format("%s.%s", group, request.getDefinition().getName());
		if (running.containsKey(deploymentId)) {
			throw new IllegalStateException(String.format("App for '%s' is already running", deploymentId));
		}
		List<Instance> processes = new ArrayList<>();
		running.put(deploymentId, processes);
		boolean useDynamicPort = !request.getDefinition().getProperties().containsKey(SERVER_PORT_KEY);
		HashMap<String, String> args = new HashMap<>();
		args.putAll(request.getDefinition().getProperties());
		args.putAll(request.getEnvironmentProperties());
		String groupDeploymentId = request.getEnvironmentProperties().get(GROUP_DEPLOYMENT_ID);
		if (groupDeploymentId == null) {
			groupDeploymentId = group + "-" + System.currentTimeMillis();
		}
		args.put(JMX_DEFAULT_DOMAIN_KEY, deploymentId);
		args.put("endpoints.shutdown.enabled", "true");
		args.put("endpoints.jmx.unique-names", "true");
		try {
			Path deploymentGroupDir = Paths.get(logPathRoot.toFile().getAbsolutePath(), groupDeploymentId);
			if (!Files.exists(deploymentGroupDir)) {
				Files.createDirectory(deploymentGroupDir);
				deploymentGroupDir.toFile().deleteOnExit();
			}
			Path workDir = Files
					.createDirectory(Paths.get(deploymentGroupDir.toFile().getAbsolutePath(), deploymentId));
			if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
				workDir.toFile().deleteOnExit();
			}
			String countProperty = request.getEnvironmentProperties().get(COUNT_PROPERTY_KEY);
			int count = (StringUtils.hasText(countProperty)) ? Integer.parseInt(countProperty) : 1;
			for (int i = 0; i < count; i++) {
				int port = useDynamicPort ? SocketUtils.findAvailableTcpPort(DEFAULT_SERVER_PORT)
						: Integer.parseInt(request.getDefinition().getProperties().get(SERVER_PORT_KEY));
				if (useDynamicPort) {
					args.put(SERVER_PORT_KEY, String.valueOf(port));
				}
				ProcessBuilder builder = new ProcessBuilder(buildJarExecutionCommand(jarPath, request));
				builder.environment().keySet().retainAll(ENV_VARS_TO_INHERIT);
				builder.environment().putAll(args);
				Instance instance = new Instance(deploymentId, i, builder, workDir, port);
				processes.add(instance);
				if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
					instance.stdout.deleteOnExit();
					instance.stderr.deleteOnExit();
				}
				logger.info("deploying app {} instance {}\n   Logs will be in {}", deploymentId, i, workDir);
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to deploy " + request, e);
		}
		return deploymentId;
	}

	@Override
	public void undeploy(String id) {
		List<Instance> processes = running.get(id);
		if (processes != null) {
			for (Instance instance : processes) {
				if (isAlive(instance.process)) {
					shutdownAndWait(instance);
				}
			}
			running.remove(id);
		}
	}

	@Override
	public AppStatus status(String id) {
		List<Instance> instances = running.get(id);
		AppStatus.Builder builder = AppStatus.of(id);
		if (instances != null) {
			for (Instance instance : instances) {
				builder.with(instance);
			}
		}
		return builder.build();
	}

	private void shutdownAndWait(Instance instance) {
		try {
			restTemplate.postForObject(instance.url + "/shutdown", null, String.class);
			instance.process.waitFor();
		}
		catch (InterruptedException | ResourceAccessException e) {
			instance.process.destroy();
		}
	}

	@PreDestroy
	public void shutdown() throws Exception {
		for (String deploymentId : running.keySet()) {
			undeploy(deploymentId);
		}
	}

	private static class Instance implements AppInstanceStatus {

		private final String deploymentId;

		private final int instanceNumber;

		private final Process process;

		private final File workDir;

		private final File stdout;

		private final File stderr;

		private final URL url;

		private Instance(String deploymentId, int instanceNumber, ProcessBuilder builder, Path workDir, int port) throws IOException {
			this.deploymentId = deploymentId;
			this.instanceNumber = instanceNumber;
			builder.directory(workDir.toFile());
			String workDirPath = workDir.toFile().getAbsolutePath();
			this.stdout = Files.createFile(Paths.get(workDirPath, "stdout_" + instanceNumber + ".log")).toFile();
			this.stderr = Files.createFile(Paths.get(workDirPath, "stderr_" + instanceNumber + ".log")).toFile();
			builder.redirectOutput(this.stdout);
			builder.redirectError(this.stderr);
			builder.environment().put("INSTANCE_INDEX", Integer.toString(instanceNumber));
			this.process = builder.start();
			this.workDir = workDir.toFile();
			this.url = new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
		}

		@Override
		public String getId() {
			return deploymentId + "-" + instanceNumber;
		}

		@Override
		public DeploymentState getState() {
			Integer exit = getProcessExitValue(process);
			// TODO: consider using exit code mapper concept from batch
			if (exit != null) {
				if (exit == 0) {
					return DeploymentState.undeployed;
				}
				else {
					return DeploymentState.failed;
				}
			}
			try {
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.connect();
				urlConnection.disconnect();
				return DeploymentState.deployed;
			}
			catch (IOException e) {
				return DeploymentState.deploying;
			}
		}

		public Map<String, String> getAttributes() {
			HashMap<String, String> result = new HashMap<>();
			result.put("working.dir", workDir.getAbsolutePath());
			result.put("stdout", stdout.getAbsolutePath());
			result.put("stderr", stderr.getAbsolutePath());
			result.put("url", url.toString());
			return result;
		}
	}

	/**
	 * Returns the process exit value. We explicitly use Integer instead of int
	 * to indicate that if {@code NULL} is returned, the process is still running.
	 * @param process the process
	 * @return the process exit value or {@code NULL} if process is still alive
	 */
	private static Integer getProcessExitValue(Process process) {
		try {
			return process.exitValue();
		}
		catch (IllegalThreadStateException e) {
			// process is still alive
			return null;
		}
	}

	// Copy-pasting of JDK8+ isAlive method to retain JDK7 compatibility
	private static boolean isAlive(Process process) {
		try {
			process.exitValue();
			return false;
		}
		catch (IllegalThreadStateException e) {
			return true;
		}
	}
}
