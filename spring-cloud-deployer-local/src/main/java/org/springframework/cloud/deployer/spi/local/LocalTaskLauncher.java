/*
 * Copyright 2016-2019 the original author or authors.
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
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * A {@link TaskLauncher} implementation that spins off a new JVM process per task launch.
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Janne Valkealahti
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Michael Minella
 * @author Christian Tzolov
 * @author David Turanski
 * @author Glenn Renfro
 */
public class LocalTaskLauncher extends AbstractLocalDeployerSupport implements TaskLauncher {

	private static final Logger logger = LoggerFactory.getLogger(LocalTaskLauncher.class);

	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";

	private final Map<String, TaskInstance> running = new ConcurrentHashMap<>();

	private final Map<String, CopyOnWriteArrayList<String>> taskInstanceHistory = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new local task launcher.
	 *
	 * @param properties the properties
	 */
	public LocalTaskLauncher(LocalDeployerProperties properties) {
		super(properties);
	}

	@Override
	public String launch(AppDeploymentRequest request) {

		if (this.maxConcurrentExecutionsReached()) {
			throw new IllegalStateException(
				String.format("Cannot launch task %s. The maximum concurrent task executions is at its limit [%d].",
					request.getDefinition().getName(), this.getMaximumConcurrentTasks())
			);
		}

		String taskLaunchId = request.getDefinition().getName() + "-" + UUID.randomUUID().toString();

		pruneTaskInstanceHistory(request.getDefinition().getName(), taskLaunchId);

		HashMap<String, String> args = new HashMap<>();
		args.putAll(request.getDefinition().getProperties());
		args.put(JMX_DEFAULT_DOMAIN_KEY, taskLaunchId);
		args.put("endpoints.shutdown.enabled", "true");
		args.put("endpoints.jmx.unique-names", "true");

		try {

			Path workDir = createWorkingDir(request.getDeploymentProperties(), taskLaunchId);

			boolean useDynamicPort = isDynamicPort(request);

			int port = calcServerPort(request, useDynamicPort, args);

			ProcessBuilder builder = buildProcessBuilder(request, args, Optional.empty(), taskLaunchId).inheritIO();

			TaskInstance instance = new TaskInstance(builder, workDir, port);
			if (this.shouldInheritLogging(request)) {
				instance.start(builder);
				logger.info("launching task {}\n    Logs will be inherited.", taskLaunchId);

			}
			else {
				instance.start(builder, getLocalDeployerProperties().isDeleteFilesOnExit());
				logger.info("launching task {}\n   Logs will be in {}", taskLaunchId, workDir);
			}
			running.put(taskLaunchId, instance);

		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to launch " + request, e);
		}

		return taskLaunchId;
	}

	private void pruneTaskInstanceHistory(String taskDefinitionName, String taskLaunchId) {
		CopyOnWriteArrayList<String> oldTaskInstanceIds = taskInstanceHistory.get(taskDefinitionName);
		if (oldTaskInstanceIds == null) {
			oldTaskInstanceIds = new CopyOnWriteArrayList<>();
			taskInstanceHistory.put(taskDefinitionName, oldTaskInstanceIds);
		}

		for (String oldTaskInstanceId : oldTaskInstanceIds) {
			TaskInstance oldTaskInstance = running.get(oldTaskInstanceId);
			if (oldTaskInstance != null && oldTaskInstance.getState() != LaunchState.running
					&& oldTaskInstance.getState() != LaunchState.launching) {
				running.remove(oldTaskInstanceId);
				oldTaskInstanceIds.remove(oldTaskInstanceId);
			} else {
				oldTaskInstanceIds.remove(oldTaskInstanceId);
			}
		}
		oldTaskInstanceIds.add(taskLaunchId);
	}

	private boolean isDynamicPort(AppDeploymentRequest request) {
		boolean isServerPortKeyonArgs = isServerPortKeyPresentOnArgs(request) != null;
		return !request.getDefinition().getProperties().containsKey(SERVER_PORT_KEY)
				&& !isServerPortKeyonArgs;
	}

	@Override
	public void cancel(String id) {
		TaskInstance instance = running.get(id);
		if (instance != null) {
			instance.cancelled = true;
			if (isAlive(instance.getProcess())) {
				shutdownAndWait(instance);
			}
		}
	}

	@Override
	public TaskStatus status(String id) {
		TaskInstance instance = running.get(id);
		if (instance != null) {
			return new TaskStatus(id, instance.getState(), instance.getAttributes());
		}
		return new TaskStatus(id, LaunchState.unknown, null);
	}

	@Override
	public String getLog(String id) {
		TaskInstance instance = running.get(id);
		if (instance != null) {
			StringBuilder stringBuilder = new StringBuilder();
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
			return stringBuilder.toString();
		}
		else {
			return "Log could not be retrieved as the task instance is not running.";
		}
	}

	@Override
	public void cleanup(String id) {
	}

