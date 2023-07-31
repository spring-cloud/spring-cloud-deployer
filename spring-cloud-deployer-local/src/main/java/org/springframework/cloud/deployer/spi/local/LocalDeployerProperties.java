/*
 * Copyright 2015-2022 the original author or authors.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the local deployer.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Oleg Zhurakousky
 * @author Vinicius Carvalho
 * @author David Turanski
 * @author Christian Tzolov
 */
@Validated
@ConfigurationProperties(prefix = LocalDeployerProperties.PREFIX)
public class LocalDeployerProperties {

	/**
	 * Top level prefix for local deployer configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.deployer.local";

	/**
	 * Deployer property allowing logging to be redirected to the output stream of
	 * the process that triggered child process. Could be set per the entire
	 * deployment (<em>i.e.</em> {@literal deployer.*.local.inheritLogging=true}) or
	 * per individual application (<em>i.e.</em>
	 * {@literal deployer.<app-name>.local.inheritLogging=true}).
	 */
	public static final String INHERIT_LOGGING = PREFIX + ".inherit-logging";

	/**
	 * Remote debugging property allowing one to specify port for the remote debug
	 * session. Must be set per individual application (<em>i.e.</em>
	 * {@literal deployer.<app-name>.local.debugPort=9999}).
	 *
	 * @deprecated This is only JDK 8 compatible. Use the {@link #DEBUG_ADDRESS} instead for supporting all JDKs.
	 */
	public static final String DEBUG_PORT = PREFIX + ".debug-port";

	/**
	 * Remote debugging property allowing one to specify the address for the remote debug
	 * session. On Java versions 1.8 or older use the <em>port</em> format. On Java versions 1.9 or greater use the
	 * <em>host:port</em> format. The host could default to <em>*</em>. May be set for individual applications (<em>i.e.</em>
	 * {@literal deployer.<app-name>.local.debugAddress=*:9999}).
	 */
	public static final String DEBUG_ADDRESS = PREFIX + ".debug-address";

	/**
	 * Remote debugging property allowing one to specify if the startup of the
	 * application should be suspended until remote debug session is established.
	 * Values must be either 'y' or 'n'. Must be set per individual application
	 * (<em>i.e.</em> {@literal deployer.<app-name>.local.debugSuspend=y}).
	 */
	public static final String DEBUG_SUSPEND = PREFIX + ".debug-suspend";

	private static final Logger logger = LoggerFactory.getLogger(LocalDeployerProperties.class);

	private static final String JAVA_COMMAND = LocalDeployerUtils.isWindows() ? "java.exe" : "java";

	// looks like some windows systems uses 'Path' but process builder give it as 'PATH'
	private static final String[] ENV_VARS_TO_INHERIT_DEFAULTS_WIN = {"TMP", "TEMP", "PATH", "Path",
		AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON};

	private static final String[] ENV_VARS_TO_INHERIT_DEFAULTS_OTHER = {"TMP", "LANG", "LANGUAGE", "LC_.*", "PATH",
		AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON};

	/**
	 * Directory in which all created processes will run and create log files.
	 */
	private Path workingDirectoriesRoot = new File(System.getProperty("java.io.tmpdir")).toPath();

	/**
	 * Whether to delete created files and directories on JVM exit.
	 */
	private boolean deleteFilesOnExit = true;

	/**
	 * Array of regular expression patterns for environment variables that should be
	 * passed to launched applications.
	 */
	private String[] envVarsToInherit = LocalDeployerUtils.isWindows() ? ENV_VARS_TO_INHERIT_DEFAULTS_WIN
		: ENV_VARS_TO_INHERIT_DEFAULTS_OTHER;

	/**
	 * The command to run java.
	 */
	private String javaCmd = JAVA_COMMAND;

	/**
	 * Maximum number of seconds to wait for application shutdown. via the
	 * {@code /shutdown} endpoint. A timeout value of 0 specifies an infinite
	 * timeout. Default is 30 seconds.
	 */
	@Min(-1)
	private int shutdownTimeout = 30;

	/**
	 * The Java Options to pass to the JVM, e.g -Dtest=foo
	 */
	private String javaOpts;

	/**
	 * Flag to indicate whether application properties are passed as command line
	 * args or in a SPRING_APPLICATION_JSON environment variable. Default value is
	 * {@code true}.
	 */
	private boolean useSpringApplicationJson = true;

	private final PortRange portRange = new PortRange();


