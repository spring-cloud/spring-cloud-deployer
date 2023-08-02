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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties.HttpProbe;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link AppDeployer} implementation that spins off a new JVM process per app
 * instance.
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Janne Valkealahti
 * @author Patrick Peralta
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Michael Minella
 * @author Glenn Renfro
 * @author Christian Tzolov
 * @author David Turanski
 */
public class LocalAppDeployer extends AbstractLocalDeployerSupport implements AppDeployer {

	private static final Logger logger = LoggerFactory.getLogger(LocalAppDeployer.class);
	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";
	private static final String ENDPOINTS_SHUTDOWN_ENABLED_KEY = "endpoints.shutdown.enabled";
	private final Map<String, AppInstancesHolder> running = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new local app deployer.
	 *
	 * @param properties the properties
	 */
	public LocalAppDeployer(LocalDeployerProperties properties) {
		super(properties);
	}

	/**
	 * Returns the process exit value. We explicitly use Integer instead of int to indicate
	 * that if {@code NULL} is returned, the process is still running.
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

	/**
	 * Gets the local process pid if available. This should be a safe workaround for unix
	 * systems where reflection can be used to get pid. More reliable way should land with
	 * jdk9.
	 *
	 * @param p the process
	 * @return the local process pid
	 */
	private static synchronized int getLocalProcessPid(Process p) {
		int pid = 0;
		try {
			if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
				Field f = p.getClass().getDeclaredField("pid");
				f.setAccessible(true);
				pid = f.getInt(p);
				f.setAccessible(false);
			}
		}
		catch (Exception e) {
			pid = 0;
		}
		return pid;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		String group = request.getDeploymentProperties().get(GROUP_PROPERTY_KEY);
		String deploymentId = String.format("%s.%s", group, request.getDefinition().getName());
		validateStatus(deploymentId, DeploymentState.unknown);
		List<AppInstance> processes = new ArrayList<>();
		running.put(deploymentId, new AppInstancesHolder(processes, request));

		try {
			Path workDir = createWorkingDir(request.getDeploymentProperties(), deploymentId);

			String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
			int count = (StringUtils.hasText(countProperty)) ? Integer.parseInt(countProperty) : 1;

			for (int index = 0; index < count; index++) {
				processes.add(deployApp(request, workDir, group, deploymentId, index, request.getDeploymentProperties()));
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to deploy " + request, e);
		}
		return deploymentId;
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		validateStatus(appScaleRequest.getDeploymentId(), DeploymentState.deployed);
		AppInstancesHolder holder = running.get(appScaleRequest.getDeploymentId());
		List<AppInstance> instances = holder != null ? holder.instances : null;
		if (instances == null) {
			throw new IllegalStateException(
					"Can't find existing instances for deploymentId " + appScaleRequest.getDeploymentId());
		}
		AppDeploymentRequest request = holder.request;

		String group = request.getDeploymentProperties().get(GROUP_PROPERTY_KEY);
		String deploymentId = String.format("%s.%s", group, request.getDefinition().getName());

		try {
			Path workDir = createWorkingDir(request.getDeploymentProperties(), deploymentId);
			int deltaCount = appScaleRequest.getCount() - instances.size();
			int targetCount = instances.size() + deltaCount;

			if (deltaCount > 0) {
				for (int index = instances.size(); index < targetCount; index++) {
					instances.add(deployApp(request, workDir, group, deploymentId, index, request.getDeploymentProperties()));
				}
			}
			else if (deltaCount < 0) {
				List<AppInstance> processes = new ArrayList<>();
				for (int index = instances.size() - 1; index >= targetCount; index--) {
					processes.add(instances.remove(index));
				}
				for (AppInstance instance : processes) {
					if (isAlive(instance.getProcess())) {
						logger.info("Un-deploying app with deploymentId {} instance {}.", deploymentId, instance.getInstanceNumber());
						shutdownAndWait(instance);
					}
				}
			}

		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to deploy " + request, e);
		}
	}