	@Override
	public void destroy(String appName) {
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(TaskLauncher.class, this.getClass());
	}

	@Override
	public int getMaximumConcurrentTasks() {
		return getLocalDeployerProperties().getMaximumConcurrentTasks();
	}


	@Override
	public int getRunningTaskExecutionCount() {
		int runningExecutionCount = 0;

		for (TaskInstance taskInstance: running.values()) {
			if (taskInstance.getProcess().isAlive()) {
				runningExecutionCount++;
			}
		}
		return runningExecutionCount;
	}

	private boolean maxConcurrentExecutionsReached() {
		return getRunningTaskExecutionCount() >= getMaximumConcurrentTasks();
	}

	@PreDestroy
	public void shutdown() throws Exception {
		for (String taskLaunchId : running.keySet()) {
			cancel(taskLaunchId);
		}
		taskInstanceHistory.clear();
	}

	private Path createWorkingDir(Map<String, String> deploymentProperties, String taskLaunchId) throws IOException {
		LocalDeployerProperties localDeployerPropertiesToUse = bindDeploymentProperties(deploymentProperties);

		Path workingDirectoryRoot = Files.createDirectories(localDeployerPropertiesToUse.getWorkingDirectoriesRoot());
		Path workDir = Files.createDirectories(workingDirectoryRoot.resolve(Long.toString(System.nanoTime())).resolve(taskLaunchId));
		if (localDeployerPropertiesToUse.isDeleteFilesOnExit()) {
			workDir.toFile().deleteOnExit();
		}
		return workDir;
	}

	private static class TaskInstance implements Instance {

		private Process process;

		private final Path workDir;

		private File stdout;

		private File stderr;

		private final URL baseUrl;

		private boolean cancelled;

		private TaskInstance(ProcessBuilder builder, Path workDir, int port) throws IOException {
			builder.directory(workDir.toFile());
			this.workDir = workDir;
			this.baseUrl = new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
			if (logger.isDebugEnabled()) {
				logger.debug("Local Task Launcher Commands: " + String.join(",", builder.command())
						+ ", Environment: " + builder.environment());
			}
		}

		@Override
		public URL getBaseUrl() {
			return this.baseUrl;
		}

		@Override
		public Process getProcess() {
			return this.process;
		}

		public LaunchState getState() {
			if (cancelled) {
				return LaunchState.cancelled;
			}
			Integer exit = getProcessExitValue(process);
			// TODO: consider using exit code mapper concept from batch
			if (exit != null) {
				if (exit == 0) {
					return LaunchState.complete;
				}
				else {
					return LaunchState.failed;
				}
			}
			try {
				HttpURLConnection urlConnection = (HttpURLConnection) baseUrl.openConnection();
				urlConnection.setConnectTimeout(100);
				urlConnection.connect();
				urlConnection.disconnect();
				return LaunchState.running;
			}
			catch (IOException e) {
				return LaunchState.launching;
			}
		}

		public String getStdOut() {
			try {
				return FileCopyUtils.copyToString(new InputStreamReader(new FileInputStream(this.stdout)));
			}
			catch (IOException e) {
				return "Log retrieval returned " + e.getMessage();
			}
		}

		public String getStdErr() {
			try {
				return FileCopyUtils.copyToString(new InputStreamReader(new FileInputStream(this.stderr)));
			}
			catch (IOException e) {
				return "Log retrieval returned " + e.getMessage();
			}
		}

		/**
		 * Will start the process while redirecting 'out' and 'err' streams to the 'out' and 'err'
		 * streams of this process.
		 */
		private void start(ProcessBuilder builder) throws IOException {
			if (logger.isDebugEnabled()) {
				logger.debug("Local Task Launcher Commands: " + String.join(",", builder.command())
						+ ", Environment: " + builder.environment());
			}
			this.process = builder.start();
		}

		private void start(ProcessBuilder builder, boolean deleteOnExit) throws IOException {
			String workDirPath = workDir.toFile().getAbsolutePath();
			this.stdout = Files.createFile(Paths.get(workDirPath, "stdout.log")).toFile();
			this.stderr = Files.createFile(Paths.get(workDirPath, "stderr.log")).toFile();
			builder.redirectOutput(this.stdout);
			builder.redirectError(this.stderr);
			this.process = builder.start();
			if(deleteOnExit) {
				this.stdout.deleteOnExit();
				this.stderr.deleteOnExit();
			}
		}

		private Map<String, String> getAttributes() {
			HashMap<String, String> result = new HashMap<>();
			result.put("working.dir", workDir.toFile().getAbsolutePath());
			if(this.stdout != null) {
				result.put("stdout", stdout.getAbsolutePath());
			}
			if(this.stderr != null) {
				result.put("stderr", stderr.getAbsolutePath());
			}
			result.put("url", baseUrl.toString());
			return result;
		}
	}

	/**
	 * Returns the process exit value. We explicitly use Integer instead of int
	 * to indicate that if {@code NULL} is returned, the process is still running.
	 *
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

}