	/**
	 * The maximum concurrent tasks allowed for this platform instance.
	 */
	@Min(1)
	private int maximumConcurrentTasks = 20;

	/**
	 * Set remote debugging port for JDK 8 runtimes.
	 *
	 * @deprecated Use the {@link #debugAddress} instead!
	 */
	private Integer debugPort;

	/**
	 * Debugging address for the remote clients to attache to. Addresses have the format "<name>:<port>" where <name>
	 * is the host name and <port> is the socket port number at which it attaches or listens.
	 * For JDK 8 or earlier, the address consists of the port number alone (the host name is implicit to localhost).
	 * Example addresses for JDK version 9 or higher: <code>*:20075, 192.168.178.10:20075</code>.
	 * Example addresses for JDK version 8 or earlier: <code>20075</code>.
	 */
	private String debugAddress;

	public enum DebugSuspendType {y, n}

	/**
	 * Suspend defines whether the JVM should suspend and wait for a debugger to attach or not
	 */
	private DebugSuspendType debugSuspend = DebugSuspendType.y;

	private boolean inheritLogging;

	private final Docker docker = new Docker();

	/**
	 * (optional) hostname to use when computing the URL of the deployed application.
	 * By default the {@link CommandBuilder} implementations decide how to build the hostname.
	 */
	private String hostname;

	private Map<String, String> javaHomePath = new HashMap<>();

	private AppAdmin appAdmin = new AppAdmin();