	@Override
	public void undeploy(String id) {
		AppInstancesHolder holder = running.get(id);
		List<AppInstance> processes = holder != null ? holder.instances : null;
		if (processes != null) {
			for (AppInstance instance : processes) {
				if (isAlive(instance.getProcess())) {
					logger.info("Un-deploying app with deploymentId {} instance {}.", id, instance.getInstanceNumber());
					shutdownAndWait(instance);
				}
			}
			running.remove(id);
		}
		else {
			throw new IllegalStateException(String.format("App with deploymentId %s is not in a deployed state.", id));
		}
	}

	@Override
	public AppStatus status(String id) {
		AppInstancesHolder holder = running.get(id);
		List<AppInstance> instances = holder != null ? holder.instances : null;
		AppStatus.Builder builder = AppStatus.of(id);

		if (instances != null) {
			for (AppInstance instance : instances) {
				builder.with(instance);
			}
		}

		return builder.build();
	}

	@Override
	public String getLog(String id) {
		AppInstancesHolder holder = running.get(id);
		List<AppInstance> instances = holder != null ? holder.instances : null;
		StringBuilder stringBuilder = new StringBuilder();
		if (instances != null) {
			for (AppInstance instance : instances) {
				String stderr = instance.getStdErr();
				if (StringUtils.hasText(stderr)) {
					stringBuilder.append("stderr:\n");
					stringBuilder.append(stderr);
				}
				String stdout = instance.getStdOut();
				if (StringUtils.hasText(stdout)) {
					stringBuilder.append("stdout:\n");
					stringBuilder.append(stdout);
				}
			}
		}
		return stringBuilder.toString();
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(AppDeployer.class, this.getClass());
	}

	@PreDestroy
	public void shutdown() {
		for (String deploymentId : running.keySet()) {
			undeploy(deploymentId);
		}
	}

	private AppInstance deployApp(AppDeploymentRequest request, Path workDir, String group, String deploymentId,
			int index, Map<String, String> deploymentProperties) throws IOException {

		LocalDeployerProperties localDeployerPropertiesToUse = bindDeploymentProperties(deploymentProperties);

		// consolidatedAppProperties is a Map of all application properties to be used by
		// the app being launched. These values should end up as environment variables
		// either explicitly or as a SPRING_APPLICATION_JSON value.
		HashMap<String, String> consolidatedAppProperties = new HashMap<>(request.getDefinition().getProperties());

		consolidatedAppProperties.put(JMX_DEFAULT_DOMAIN_KEY, deploymentId);

		if (!request.getDefinition().getProperties().containsKey(ENDPOINTS_SHUTDOWN_ENABLED_KEY)) {
			consolidatedAppProperties.put(ENDPOINTS_SHUTDOWN_ENABLED_KEY, "true");
		}

		consolidatedAppProperties.put("endpoints.jmx.unique-names", "true");

		if (group != null) {
			consolidatedAppProperties.put("spring.cloud.application.group", group);
		}

		// This Map is the consolidated application properties *for the instance*
		// to be deployed in this iteration
		Map<String, String> appInstanceEnv = new HashMap<>(consolidatedAppProperties);

		// we only set 'normal' style props reflecting what we set for env format
		// for cross reference to work inside SAJ.
		// looks like for now we can't remove these env style formats as i.e.
		// DeployerIntegrationTestProperties in tests really assume 'INSTANCE_INDEX' and
		// this might be indication that we can't yet fully remove those.
		String guid = toGuid(deploymentId, index);
		if (useSpringApplicationJson(request)) {
			appInstanceEnv.put("instance.index", Integer.toString(index));
			appInstanceEnv.put("spring.cloud.stream.instanceIndex", Integer.toString(index));
			appInstanceEnv.put("spring.application.index", Integer.toString(index));
			appInstanceEnv.put("spring.cloud.application.guid", guid);
		}
		else {
			appInstanceEnv.put("INSTANCE_INDEX", Integer.toString(index));
			appInstanceEnv.put("SPRING_APPLICATION_INDEX", Integer.toString(index));
			appInstanceEnv.put("SPRING_CLOUD_APPLICATION_GUID", guid);
		}

		this.getLocalDeployerProperties().getAppAdmin().addCredentialsToAppEnvironmentAsProperties(appInstanceEnv);

		boolean useDynamicPort = !request.getDefinition().getProperties().containsKey(SERVER_PORT_KEY);
		// WATCH OUT: The calcServerPort sets the computed port in the appInstanceEnv#SERVER_PORT_KEY.
		//  Later is implicitly passed to and used inside the command builder. Therefore the calcServerPort() method
		//  must always be called before the buildProcessBuilder(..)!
		int port = calcServerPort(request, useDynamicPort, appInstanceEnv);

		ProcessBuilder builder = buildProcessBuilder(request, appInstanceEnv, Optional.of(index), deploymentId)
				.inheritIO();
		builder.directory(workDir.toFile());

		URL baseUrl = (StringUtils.hasText(localDeployerPropertiesToUse.getHostname())) ?
				new URL("http", localDeployerPropertiesToUse.getHostname(), port, "")
				: getCommandBuilder(request).getBaseUrl(deploymentId, index, port);

		AppInstance instance = new AppInstance(deploymentId, index, port, baseUrl,
				localDeployerPropertiesToUse.getStartupProbe(), localDeployerPropertiesToUse.getHealthProbe());
		if (this.shouldInheritLogging(request)) {
			instance.start(builder, workDir);
			logger.info("Deploying app with deploymentId {} instance {}.\n   Logs will be inherited.",
					deploymentId, index);
		}
		else {
			instance.start(builder, workDir, getLocalDeployerProperties().isDeleteFilesOnExit());
			logger.info("Deploying app with deploymentId {} instance {}.\n   Logs will be in {}", deploymentId,
					index, workDir);
		}
		return instance;
	}