	public LocalDeployerProperties() {
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			javaHomePath.put("2", javaHome);
			javaHomePath.put("3", javaHome);
		}
	}

	public LocalDeployerProperties(LocalDeployerProperties from) {
		this.debugPort = from.getDebugPort();
		this.debugAddress = from.getDebugAddress();
		this.debugSuspend = from.getDebugSuspend();
		this.deleteFilesOnExit = from.isDeleteFilesOnExit();
		this.docker.network = from.getDocker().getNetwork();
		this.docker.deleteContainerOnExit = from.getDocker().isDeleteContainerOnExit();
		this.docker.portRange = from.getDocker().getPortRange();
		this.envVarsToInherit = new String[from.getEnvVarsToInherit().length];
		System.arraycopy(from.getEnvVarsToInherit(), 0, this.envVarsToInherit, 0, from.getEnvVarsToInherit().length);
		this.inheritLogging = from.isInheritLogging();
		this.javaCmd = from.getJavaCmd();
		this.javaOpts = from.getJavaOpts();
		this.maximumConcurrentTasks = from.getMaximumConcurrentTasks();
		this.portRange.high = from.getPortRange().getHigh();
		this.portRange.low = from.getPortRange().getLow();
		this.shutdownTimeout = from.getShutdownTimeout();
		this.useSpringApplicationJson = from.isUseSpringApplicationJson();
		this.workingDirectoriesRoot = Paths.get(from.getWorkingDirectoriesRoot().toUri());
		this.hostname = from.getHostname();
		this.appAdmin = from.getAppAdmin();
		this.javaHomePath = from.getJavaHomePath().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		this.startupProbe = from.getStartupProbe();
		this.healthProbe = from.getHealthProbe();
	}

	public static class PortRange {

		/**
		 * Lower bound for computing applications's random port.
		 */
		private int low = 20000;

		/**
		 * Upper bound for computing applications's random port.
		 */
		private int high = 61000;

		public int getLow() {
			return low;
		}

		public void setLow(int low) {
			this.low = low;
		}

		public int getHigh() {
			return high;
		}

		public void setHigh(int high) {
			this.high = high;
		}

		@Override
		public String toString() {
			return "{ low=" + low + ", high=" + high + '}';
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + high;
			result = prime * result + low;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			PortRange other = (PortRange) obj;
			if (high != other.high) {
				return false;
			}
			return low == other.low;
		}
	}

	public static class Docker {
		/**
		 * Container network
		 */
		private String network = "bridge";

		/**
		 * Whether to delete the container on container exit.
		 */
		private boolean deleteContainerOnExit = true;

		/**
		 * Allow the Docker command builder use its own port range.
		 */
		private PortRange portRange = new PortRange();

		/**
		 * Set port mappings for container
		 */
		private String portMappings;

		/**
		 * Set volume mappings
		 */
		private String volumeMounts;

		public PortRange getPortRange() {
			return portRange;
		}

		public String getNetwork() {
			return network;
		}

		public void setNetwork(String network) {
			this.network = network;
		}

		public boolean isDeleteContainerOnExit() {
			return deleteContainerOnExit;
		}

		public void setDeleteContainerOnExit(boolean deleteContainerOnExit) {
			this.deleteContainerOnExit = deleteContainerOnExit;
		}

		public String getPortMappings() {
			return portMappings;
		}

		public void setPortMappings(String portMappings) {
			this.portMappings = portMappings;
		}

		public String getVolumeMounts() {
			return volumeMounts;
		}

		public void setVolumeMounts(String volumeMounts) {
			this.volumeMounts = volumeMounts;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((network == null) ? 0 : network.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Docker other = (Docker) obj;
			if (network == null) {
				return other.network == null;
			} else return network.equals(other.network);
		}
	}

	public Docker getDocker() {
		return docker;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public Integer getDebugPort() {
		return debugPort;
	}

	public DebugSuspendType getDebugSuspend() {
		return debugSuspend;
	}

	public void setDebugSuspend(DebugSuspendType debugSuspend) {
		this.debugSuspend = debugSuspend;
	}

	public void setDebugPort(Integer debugPort) {
		logger.warn("The debugPort is deprecated! It supports only pre Java 9 environments. " +
			"Please use the debugAddress property instead!");
		this.debugPort = debugPort;
	}

	public String getDebugAddress() {
		return debugAddress;
	}

	public void setDebugAddress(String debugAddress) {
		this.debugAddress = debugAddress;
	}

	public boolean isInheritLogging() {
		return inheritLogging;
	}

	public void setInheritLogging(boolean inheritLogging) {
		this.inheritLogging = inheritLogging;
	}

	public String getJavaCommand(String bootVersion) {
		if (!StringUtils.hasText(javaCmd) || this.javaCmd.equals(JAVA_COMMAND)) {
			return deduceJavaCommand(bootVersion);
		}
		return javaCmd;
	}

	public Map<String, String> getJavaHomePath() {
		return javaHomePath;
	}

	public void setJavaHomePath(Map<String, String> javaHomePath) {
		this.javaHomePath = javaHomePath;
	}

	public String getJavaCmd() {
		return javaCmd;
	}

	public void setJavaCmd(String javaCmd) {
		this.javaCmd = javaCmd;
	}

	public Path getWorkingDirectoriesRoot() {
		return workingDirectoriesRoot;
	}

	public void setWorkingDirectoriesRoot(String workingDirectoriesRoot) {
		this.workingDirectoriesRoot = Paths.get(workingDirectoriesRoot);
	}

	public void setWorkingDirectoriesRoot(Path workingDirectoriesRoot) {
		this.workingDirectoriesRoot = workingDirectoriesRoot;
	}

	public boolean isDeleteFilesOnExit() {
		return deleteFilesOnExit;
	}

	public void setDeleteFilesOnExit(boolean deleteFilesOnExit) {
		this.deleteFilesOnExit = deleteFilesOnExit;
	}

	public String[] getEnvVarsToInherit() {
		return envVarsToInherit;
	}

	public void setEnvVarsToInherit(String[] envVarsToInherit) {
		this.envVarsToInherit = envVarsToInherit;
	}

	public int getShutdownTimeout() {
		return shutdownTimeout;
	}

	public LocalDeployerProperties setShutdownTimeout(int shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
		return this;
	}

	public String getJavaOpts() {
		return javaOpts;
	}

	public void setJavaOpts(String javaOpts) {
		this.javaOpts = javaOpts;
	}

	public boolean isUseSpringApplicationJson() {
		return useSpringApplicationJson;
	}

	public void setUseSpringApplicationJson(boolean useSpringApplicationJson) {
		this.useSpringApplicationJson = useSpringApplicationJson;
	}

	public PortRange getPortRange() {
		return portRange;
	}

	public int getMaximumConcurrentTasks() {
		return maximumConcurrentTasks;
	}

	public void setMaximumConcurrentTasks(int maximumConcurrentTasks) {
		this.maximumConcurrentTasks = maximumConcurrentTasks;
	}

	private HttpProbe startupProbe = new HttpProbe();

	private HttpProbe healthProbe = new HttpProbe();

	public HttpProbe getStartupProbe() {
		return startupProbe;
	}

	public void setStartupProbe(HttpProbe startupProbe) {
		this.startupProbe = startupProbe;
	}

	public HttpProbe getHealthProbe() {
		return healthProbe;
	}

	public void setHealthProbe(HttpProbe healthProbe) {
		this.healthProbe = healthProbe;
	}

	public AppAdmin getAppAdmin() {
		return appAdmin;
	}

	public void setAppAdmin(AppAdmin appAdmin) {
		this.appAdmin = appAdmin;
	}

	public static class HttpProbe {

		/**
		 * Path to check as a probe
		 */
		private String path;

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}
	}

	private String deduceJavaCommand(String bootVersion) {
		String javaExecutablePath = JAVA_COMMAND;

		String javaHome = getJavaHome(bootVersion);
		if (StringUtils.hasText(javaHome)) {
			File javaExecutable = new File(javaHome, "bin" + File.separator + javaExecutablePath);
			Assert.isTrue(javaExecutable.exists(), () ->
				"Java executable'" + javaExecutable + "'discovered via 'java.home' system property '" + this.javaHomePath
					+ "' does not exist.");
			Assert.isTrue(javaExecutable.canExecute(), () ->
				"Java executable'" + javaExecutable + "'discovered via 'java.home' system property '" + this.javaHomePath
					+ "' is not executable.");
			javaExecutablePath = javaExecutable.getAbsolutePath();
		} else {
			logger.warn("System property 'java.home' and 'spring.cloud.deployer.local.{}.javaHomePath' is not set. " +
				"Defaulting to the java executable path as " + JAVA_COMMAND + " assuming it's in PATH.", bootVersion);
		}
		return javaExecutablePath;
	}

	private String getJavaHome(String bootVersion) {
		String path = javaHomePath.get(bootVersion);
		return path == null ? System.getProperty("java.home") : path;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("workingDirectoriesRoot", this.workingDirectoriesRoot)
			.append("javaOpts", this.javaOpts).append("envVarsToInherit", this.envVarsToInherit).toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + debugPort.hashCode();
		result = prime * result + debugSuspend.hashCode();
		result = prime * result + (deleteFilesOnExit ? 1231 : 1237);
		result = prime * result + docker.hashCode();
		result = prime * result + Arrays.hashCode(envVarsToInherit);
		result = prime * result + (inheritLogging ? 1231 : 1237);
		result = prime * result + javaHomePath.hashCode();
		result = prime * result + javaOpts.hashCode();
		result = prime * result + maximumConcurrentTasks;
		result = prime * result + portRange.hashCode();
		result = prime * result + shutdownTimeout;
		result = prime * result + (useSpringApplicationJson ? 1231 : 1237);
		result = prime * result + workingDirectoriesRoot.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		LocalDeployerProperties other = (LocalDeployerProperties) obj;
		if (debugPort == null) {
			if (other.debugPort != null) {
				return false;
			}
		} else if (!debugPort.equals(other.debugPort)) {
			return false;
		}
		if (debugAddress == null) {
			if (other.debugAddress != null) {
				return false;
			}
		} else if (!debugAddress.equals(other.debugAddress)) {
			return false;
		}
		if (debugSuspend == null) {
			if (other.debugSuspend != null) {
				return false;
			}
		} else if (!debugSuspend.equals(other.debugSuspend)) {
			return false;
		}
		if (deleteFilesOnExit != other.deleteFilesOnExit) {
			return false;
		}
		if (!docker.equals(other.docker)) {
			return false;
		}
		if (!Arrays.equals(envVarsToInherit, other.envVarsToInherit)) {
			return false;
		}
		if (inheritLogging != other.inheritLogging) {
			return false;
		}
		if (javaHomePath == null) {
			if (other.javaHomePath != null) {
				return false;
			}
		} else if (!javaHomePath.equals(other.javaHomePath)) {
			return false;
		}
		if (javaOpts == null) {
			if (other.javaOpts != null) {
				return false;
			}
		} else if (!javaOpts.equals(other.javaOpts)) {
			return false;
		}
		if (maximumConcurrentTasks != other.maximumConcurrentTasks) {
			return false;
		}
		if (!portRange.equals(other.portRange)) {
			return false;
		}
		if (shutdownTimeout != other.shutdownTimeout) {
			return false;
		}
		if (useSpringApplicationJson != other.useSpringApplicationJson) {
			return false;
		}
		if (workingDirectoriesRoot == null) {
			return other.workingDirectoriesRoot == null;
		} else return workingDirectoriesRoot.equals(other.workingDirectoriesRoot);
	}
}