	private Path createWorkingDir(Map<String, String> deploymentProperties, String deploymentId) throws IOException {
		LocalDeployerProperties localDeployerPropertiesToUse = bindDeploymentProperties(deploymentProperties);

		Path workingDirectoryRoot = Files.createDirectories(localDeployerPropertiesToUse.getWorkingDirectoriesRoot());
		Path workDir = Files.createDirectories(workingDirectoryRoot.resolve(Long.toString(System.currentTimeMillis())).resolve(deploymentId));

		if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
			workDir.toFile().deleteOnExit();
		}
		return workDir;
	}

	private void validateStatus(String deploymentId, DeploymentState expectedState) {
		DeploymentState state = status(deploymentId).getState();
		Assert.state(expectedState.equals(state),
				String.format("App with deploymentId [%s] with state [%s] doesn't match expected state [%s]",
						deploymentId, state, expectedState));
	}

	private static String toGuid(String deploymentId, int appIndex) {
		return String.format("%s-%s", deploymentId, appIndex);
	}

	private static class AppInstance implements Instance, AppInstanceStatus {

		private final String deploymentId;

		private final int instanceNumber;

		private final URL baseUrl;
		private final Map<String, String> attributes = new TreeMap<>();
		private int pid;
		private Process process;
		private File workFile;
		private File stdout;
		private File stderr;
		private int port;
		private HttpProbeExecutor startupProbeExecutor;
		private HttpProbeExecutor healthProbeExecutor;
		private boolean startupProbeOk;

		private AppInstance(String deploymentId, int instanceNumber, int port, URL baseUrl, HttpProbe startupProbe,
				HttpProbe healthProbe) {
			this.deploymentId = deploymentId;
			this.instanceNumber = instanceNumber;
			this.port = port;
			this.baseUrl = baseUrl;
			this.attributes.put("port", Integer.toString(port));
			this.attributes.put("guid", toGuid(deploymentId, instanceNumber));
			this.attributes.put("url", baseUrl.toString());
			this.startupProbeExecutor = HttpProbeExecutor.from(baseUrl, startupProbe);
			this.healthProbeExecutor = HttpProbeExecutor.from(baseUrl, healthProbe);
		}

		@Override
		public String getId() {
			return deploymentId + "-" + instanceNumber;
		}

		@Override
		public URL getBaseUrl() {
			return this.baseUrl;
		}

		@Override
		public Process getProcess() {
			return this.process;
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", getId(), getState());
		}

		@Override
		public DeploymentState getState() {
			Integer exit = getProcessExitValue(process);
			// TODO: consider using exit code mapper concept from batch
			if (exit != null) {
				return DeploymentState.failed;
			}
			if (port < 1) {
				// Support case where user passed in zero or negative port indicating fully random
				// chosen by OS. In this case we simply return deployed if process is up.
				// Also we can't even try http check as we would not know port to connect to.
				return DeploymentState.deployed;
			}
			// do startup probe first and only until we're deployed
			if (startupProbeExecutor != null && !startupProbeOk) {
				boolean ok = startupProbeExecutor.probe();
				if (ok) {
					startupProbeOk = true;
					return DeploymentState.deployed;
				}
				else {
					return DeploymentState.deploying;
				}
			}
			// now deployed, checking health probe
			if (healthProbeExecutor != null) {
				return healthProbeExecutor.probe() ? DeploymentState.deployed : DeploymentState.failed;
			}
			try {
				HttpURLConnection urlConnection = (HttpURLConnection) baseUrl.openConnection();
				urlConnection.setConnectTimeout(100);
				urlConnection.connect();
				urlConnection.disconnect();
				return DeploymentState.deployed;
			}
			catch (IOException e) {
				return DeploymentState.deploying;
			}
		}

		public String getStdOut() {
			try {
				return FileCopyUtils.copyToString(new InputStreamReader(Files.newInputStream(this.stdout.toPath())));
			}
			catch (IOException e) {
				return "Log retrieval returned " + e.getMessage();
			}
		}

		public String getStdErr() {
			try {
				return FileCopyUtils.copyToString(new InputStreamReader(Files.newInputStream(this.stderr.toPath())));
			}
			catch (IOException e) {
				return "Log retrieval returned " + e.getMessage();
			}
		}

		public int getInstanceNumber() {
			return instanceNumber;
		}

		@Override
		public Map<String, String> getAttributes() {
			return this.attributes;
		}

		/**
		 * Will start the process while redirecting 'out' and 'err' streams to the 'out' and 'err'
		 * streams of this process.
		 */
		private void start(ProcessBuilder builder, Path workDir) throws IOException {
			if (logger.isDebugEnabled()) {
				logger.debug("Local App Deployer Commands: " + String.join(",", builder.command())
						+ ", Environment: " + builder.environment());
			}
			this.workFile = workDir.toFile();
			this.attributes.put("working.dir", this.workFile.getAbsolutePath());
			this.process = builder.start();
			this.pid = getLocalProcessPid(this.process);
			if (pid > 0) {
				// add pid if we got it
				attributes.put("pid", Integer.toString(pid));
			}
		}

		private void start(ProcessBuilder builder, Path workDir, boolean deleteOnExit) throws IOException {
			String workDirPath = workDir.toFile().getAbsolutePath();

			this.stdout = Files.createFile(Paths.get(workDirPath, "stdout_" + instanceNumber + ".log")).toFile();
			this.attributes.put("stdout", stdout.getAbsolutePath());

			this.stderr = Files.createFile(Paths.get(workDirPath, "stderr_" + instanceNumber + ".log")).toFile();
			this.attributes.put("stderr", stderr.getAbsolutePath());

			if (deleteOnExit) {
				this.stdout.deleteOnExit();
				this.stderr.deleteOnExit();
			}
			builder.redirectOutput(Redirect.to(this.stdout));
			builder.redirectError(Redirect.to(this.stderr));

			this.start(builder, workDir);
		}
	}

	private static class AppInstancesHolder {
		final List<AppInstance> instances;
		final AppDeploymentRequest request;

		public AppInstancesHolder(List<AppInstance> instances, AppDeploymentRequest request) {
			this.instances = instances;
			this.request = request;
		}
	}
}
