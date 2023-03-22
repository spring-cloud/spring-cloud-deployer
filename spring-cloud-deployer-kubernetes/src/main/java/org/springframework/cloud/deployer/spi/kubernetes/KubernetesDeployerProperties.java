/*
 * Copyright 2015-2023 the original author or authors.
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

package org.springframework.cloud.deployer.spi.kubernetes;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.deployer.spi.app.AppAdmin;

/**
 * @author Florian Rosenberg
 * @author Thomas Risberg
 * @author Donovan Muller
 * @author Ilayaperumal Gopinathan
 * @author Leonardo Diniz
 * @author Chris Schaefer
 * @author David Turanski
 * @author Enrique Medina Montenegro
 * @author Chris Bono
 * @author Corneil du Plessis
 */
@ConfigurationProperties(prefix = KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX)
public class KubernetesDeployerProperties {

    static final String KUBERNETES_DEPLOYER_PROPERTIES_PREFIX = "spring.cloud.deployer.kubernetes";

    /**
     * Constants for app deployment properties that don't have a deployer level default
     * property.
     */
    static final String KUBERNETES_DEPLOYMENT_NODE_SELECTOR = "spring.cloud.deployer.kubernetes.deployment.nodeSelector";

    /**
     * The maximum concurrent tasks allowed for this platform instance.
     */
    private int maximumConcurrentTasks = 20;

    @NestedConfigurationProperty
    private Config fabric8 = Config.autoConfigure(null);


    public Config getFabric8() {
        return this.fabric8;
    }

    public void setFabric8(Config fabric8) {
        this.fabric8 = fabric8;
    }

    /**
     * Encapsulates resources for Kubernetes Container resource limits
     */
    public static class LimitsResources {

        /**
         * Container resource cpu limit.
         */
        private String cpu;

        /**
         * Container resource memory limit.
         */
        private String memory;

        /**
         * Container GPU vendor name for limit
         */
        private String gpuVendor;

        /**
         * Container GPU count for limit.
         */
        private String gpuCount;

        public LimitsResources() {
        }

        /**
         * 'All' args constructor
         *
         * @param cpu    Container resource cpu limit
         * @param memory Container resource memory limit
         * @deprecated This method should no longer be used to set all fields at construct time.
         * <p>
         * Use the default constructor and set() methods instead.
         */
        @Deprecated
        public LimitsResources(String cpu, String memory) {
            this.cpu = cpu;
            this.memory = memory;
        }

        public String getCpu() {
            return cpu;
        }

        public void setCpu(String cpu) {
            this.cpu = cpu;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getGpuVendor() {
            return gpuVendor;
        }

        public void setGpuVendor(String gpuVendor) {
            this.gpuVendor = gpuVendor;
        }

        public String getGpuCount() {
            return gpuCount;
        }

        public void setGpuCount(String gpuCount) {
            this.gpuCount = gpuCount;
        }
    }

    /**
     * Encapsulates resources for Kubernetes Container resource requests
     */
    public static class RequestsResources {

        /**
         * Container request limit.
         */
        private String cpu;

        /**
         * Container memory limit.
         */
        private String memory;

        public RequestsResources() {
        }

        public RequestsResources(String cpu, String memory) {
            this.cpu = cpu;
            this.memory = memory;
        }

        public String getCpu() {
            return cpu;
        }

        public void setCpu(String cpu) {
            this.cpu = cpu;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }
    }

    public static class StatefulSet {

        private VolumeClaimTemplate volumeClaimTemplate = new VolumeClaimTemplate();

        public VolumeClaimTemplate getVolumeClaimTemplate() {
            return volumeClaimTemplate;
        }

        public void setVolumeClaimTemplate(VolumeClaimTemplate volumeClaimTemplate) {
            this.volumeClaimTemplate = volumeClaimTemplate;
        }

        public static class VolumeClaimTemplate {

            /**
             * VolumeClaimTemplate name
             */
            private String name;

            /**
             * VolumeClaimTemplate storage.
             */
            private String storage = "10m";

            /**
             * VolumeClaimTemplate storage class name.
             */
            private String storageClassName;

            public String getName() {
                return this.name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getStorage() {
                return storage;
            }

            public void setStorage(String storage) {
                this.storage = storage;
            }

            public String getStorageClassName() {
                return storageClassName;
            }

            public void setStorageClassName(String storageClassName) {
                this.storageClassName = storageClassName;
            }
        }
    }

    public static class Toleration {

        private String effect;

        private String key;

        private String operator;

        private Long tolerationSeconds;

        private String value;

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public Long getTolerationSeconds() {
            return tolerationSeconds;
        }

        public void setTolerationSeconds(Long tolerationSeconds) {
            this.tolerationSeconds = tolerationSeconds;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static class KeyRef {
        private String envVarName;

        private String dataKey;

        public void setEnvVarName(String envVarName) {
            this.envVarName = envVarName;
        }

        public String getEnvVarName() {
            return envVarName;
        }

        public void setDataKey(String dataKey) {
            this.dataKey = dataKey;
        }

        public String getDataKey() {
            return dataKey;
        }
    }

    public static class SecretKeyRef extends KeyRef {
        private String secretName;

        public void setSecretName(String secretName) {
            this.secretName = secretName;
        }

        public String getSecretName() {
            return secretName;
        }
    }

    public static class ConfigMapKeyRef extends KeyRef {
        private String configMapName;

        public void setConfigMapName(String configMapName) {
            this.configMapName = configMapName;
        }

        public String getConfigMapName() {
            return configMapName;
        }
    }

    public static class PodSecurityContext {
		/**
		 * The numeric user ID to run pod container processes under
		 */
		private Long runAsUser;

		/**
		 * The numeric group id to run the entrypoint of the container process
		 */
		private Long runAsGroup;

		/**
		 * Indicates that the container must run as a non-root user
		 */
		private Boolean runAsNonRoot;

        /**
         * The numeric group ID for the volumes of the pod
         */
        private Long fsGroup;

		/**
		 * Defines behavior of changing ownership and permission of the volume before being
		 * exposed inside pod (only applies to volume types which support fsGroup based
		 * ownership and permissions) - possible values are "OnRootMismatch", "Always"
		 */
		private String fsGroupChangePolicy;

        /**
         * The numeric group IDs applied to the pod container processes, in addition to the container's primary group ID
         */
        private Long[] supplementalGroups;

		/**
		 * The seccomp options to use for the pod containers
		 */
		private SeccompProfile seccompProfile;

		/**
		 * The SELinux context to be applied to the pod containers
		 */
		private SELinuxOptions seLinuxOptions;

		/**
		 * List of namespaced sysctls used for the pod (not used when spec.os.name is windows).
		 */
		private List<SysctlInfo> sysctls;

		/**
		 * The Windows specific settings applied to all containers.
		 */
		private WindowsSecurityContextOptions windowsOptions;

        public void setRunAsUser(Long runAsUser) {
            this.runAsUser = runAsUser;
        }

        public Long getRunAsUser() {
            return this.runAsUser;
        }

		public Long getRunAsGroup() {
			return runAsGroup;
		}

		public void setRunAsGroup(Long runAsGroup) {
			this.runAsGroup = runAsGroup;
		}

		public Boolean getRunAsNonRoot() {
			return runAsNonRoot;
		}

		public void setRunAsNonRoot(Boolean runAsNonRoot) {
			this.runAsNonRoot = runAsNonRoot;
		}

		public void setFsGroup(Long fsGroup) {
            this.fsGroup = fsGroup;
        }

        public Long getFsGroup() {
            return fsGroup;
        }

		public String getFsGroupChangePolicy() {
			return fsGroupChangePolicy;
		}

		public void setFsGroupChangePolicy(String fsGroupChangePolicy) {
			this.fsGroupChangePolicy = fsGroupChangePolicy;
		}

		public void setSupplementalGroups(Long[] supplementalGroups) {
            this.supplementalGroups = supplementalGroups;
        }

        public Long[] getSupplementalGroups() {
            return supplementalGroups;
        }

        public SeccompProfile getSeccompProfile() {
            return seccompProfile;
        }

        public void setSeccompProfile(SeccompProfile seccompProfile) {
            this.seccompProfile = seccompProfile;
        }

		public SELinuxOptions getSeLinuxOptions() {
			return seLinuxOptions;
		}

		public void setSeLinuxOptions(SELinuxOptions seLinuxOptions) {
			this.seLinuxOptions = seLinuxOptions;
		}

		public List<SysctlInfo> getSysctls() {
			return sysctls;
		}

		public void setSysctls(List<SysctlInfo> sysctls) {
			this.sysctls = sysctls;
		}

		public WindowsSecurityContextOptions getWindowsOptions() {
			return windowsOptions;
		}

		public void setWindowsOptions(WindowsSecurityContextOptions windowsOptions) {
			this.windowsOptions = windowsOptions;
		}
	}

	public static class ContainerSecurityContext {

		/**
		 * Whether a process can gain more privileges than its parent process
		 */
		private Boolean allowPrivilegeEscalation;

		/**
		 * The capabilities to add/drop when running the container (cannot be set when spec.os.name is windows)
		 */
		private Capabilities capabilities;

		/**
		 * Run container in privileged mode.
		 */
		private Boolean privileged;

		/**
		 * The type of proc mount to use for the container (cannot be set when spec.os.name is windows)
		 */
		private String procMount;

		/**
		 * Mounts the container's root filesystem as read-only
		 */
		private Boolean readOnlyRootFilesystem;

		/**
		 * The numeric user ID to run pod container processes under
		 */
		private Long runAsUser;

		/**
		 * The numeric group id to run the entrypoint of the container process
		 */
		private Long runAsGroup;

		/**
		 * Indicates that the container must run as a non-root user
		 */
		private Boolean runAsNonRoot;

		/**
		 * The seccomp options to use for the container
		 */
		private SeccompProfile seccompProfile;

		/**
		 * The SELinux context to be applied to the container.
		 */
		private SELinuxOptions seLinuxOptions;

		/**
		 * The Windows specific settings applied to the container.
		 */
		private WindowsSecurityContextOptions windowsOptions;

		public Boolean getAllowPrivilegeEscalation() {
			return allowPrivilegeEscalation;
		}

		public void setAllowPrivilegeEscalation(Boolean allowPrivilegeEscalation) {
			this.allowPrivilegeEscalation = allowPrivilegeEscalation;
		}

		public Capabilities getCapabilities() {
			return capabilities;
		}

		public void setCapabilities(Capabilities capabilities) {
			this.capabilities = capabilities;
		}

		public Boolean getPrivileged() {
			return privileged;
		}

		public void setPrivileged(Boolean privileged) {
			this.privileged = privileged;
		}

		public String getProcMount() {
			return procMount;
		}

		public void setProcMount(String procMount) {
			this.procMount = procMount;
		}

		public Boolean getReadOnlyRootFilesystem() {
			return readOnlyRootFilesystem;
		}

		public void setReadOnlyRootFilesystem(Boolean readOnlyRootFilesystem) {
			this.readOnlyRootFilesystem = readOnlyRootFilesystem;
		}

		public Long getRunAsUser() {
			return runAsUser;
		}

		public void setRunAsUser(Long runAsUser) {
			this.runAsUser = runAsUser;
		}

		public Long getRunAsGroup() {
			return runAsGroup;
		}

		public void setRunAsGroup(Long runAsGroup) {
			this.runAsGroup = runAsGroup;
		}

		public Boolean getRunAsNonRoot() {
			return runAsNonRoot;
		}

		public void setRunAsNonRoot(Boolean runAsNonRoot) {
			this.runAsNonRoot = runAsNonRoot;
		}

		public SeccompProfile getSeccompProfile() {
			return seccompProfile;
		}

		public void setSeccompProfile(SeccompProfile seccompProfile) {
			this.seccompProfile = seccompProfile;
		}

		public SELinuxOptions getSeLinuxOptions() {
			return seLinuxOptions;
		}

		public void setSeLinuxOptions(SELinuxOptions seLinuxOptions) {
			this.seLinuxOptions = seLinuxOptions;
		}

		public WindowsSecurityContextOptions getWindowsOptions() {
			return windowsOptions;
		}

		public void setWindowsOptions(WindowsSecurityContextOptions windowsOptions) {
			this.windowsOptions = windowsOptions;
		}
	}

	/**
	 * Adds and removes POSIX capabilities from running containers.
	 */
	public static class Capabilities {
		/**
		 * Added capabilities.
		 */
		private List<String> add;

		/**
		 * Removed capabilities.
		 */
		private List<String> drop;

		public List<String> getAdd() {
			return add;
		}

		public void setAdd(List<String> add) {
			this.add = add;
		}

		public List<String> getDrop() {
			return drop;
		}

		public void setDrop(List<String> drop) {
			this.drop = drop;
		}
	}

	/**
     * Defines a pod seccomp profile settings.
     */
	public static class SeccompProfile {

        /**
         * Type of seccomp profile.
         */
        private String type;

        /**
         * Path of the pre-configured profile on the node, relative to the kubelet's configured Seccomp profile location, only valid when type is "Localhost".
         */
        private String localhostProfile;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLocalhostProfile() {
            return localhostProfile;
        }

        public void setLocalhostProfile(String localhostProfile) {
            this.localhostProfile = localhostProfile;
        }
    }

	/**
	 * The labels to be applied to the container.
	 */
	public static class SELinuxOptions {
		/**
		 * Level label applied to the container
		 */
		private String level;

		/**
		 * Role Level label applied to the container
		 */
		private String role;

		/**
		 * Type label applied to the container
		 */
		private String type;

		/**
		 * User label applied to the container
		 */
		private String user;

		public String getLevel() {
			return level;
		}

		public void setLevel(String level) {
			this.level = level;
		}

		public String getRole() {
			return role;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getUser() {
			return user;
		}

		public void setUser(String user) {
			this.user = user;
		}
	}

	/**
	 * Sysctl defines a kernel parameter to be set on the pod.
	 */
	public static class SysctlInfo {
		/**
		 * Name of the property
		 */
		private String name;

		/**
		 * Value of the property
		 */
		private String value;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	/**
	 * Contains Windows-specific options and credentials.
	 */
	public static class WindowsSecurityContextOptions {

		/**
		 * Where the GMSA admission webhook inlines the contents of the GMSA credential spec
		 * named by the GMSACredentialSpecName field.
		 */
		private String gmsaCredentialSpec;

		/**
		 * The name of the GMSA credential spec to use.
		 */
		private String gmsaCredentialSpecName;

		/**
		 * Whether a container should be run as a 'Host Process' container.
		 */
		private Boolean hostProcess;

		/**
		 * The username in Windows to run the entrypoint of the container process.
		 */
		private String runAsUserName;

		public String getGmsaCredentialSpec() {
			return gmsaCredentialSpec;
		}

		public void setGmsaCredentialSpec(String gmsaCredentialSpec) {
			this.gmsaCredentialSpec = gmsaCredentialSpec;
		}

		public String getGmsaCredentialSpecName() {
			return gmsaCredentialSpecName;
		}

		public void setGmsaCredentialSpecName(String gmsaCredentialSpecName) {
			this.gmsaCredentialSpecName = gmsaCredentialSpecName;
		}

		public Boolean getHostProcess() {
			return hostProcess;
		}

		public void setHostProcess(Boolean hostProcess) {
			this.hostProcess = hostProcess;
		}

		public String getRunAsUserName() {
			return runAsUserName;
		}

		public void setRunAsUserName(String runAsUserName) {
			this.runAsUserName = runAsUserName;
		}
	}

	public static class Lifecycle {
        private Hook postStart;
        private Hook preStop;

        Hook getPreStop() {
            return preStop;
        }

        Hook getPostStart() {
            return postStart;
        }

        void setPostStart(Hook postStart) {
            this.postStart = postStart;
        }

        void setPreStop(Hook preStop) {
            this.preStop = preStop;

        }

        public static class Hook {
            private Exec exec;

            Exec getExec() {
                return exec;
            }

            void setExec(Exec exec) {
                this.exec = exec;
            }
        }

        public static class Exec {
            private List<String> command;

            List<String> getCommand() {
                return command;
            }

            void setCommand(List<String> command) {
                this.command = command;
            }
        }
    }

	public static class InitContainer extends ContainerProperties {
    }

    static class Container extends io.fabric8.kubernetes.api.model.Container {
    }

    public static class ContainerProperties {
        private String imageName;

        private String containerName;

        private List<String> commands;

        private List<VolumeMount> volumeMounts;

        /**
         * Environment variables to set for any deployed init container.
         */
        private String[] environmentVariables = new String[]{};

        public String getImageName() {
            return imageName;
        }

        public void setImageName(String imageName) {
            this.imageName = imageName;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public List<VolumeMount> getVolumeMounts() {
            return volumeMounts;
        }

        public void setVolumeMounts(List<VolumeMount> volumeMounts) {
            this.volumeMounts = volumeMounts;
        }

        public String[] getEnvironmentVariables() {
            return environmentVariables;
        }

        public void setEnvironmentVariables(String[] environmentVariables) {
            this.environmentVariables = environmentVariables;
        }
    }

    /**
     * The {@link RestartPolicy} to use. Defaults to {@link RestartPolicy#Always}.
     */
    private RestartPolicy restartPolicy = RestartPolicy.Always;

    /**
     * The default service account name to use for tasks.
     */
    protected static final String DEFAULT_TASK_SERVICE_ACCOUNT_NAME = "default";

    /**
     * Service account name to use for tasks, defaults to:
     * {@link #DEFAULT_TASK_SERVICE_ACCOUNT_NAME}
     */
    private String taskServiceAccountName = DEFAULT_TASK_SERVICE_ACCOUNT_NAME;

    /**
     * Obtains the {@link RestartPolicy} to use. Defaults to
     * {@link #restartPolicy}.
     *
     * @return the {@link RestartPolicy} to use
     */
    public RestartPolicy getRestartPolicy() {
        return restartPolicy;
    }

    /**
     * Sets the {@link RestartPolicy} to use.
     *
     * @param restartPolicy the {@link RestartPolicy} to use
     */
    public void setRestartPolicy(RestartPolicy restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    /**
     * Obtains the service account name to use for tasks.
     *
     * @return the service account name
     */
    public String getTaskServiceAccountName() {
        return taskServiceAccountName;
    }

    /**
     * Sets the service account name to use for tasks.
     *
     * @param taskServiceAccountName the service account name
     */
    public void setTaskServiceAccountName(String taskServiceAccountName) {
        this.taskServiceAccountName = taskServiceAccountName;
    }

    /**
     * Name of the environment variable that can define the Kubernetes namespace to use.
     */
    public static final String ENV_KEY_KUBERNETES_NAMESPACE = "KUBERNETES_NAMESPACE";

    private static String KUBERNETES_NAMESPACE = System.getenv("KUBERNETES_NAMESPACE");

    /**
     * Namespace to use.
     */
    private String namespace = KUBERNETES_NAMESPACE;

    /**
     * Secrets for a access a private registry to pull images.
     */
    private String imagePullSecret;

    /**
     * List of Secrets for a access a private registry to pull images.
     */
    private List<String> imagePullSecrets;

    /**
     * Delay in seconds when the Kubernetes liveness check of the app container should start
     * checking its health status.
     */
    private int livenessHttpProbeDelay = 1;
    /**
     * When the probe fails more times than the failure value the pod is restarted.
     */
    private int livenessHttpProbeFailure = 3;
    /**
     * When the probe passes success times it is considered live.
     */
    private int livenessHttpProbeSuccess = 1;

    /**
     * Period in seconds for performing the Kubernetes liveness check of the app container.
     */
    private int livenessHttpProbePeriod = 60;

    /**
     * Timeout in seconds for the check to wait for a response after which it is considered a failed check.
     */
    private int livenessHttpProbeTimeout = 5;
    /**
     * Timeout in seconds for the liveness check to wait for a response before the check is considered a failure.
     */
    private int livenessTcpProbeTimeout = 5;

    /**
     * Path that app container has to respond to for liveness check.
     */
    private String livenessHttpProbePath;

    /**
     * Port that app container has to respond on for liveness check.
     */
    private Integer livenessHttpProbePort = null;

    /**
     * Schema that app container has to respond on for liveness check.
     */
    private String livenessHttpProbeScheme = "HTTP";

    /**
     * Schema that app container has to respond to for readiness check.
     */
    private String readinessHttpProbeScheme = "HTTP";

    /**
     * Schema that app container has to respond to for startup check.
     */
    private String startupProbeScheme = "HTTP";

    /**
     * If present will assign to spec.shareProcessNamespace of the Pod.
     */
    private Boolean shareProcessNamespace;
    /**
     * Delay in seconds when the readiness check of the app container should start checking if
     * the module is fully up and running.
     */
    private int readinessHttpProbeDelay = 1;

    private int readinessHttpProbeSuccess = 1;

    private int readinessHttpProbeFailure = 3;

    private int readinessTcpProbeTimeout = 3;

    private int readinessTcpProbeFailure = 3;
    private int readinessTcpProbeSuccess = 1;
    /**
     * Delay in seconds when the startup check of the app container should start checking if
     * the module is fully up and running.
     */
    private int startupHttpProbeDelay = 30;
    /**
     * The number of time the http startup probe will be allowed to fail before restarting the pod.
     */

    private int startupHttpProbeFailure = 20;
    /**
     * The number of time the http startup probe will be required to pass before considering the application started.
     */
    private int startupHttpProbeSuccess = 1;
    /**
     * The number of time the tcp startup probe will be allowed to fail before restarting the pod.
     */
    private int startupTcpProbeFailure = 20;
    /**
     * The number of time the tcp startup probe will be required to pass before considering the application started.
     */
    private int startupTcpProbeSuccess = 1;
    /**
     * The number of time the command startup probe will be allowed to fail before restarting the pod.
     */
    private int startupCommandProbeFailure = 10;
    /**
     * The number of time the command startup probe will be required to pass before considering the application started.
     */
    private int startupCommandProbeSuccess = 1;
    /**
     * Period in seconds to perform the readiness check of the app container.
     */
    private int readinessHttpProbePeriod = 10;

    /**
     * Period in seconds to perform the startup check of the app container.
     */
    private int startupHttpProbePeriod = 3;

    /**
     * Timeout in seconds that the app container has to respond to its status during
     * the startup check.
     */
    private int startupHttpProbeTimeout = 5;

    /**
     * Timeout in seconds that the app container has to respond to its health status during
     * the readiness check.
     */
    private int readinessHttpProbeTimeout = 5;

    private int readinessCommandProbeFailure = 3;
    private int readinessCommandProbeSuccess = 1;

    /**
     * Path that app container has to respond to for readiness check.
     */
    private String readinessHttpProbePath;

    /**
     * Path that app container has to respond to for startup check.
     */
    private String startupHttpProbePath;
    /**
     * Port that app container has to respond on for readiness check.
     */
    private Integer readinessHttpProbePort = null;

    /**
     * Port that app container has to respond on for startup check.
     */
    private Integer startupHttpProbePort = null;
    /**
     * Delay in seconds when the liveness TCP check should start checking
     */
    private int livenessTcpProbeDelay = 10;

    private int livenessTcpProbeSuccess = 1;

    private int livenessTcpProbeFailure = 3;

    /**
     * Period in seconds to perform the liveness TCP check
     */
    private int livenessTcpProbePeriod = 60;

    /**
     * The TCP port the liveness probe should check
     */
    private Integer livenessTcpProbePort = null;

    /**
     * Delay in seconds when the readiness TCP check should start checking
     */
    private int readinessTcpProbeDelay = 1;

    /**
     * Period in seconds to perform the readiness TCP check
     */
    private int readinessTcpProbePeriod = 10;

    /**
     * The TCP port the readiness probe should check
     */
    private Integer readinessTcpProbePort = null;

    /**
     * Delay in seconds when the readiness command check should start checking
     */
    private int readinessCommandProbeDelay = 1;

    /**
     * Period in seconds to perform the readiness command check
     */
    private int readinessCommandProbePeriod = 10;

    /**
     * The command the readiness probe should use to check
     */
    private String readinessCommandProbeCommand = null;

    /**
     * Delay in seconds when the readiness TCP check should start checking
     */
    private int startupTcpProbeDelay = 30;

    private int startupTcpProbeTimeout = 5;

    /**
     * Period in seconds to perform the readiness TCP check
     */
    private int startupTcpProbePeriod = 3;

    /**
     * The TCP port the readiness probe should check
     */
    private Integer startupTcpProbePort = null;

    /**
     * Delay in seconds when the readiness command check should start checking
     */
    private int startupCommandProbeDelay = 30;

    /**
     * Period in seconds to perform the readiness command check
     */
    private int startupCommandProbePeriod = 10;

    /**
     * The command the readiness probe should use to check
     */
    private String startupCommandProbeCommand = null;

    /**
     * Delay in seconds when the liveness command check should start checking
     */
    private int livenessCommandProbeDelay = 10;

    private int livenessCommandProbeFailure = 3;
    private int livenessCommandProbeSuccess = 1;

    /**
     * Period in seconds to perform the liveness command check
     */
    private int livenessCommandProbePeriod = 10;

    /**
     * The command the liveness probe should use to check
     */
    private String livenessCommandProbeCommand = null;

    /**
     * The secret name containing the credentials to use when accessing secured probe
     * endpoints.
     */
    private String probeCredentialsSecret;

    /**
     * The probe type to use when doing health checks. Defaults to HTTP.
     */
    private ProbeType probeType = ProbeType.HTTP;

    /**
     * Memory and CPU limits (i.e. maximum needed values) to allocate for a Pod.
     */
    private LimitsResources limits = new LimitsResources();

    /**
     * Memory and CPU requests (i.e. guaranteed needed values) to allocate for a Pod.
     */
    private RequestsResources requests = new RequestsResources();

    /**
     * Tolerations to allocate for a Pod.
     */
    private List<Toleration> tolerations = new ArrayList<>();

    /**
     * Secret key references to be added to the Pod environment.
     */
    private List<SecretKeyRef> secretKeyRefs = new ArrayList<>();

    /**
     * ConfigMap key references to be added to the Pod environment.
     */
    private List<ConfigMapKeyRef> configMapKeyRefs = new ArrayList<>();

    /**
     * ConfigMap references to be added to the Pod environment.
     */
    private List<String> configMapRefs = new ArrayList<>();

    /**
     * Secret references to be added to the Pod environment.
     */
    private List<String> secretRefs = new ArrayList<>();

    /**
     * Resources to assign for VolumeClaimTemplates (identified by metadata name) inside
     * StatefulSet.
     */
    private StatefulSet statefulSet = new StatefulSet();

    /**
     * Environment variables to set for any deployed app container. To be used for service
     * binding.
     */
    private String[] environmentVariables = new String[]{};

    /**
     * Entry point style used for the Docker image. To be used to determine how to pass in
     * properties.
     */
    private EntryPointStyle entryPointStyle = EntryPointStyle.exec;

    /**
     * Create a "LoadBalancer" for the service created for each app. This facilitates
     * assignment of external IP to app.
     */
    private boolean createLoadBalancer = false;

    /**
     * Service annotations to set for the service created for each app.
     */
    private String serviceAnnotations = null;

    /**
     * Pod annotations to set for the pod created for each deployment.
     */
    private String podAnnotations;

    /**
     * Job annotations to set for the pod or job created for a job.
     */
    private String jobAnnotations;

    /**
     * Time to wait for load balancer to be available before attempting delete of service (in
     * minutes).
     */
    private int minutesToWaitForLoadBalancer = 5;

    /**
     * Maximum allowed restarts for app that fails due to an error or excessive resource use.
     */
    private int maxTerminatedErrorRestarts = 2;

    /**
     * Maximum allowed restarts for app that is in a CrashLoopBackOff.
     */
    private int maxCrashLoopBackOffRestarts = 4;

    /**
     * The image pull policy to use for Pod deployments in Kubernetes.
     */
    private ImagePullPolicy imagePullPolicy = ImagePullPolicy.IfNotPresent;

    /**
     * Volume mounts that a container is requesting. This can be specified as a deployer
     * property or as an app deployment property. Deployment properties will override deployer
     * properties.
     */
    private List<VolumeMount> volumeMounts = new ArrayList<>();

    /**
     * The volumes that a Kubernetes instance supports. See
     * https://kubernetes.io/docs/user-guide/volumes/#types-of-volumes This can be specified
     * as a deployer property or as an app deployment property. Deployment properties will
     * override deployer properties.
     */
    private List<Volume> volumes = new ArrayList<>();

    /**
     * The hostNetwork setting for the deployments. See
     * https://kubernetes.io/docs/api-reference/v1/definitions/#_v1_podspec This can be
     * specified as a deployer property or as an app deployment property. Deployment
     * properties will override deployer properties.
     */
    private boolean hostNetwork = false;

    /**
     * Create a "Job" instead of just a "Pod" when launching tasks. See
     * https://kubernetes.io/docs/concepts/workloads/controllers/jobs-run-to-completion/
     */
    private boolean createJob = false;

    /**
     * The node selector to use in key:value format, comma separated
     */
    private String nodeSelector;

    /**
     * Service account name to use for app deployments
     */
    private String deploymentServiceAccountName;

    /**
     * The security context to apply to created pod's.
     */
    private PodSecurityContext podSecurityContext;

    /**
     * The security context to apply to created pod's main container.
     */
    private ContainerSecurityContext containerSecurityContext;

    /**
     * The node affinity rules to apply.
     */
    private NodeAffinity nodeAffinity;

    /**
     * The pod affinity rules to apply
     */
    private PodAffinity podAffinity;

    /**
     * The pod anti-affinity rules to apply
     */
    private PodAntiAffinity podAntiAffinity;

    /**
     * A custom init container image name to use when creating a StatefulSet
     */
    private String statefulSetInitContainerImageName;

    /**
     * A custom init container to apply.
     */
    private InitContainer initContainer;

    /**
     * Lifecycle spec to apply.
     */
    private Lifecycle lifecycle = new Lifecycle();

    /**
     * The additional containers one can add to the main application container.
     */
    private List<Container> additionalContainers;

    /**
     * Deployment label to be applied to Deployment, StatefulSet, JobSpec etc.,
     */
    private String deploymentLabels;

    /**
     * Pod priorityClassName. The user should create a PriorityClass before setting this property.
     */
    private String priorityClassName;

    private AppAdmin appAdmin = new AppAdmin();

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getImagePullSecret() {
        return imagePullSecret;
    }

    public void setImagePullSecret(String imagePullSecret) {
        this.imagePullSecret = imagePullSecret;
    }

    public List<String> getImagePullSecrets() {
        return imagePullSecrets;
    }

    public void setImagePullSecrets(List<String> imagePullSecrets) {
        this.imagePullSecrets = imagePullSecrets;
    }

	public static class CronConfig {
		private String concurrencyPolicy;

		private Integer ttlSecondsAfterFinished;

		public String getConcurrencyPolicy() {
			return concurrencyPolicy;
		}

		public void setConcurrencyPolicy(String concurrencyPolicy) {
			this.concurrencyPolicy = concurrencyPolicy;
		}

		public Integer getTtlSecondsAfterFinished() {
			return ttlSecondsAfterFinished;
		}

		public void setTtlSecondsAfterFinished(Integer ttlSecondsAfterFinished) {
			this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
		}
	}

    public Boolean getShareProcessNamespace() {
        return shareProcessNamespace;
    }

    public void setShareProcessNamespace(Boolean shareProcessNamespace) {
        this.shareProcessNamespace = shareProcessNamespace;
    }

    public String getPriorityClassName() {
        return priorityClassName;
    }

    public void setPriorityClassName(String priorityClassName) {
        this.priorityClassName = priorityClassName;
    }

    /**
     * @deprecated @{see {@link #getLivenessHttpProbeDelay()}}
     */
    @Deprecated
    public int getLivenessProbeDelay() {
        return livenessHttpProbeDelay;
    }

    /**
     * @deprecated @{see {@link #setLivenessHttpProbeDelay(int)}}
     */
    @Deprecated
    public void setLivenessProbeDelay(int livenessProbeDelay) {
        this.livenessHttpProbeDelay = livenessProbeDelay;
    }

    /**
     * @deprecated @{see {@link #getLivenessHttpProbePeriod()}}
     */
    @Deprecated
    public int getLivenessProbePeriod() {
        return livenessHttpProbePeriod;
    }

    /**
     * @deprecated @{see {@link #setLivenessHttpProbePeriod(int)}}
     */
    @Deprecated
    public void setLivenessProbePeriod(int livenessProbePeriod) {
        this.livenessHttpProbePeriod = livenessProbePeriod;
    }

    /**
     * @deprecated @{see {@link #getLivenessHttpProbeTimeout()}}
     */
    @Deprecated
    public int getLivenessProbeTimeout() {
        return livenessHttpProbeTimeout;
    }

    /**
     * @deprecated @{see {@link #setLivenessHttpProbeTimeout(int)}}
     */
    @Deprecated
    public void setLivenessProbeTimeout(int livenessProbeTimeout) {
        this.livenessHttpProbeTimeout = livenessProbeTimeout;
    }

    /**
     * @deprecated @{see {@link #getLivenessHttpProbePath()}}
     */
    @Deprecated
    public String getLivenessProbePath() {
        return livenessHttpProbePath;
    }

    /**
     * @deprecated @{see {@link #setLivenessHttpProbePath(String)}}
     */
    @Deprecated
    public void setLivenessProbePath(String livenessProbePath) {
        this.livenessHttpProbePath = livenessProbePath;
    }

    /**
     * @deprecated @{see {@link #getLivenessHttpProbePort()}}
     */
    @Deprecated
    public Integer getLivenessProbePort() {
        return livenessHttpProbePort;
    }

    /**
     * @deprecated @{see {@link #setLivenessHttpProbePort(Integer)}}
     */
    @Deprecated
    public void setLivenessProbePort(Integer livenessProbePort) {
        this.livenessHttpProbePort = livenessProbePort;
    }


    public int getLivenessTcpProbeSuccess() {
        return livenessTcpProbeSuccess;
    }

    public void setLivenessTcpProbeSuccess(int livenessTcpProbeSuccess) {
        this.livenessTcpProbeSuccess = livenessTcpProbeSuccess;
    }

    public int getLivenessTcpProbeFailure() {
        return livenessTcpProbeFailure;
    }

    public void setLivenessTcpProbeFailure(int livenessTcpProbeFailure) {
        this.livenessTcpProbeFailure = livenessTcpProbeFailure;
    }

    public int getLivenessHttpProbeDelay() {
        return livenessHttpProbeDelay;
    }

    public void setLivenessHttpProbeDelay(int livenessHttpProbeDelay) {
        this.livenessHttpProbeDelay = livenessHttpProbeDelay;
    }

    public int getLivenessTcpProbeTimeout() {
        return livenessTcpProbeTimeout;
    }

    public void setLivenessTcpProbeTimeout(int livenessTcpProbeTimeout) {
        this.livenessTcpProbeTimeout = livenessTcpProbeTimeout;
    }


    public int getLivenessHttpProbePeriod() {
        return livenessHttpProbePeriod;
    }

    public void setLivenessHttpProbePeriod(int livenessHttpProbePeriod) {
        this.livenessHttpProbePeriod = livenessHttpProbePeriod;
    }

    public int getLivenessHttpProbeTimeout() {
        return livenessHttpProbeTimeout;
    }

    public void setLivenessHttpProbeTimeout(int livenessHttpProbeTimeout) {
        this.livenessHttpProbeTimeout = livenessHttpProbeTimeout;
    }

    public String getLivenessHttpProbePath() {
        return livenessHttpProbePath;
    }

    public void setLivenessHttpProbePath(String livenessHttpProbePath) {
        this.livenessHttpProbePath = livenessHttpProbePath;
    }

    public Integer getLivenessHttpProbePort() {
        return livenessHttpProbePort;
    }

    public void setLivenessHttpProbePort(Integer livenessHttpProbePort) {
        this.livenessHttpProbePort = livenessHttpProbePort;
    }

    public int getStartupHttpProbeTimeout() {
        return startupHttpProbeTimeout;
    }

    public int getStartupHttpProbeFailure() {
        return startupHttpProbeFailure;
    }

    public void setStartupHttpProbeFailure(int startupHttpProbeFailure) {
        this.startupHttpProbeFailure = startupHttpProbeFailure;
    }

    public void setStartupProbeFailure(int startupHttpProbeFailure) {
        this.startupHttpProbeFailure = startupHttpProbeFailure;
    }

    public int getLivenessHttpProbeFailure() {
        return livenessHttpProbeFailure;
    }

    public void setLivenessHttpProbeFailure(int livenessHttpProbeFailure) {
        this.livenessHttpProbeFailure = livenessHttpProbeFailure;
    }

    public void setLivenessProbeFailure(int livenessHttpProbeFailure) {
        this.livenessHttpProbeFailure = livenessHttpProbeFailure;
    }

    public int getLivenessHttpProbeSuccess() {
        return livenessHttpProbeSuccess;
    }

    public void setLivenessHttpProbeSuccess(int livenessHttpProbeSuccess) {
        this.livenessHttpProbeSuccess = livenessHttpProbeSuccess;
    }

    public void setLivenessProbeSuccess(int livenessHttpProbeSuccess) {
        this.livenessHttpProbeSuccess = livenessHttpProbeSuccess;
    }

    public int getStartupHttpProbeSuccess() {
        return startupHttpProbeSuccess;
    }

    public void setStartupHttpProbeSuccess(int startupHttpProbeSuccess) {
        this.startupHttpProbeSuccess = startupHttpProbeSuccess;
    }

    public void setStartupProbeSuccess(int startupHttpProbeSuccess) {
        this.startupHttpProbeSuccess = startupHttpProbeSuccess;
    }

    public int getStartupTcpProbeFailure() {
        return startupTcpProbeFailure;
    }

    public void setStartupTcpProbeFailure(int startupTcpProbeFailure) {
        this.startupTcpProbeFailure = startupTcpProbeFailure;
    }

    public int getStartupTcpProbeSuccess() {
        return startupTcpProbeSuccess;
    }

    public void setStartupTcpProbeSuccess(int startupTcpProbeSuccess) {
        this.startupTcpProbeSuccess = startupTcpProbeSuccess;
    }

    public int getStartupCommandProbeFailure() {
        return startupCommandProbeFailure;
    }

    public void setStartupCommandProbeFailure(int startupCommandProbeFailure) {
        this.startupCommandProbeFailure = startupCommandProbeFailure;
    }

    public int getStartupCommandProbeSuccess() {
        return startupCommandProbeSuccess;
    }

    public void setStartupCommandProbeSuccess(int startupCommandProbeSuccess) {
        this.startupCommandProbeSuccess = startupCommandProbeSuccess;
    }

    public int getLivenessCommandProbeFailure() {
        return livenessCommandProbeFailure;
    }

    public void setLivenessCommandProbeFailure(int livenessCommandProbeFailure) {
        this.livenessCommandProbeFailure = livenessCommandProbeFailure;
    }

    public int getLivenessCommandProbeSuccess() {
        return livenessCommandProbeSuccess;
    }

    public void setLivenessCommandProbeSuccess(int livenessCommandProbeSuccess) {
        this.livenessCommandProbeSuccess = livenessCommandProbeSuccess;
    }

    public int getStartupHttpProbeDelay() {
        return startupHttpProbeDelay;
    }

    public void setStartupHttpProbeTimeout(int startupHttpProbeTimeout) {
        this.startupHttpProbeTimeout = startupHttpProbeTimeout;
    }

    public void setStartupProbeTimeout(int startupHttpProbeTimeout) {
        this.startupHttpProbeTimeout = startupHttpProbeTimeout;
    }

    public void setStartupHttpProbeDelay(int startupHttpProbeDelay) {
        this.startupHttpProbeDelay = startupHttpProbeDelay;
    }

    public void setStartupProbeDelay(int startupHttpProbeDelay) {
        this.startupHttpProbeDelay = startupHttpProbeDelay;
    }

    public String getStartupHttpProbePath() {
        return startupHttpProbePath;
    }

    public void setStartupHttpProbePath(String startupHttpProbePath) {
        this.startupHttpProbePath = startupHttpProbePath;
    }

    public Integer getStartupHttpProbePort() {
        return startupHttpProbePort;
    }

    public void setStartupHttpProbePort(Integer startupHttpProbePort) {
        this.startupHttpProbePort = startupHttpProbePort;
    }

    public void setStartupProbePort(Integer startupHttpProbePort) {
        this.startupHttpProbePort = startupHttpProbePort;
    }

    public String getStartupProbeScheme() {
        return startupProbeScheme;
    }

    public void setStartupProbeScheme(String startupProbeScheme) {
        this.startupProbeScheme = startupProbeScheme;
    }

    public int getStartupHttpProbePeriod() {
        return startupHttpProbePeriod;
    }

    public void setStartupHttpProbePeriod(int startupHttpProbePeriod) {
        this.startupHttpProbePeriod = startupHttpProbePeriod;
    }

    public int getStartupProbePeriod() {
        return startupHttpProbePeriod;
    }

    public void setStartupProbePeriod(int startupHttpProbePeriod) {
        this.startupHttpProbePeriod = startupHttpProbePeriod;
    }

    public int getStartupTcpProbeDelay() {
        return startupTcpProbeDelay;
    }

    public void setStartupTcpProbeDelay(int startupTcpProbeDelay) {
        this.startupTcpProbeDelay = startupTcpProbeDelay;
    }

    public int getStartupTcpProbeTimeout() {
        return startupTcpProbeTimeout;
    }

    public void setStartupTcpProbeTimeout(int startupTcpProbeTimeout) {
        this.startupTcpProbeTimeout = startupTcpProbeTimeout;
    }
	
	/**
	 * Cron configuration for job scheduling
	 */
	private CronConfig cron = new CronConfig();

    public int getStartupTcpProbePeriod() {
        return startupTcpProbePeriod;
    }

    public void setStartupTcpProbePeriod(int startupTcpProbePeriod) {
        this.startupTcpProbePeriod = startupTcpProbePeriod;
    }

    public Integer getStartupTcpProbePort() {
        return startupTcpProbePort;
    }

    public void setStartupTcpProbePort(Integer startupTcpProbePort) {
        this.startupTcpProbePort = startupTcpProbePort;
    }

    public int getStartupCommandProbeDelay() {
        return startupCommandProbeDelay;
    }

    public void setStartupCommandProbeDelay(int startupCommandProbeDelay) {
        this.startupCommandProbeDelay = startupCommandProbeDelay;
    }

    public int getStartupCommandProbePeriod() {
        return startupCommandProbePeriod;
    }

    public void setStartupCommandProbePeriod(int startupCommandProbePeriod) {
        this.startupCommandProbePeriod = startupCommandProbePeriod;
    }

    public String getStartupCommandProbeCommand() {
        return startupCommandProbeCommand;
    }

    public void setStartupCommandProbeCommand(String startupCommandProbeCommand) {
        this.startupCommandProbeCommand = startupCommandProbeCommand;
    }

    /**
     * @deprecated @{see {@link #getReadinessHttpProbeDelay()}}
     */
    @Deprecated
    public int getReadinessProbeDelay() {
        return readinessHttpProbeDelay;
    }

    /**
     * @deprecated @{see {@link #setReadinessHttpProbeDelay(int)}}
     */
    @Deprecated
    public void setReadinessProbeDelay(int readinessProbeDelay) {
        this.readinessHttpProbeDelay = readinessProbeDelay;
    }

    /**
     * @deprecated @{see {@link #getReadinessHttpProbePeriod()}}
     */
    @Deprecated
    public int getReadinessProbePeriod() {
        return readinessHttpProbePeriod;
    }

    public int getReadinessTcpProbeFailure() {
        return readinessTcpProbeFailure;
    }

    public void setReadinessTcpProbeFailure(int readinessTcpProbeFailure) {
        this.readinessTcpProbeFailure = readinessTcpProbeFailure;
    }

    public int getReadinessTcpProbeSuccess() {
        return readinessTcpProbeSuccess;
    }

    public void setReadinessTcpProbeSuccess(int readinessTcpProbeSuccess) {
        this.readinessTcpProbeSuccess = readinessTcpProbeSuccess;
    }

    public int getReadinessHttpProbeSuccess() {
        return readinessHttpProbeSuccess;
    }

    public void setReadinessHttpProbeSuccess(int readinessHttpProbeSuccess) {
        this.readinessHttpProbeSuccess = readinessHttpProbeSuccess;
    }

    public int getReadinessHttpProbeFailure() {
        return readinessHttpProbeFailure;
    }

    public void setReadinessHttpProbeFailure(int readinessHttpProbeFailure) {
        this.readinessHttpProbeFailure = readinessHttpProbeFailure;
    }

    public void setReadinessProbeFailure(int readinessHttpProbeFailure) {
        this.readinessHttpProbeFailure = readinessHttpProbeFailure;
    }


    public int getReadinessCommandProbeFailure() {
        return readinessCommandProbeFailure;
    }

    public void setReadinessCommandProbeFailure(int readinessCommandProbeFailure) {
        this.readinessCommandProbeFailure = readinessCommandProbeFailure;
    }

    public int getReadinessCommandProbeSuccess() {
        return readinessCommandProbeSuccess;
    }

    public void setReadinessCommandProbeSuccess(int readinessCommandProbeSuccess) {
        this.readinessCommandProbeSuccess = readinessCommandProbeSuccess;
    }

    /**
     * @deprecated @{see {@link #setReadinessHttpProbePeriod(int)}}
     */
    @Deprecated
    public void setReadinessProbePeriod(int readinessProbePeriod) {
        this.readinessHttpProbePeriod = readinessProbePeriod;
    }

    /**
     * @deprecated @{see {@link #getReadinessHttpProbeTimeout()}}
     */
    @Deprecated
    public int getReadinessProbeTimeout() {
        return readinessHttpProbeTimeout;
    }

    /**
     * @deprecated @{see {@link #setReadinessHttpProbeTimeout(int)}}
     */
    @Deprecated
    public void setReadinessProbeTimeout(int readinessProbeTimeout) {
        this.readinessHttpProbeTimeout = readinessProbeTimeout;
    }

    /**
     * @deprecated @{see {@link #getReadinessHttpProbePath()}}
     */
    @Deprecated
    public String getReadinessProbePath() {
        return readinessHttpProbePath;
    }

    /**
     * @deprecated @{see {@link #setReadinessHttpProbePath(String)}}
     */
    @Deprecated
    public void setReadinessProbePath(String readinessProbePath) {
        this.readinessHttpProbePath = readinessProbePath;
    }

    /**
     * @deprecated @{see {@link #getReadinessHttpProbePort()}}
     */
    @Deprecated
    public Integer getReadinessProbePort() {
        return readinessHttpProbePort;
    }

    /**
     * @deprecated @{see {@link #setReadinessHttpProbePort(Integer)}}
     */
    @Deprecated
    public void setReadinessProbePort(Integer readinessProbePort) {
        this.readinessHttpProbePort = readinessProbePort;
    }

    public int getReadinessHttpProbeDelay() {
        return readinessHttpProbeDelay;
    }

    public void setReadinessHttpProbeDelay(int readinessHttpProbeDelay) {
        this.readinessHttpProbeDelay = readinessHttpProbeDelay;
    }

    public int getReadinessHttpProbePeriod() {
        return readinessHttpProbePeriod;
    }

    public void setReadinessHttpProbePeriod(int readinessHttpProbePeriod) {
        this.readinessHttpProbePeriod = readinessHttpProbePeriod;
    }

    public int getReadinessTcpProbeTimeout() {
        return readinessTcpProbeTimeout;
    }

    public void setReadinessTcpProbeTimeout(int readinessTcpProbeTimeout) {
        this.readinessTcpProbeTimeout = readinessTcpProbeTimeout;
    }

    public int getReadinessHttpProbeTimeout() {
        return readinessHttpProbeTimeout;
    }

    public void setReadinessHttpProbeTimeout(int readinessHttpProbeTimeout) {
        this.readinessHttpProbeTimeout = readinessHttpProbeTimeout;
    }

    public String getReadinessHttpProbePath() {
        return readinessHttpProbePath;
    }

    public void setReadinessHttpProbePath(String readinessHttpProbePath) {
        this.readinessHttpProbePath = readinessHttpProbePath;
    }

    public Integer getReadinessHttpProbePort() {
        return readinessHttpProbePort;
    }

    public void setReadinessHttpProbePort(Integer readinessHttpProbePort) {
        this.readinessHttpProbePort = readinessHttpProbePort;
    }

    public int getLivenessTcpProbeDelay() {
        return livenessTcpProbeDelay;
    }

    public void setLivenessTcpProbeDelay(int livenessTcpProbeDelay) {
        this.livenessTcpProbeDelay = livenessTcpProbeDelay;
    }

    public int getLivenessTcpProbePeriod() {
        return livenessTcpProbePeriod;
    }

    public void setLivenessTcpProbePeriod(int livenessTcpProbePeriod) {
        this.livenessTcpProbePeriod = livenessTcpProbePeriod;
    }

    public Integer getLivenessTcpProbePort() {
        return livenessTcpProbePort;
    }

    public void setLivenessTcpProbePort(Integer livenessTcpProbePort) {
        this.livenessTcpProbePort = livenessTcpProbePort;
    }

    public int getReadinessTcpProbeDelay() {
        return readinessTcpProbeDelay;
    }

    public void setReadinessTcpProbeDelay(int readinessTcpProbeDelay) {
        this.readinessTcpProbeDelay = readinessTcpProbeDelay;
    }

    public int getReadinessTcpProbePeriod() {
        return readinessTcpProbePeriod;
    }

    public void setReadinessTcpProbePeriod(int readinessTcpProbePeriod) {
        this.readinessTcpProbePeriod = readinessTcpProbePeriod;
    }

    public Integer getReadinessTcpProbePort() {
        return readinessTcpProbePort;
    }

    public void setReadinessTcpProbePort(Integer readinessTcpProbePort) {
        this.readinessTcpProbePort = readinessTcpProbePort;
    }

    public int getReadinessCommandProbeDelay() {
        return readinessCommandProbeDelay;
    }

    public void setReadinessCommandProbeDelay(int readinessCommandProbeDelay) {
        this.readinessCommandProbeDelay = readinessCommandProbeDelay;
    }

    public int getReadinessCommandProbePeriod() {
        return readinessCommandProbePeriod;
    }

    public void setReadinessCommandProbePeriod(int readinessCommandProbePeriod) {
        this.readinessCommandProbePeriod = readinessCommandProbePeriod;
    }

    public String getReadinessCommandProbeCommand() {
        return readinessCommandProbeCommand;
    }

    public void setReadinessCommandProbeCommand(String readinessCommandProbeCommand) {
        this.readinessCommandProbeCommand = readinessCommandProbeCommand;
    }

    public int getLivenessCommandProbeDelay() {
        return livenessCommandProbeDelay;
    }

    public void setLivenessCommandProbeDelay(int livenessCommandProbeDelay) {
        this.livenessCommandProbeDelay = livenessCommandProbeDelay;
    }

    public int getLivenessCommandProbePeriod() {
        return livenessCommandProbePeriod;
    }

    public void setLivenessCommandProbePeriod(int livenessCommandProbePeriod) {
        this.livenessCommandProbePeriod = livenessCommandProbePeriod;
    }

    public String getLivenessCommandProbeCommand() {
        return livenessCommandProbeCommand;
    }

    public void setLivenessCommandProbeCommand(String livenessCommandProbeCommand) {
        this.livenessCommandProbeCommand = livenessCommandProbeCommand;
    }

    public String getProbeCredentialsSecret() {
        return probeCredentialsSecret;
    }

    public void setProbeCredentialsSecret(String probeCredentialsSecret) {
        this.probeCredentialsSecret = probeCredentialsSecret;
    }

    public ProbeType getProbeType() {
        return probeType;
    }

    public void setProbeType(ProbeType probeType) {
        this.probeType = probeType;
    }

    public StatefulSet getStatefulSet() {
        return statefulSet;
    }

    public void setStatefulSet(
            StatefulSet statefulSet) {
        this.statefulSet = statefulSet;
    }

    public List<Toleration> getTolerations() {
        return tolerations;
    }

    public void setTolerations(List<Toleration> tolerations) {
        this.tolerations = tolerations;
    }

    public List<SecretKeyRef> getSecretKeyRefs() {
        return secretKeyRefs;
    }

    public void setSecretKeyRefs(List<SecretKeyRef> secretKeyRefs) {
        this.secretKeyRefs = secretKeyRefs;
    }

    public List<ConfigMapKeyRef> getConfigMapKeyRefs() {
        return configMapKeyRefs;
    }

    public void setConfigMapKeyRefs(List<ConfigMapKeyRef> configMapKeyRefs) {
        this.configMapKeyRefs = configMapKeyRefs;
    }

    public List<String> getConfigMapRefs() {
        return configMapRefs;
    }

    public void setConfigMapRefs(List<String> configMapRefs) {
        this.configMapRefs = configMapRefs;
    }

    public List<String> getSecretRefs() {
        return secretRefs;
    }

    public void setSecretRefs(List<String> secretRefs) {
        this.secretRefs = secretRefs;
    }

    public String[] getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(String[] environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public EntryPointStyle getEntryPointStyle() {
        return entryPointStyle;
    }

    public void setEntryPointStyle(EntryPointStyle entryPointStyle) {
        this.entryPointStyle = entryPointStyle;
    }

    public boolean isCreateLoadBalancer() {
        return createLoadBalancer;
    }

    public void setCreateLoadBalancer(boolean createLoadBalancer) {
        this.createLoadBalancer = createLoadBalancer;
    }

    public String getServiceAnnotations() {
        return serviceAnnotations;
    }

    public void setServiceAnnotations(String serviceAnnotations) {
        this.serviceAnnotations = serviceAnnotations;
    }

    public String getPodAnnotations() {
        return podAnnotations;
    }

    public void setPodAnnotations(String podAnnotations) {
        this.podAnnotations = podAnnotations;
    }

    public String getJobAnnotations() {
        return jobAnnotations;
    }

    public void setJobAnnotations(String jobAnnotations) {
        this.jobAnnotations = jobAnnotations;
    }

    public int getMinutesToWaitForLoadBalancer() {
        return minutesToWaitForLoadBalancer;
    }

    public void setMinutesToWaitForLoadBalancer(int minutesToWaitForLoadBalancer) {
        this.minutesToWaitForLoadBalancer = minutesToWaitForLoadBalancer;
    }

    public int getMaxTerminatedErrorRestarts() {
        return maxTerminatedErrorRestarts;
    }

    public void setMaxTerminatedErrorRestarts(int maxTerminatedErrorRestarts) {
        this.maxTerminatedErrorRestarts = maxTerminatedErrorRestarts;
    }

    public int getMaxCrashLoopBackOffRestarts() {
        return maxCrashLoopBackOffRestarts;
    }

    public void setMaxCrashLoopBackOffRestarts(int maxCrashLoopBackOffRestarts) {
        this.maxCrashLoopBackOffRestarts = maxCrashLoopBackOffRestarts;
    }

    public ImagePullPolicy getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public LimitsResources getLimits() {
        return limits;
    }

    public void setLimits(LimitsResources limits) {
        this.limits = limits;
    }

    public RequestsResources getRequests() {
        return requests;
    }

    public void setRequests(RequestsResources requests) {
        this.requests = requests;
    }

    public List<VolumeMount> getVolumeMounts() {
        return volumeMounts;
    }

    public void setVolumeMounts(List<VolumeMount> volumeMounts) {
        this.volumeMounts = volumeMounts;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }

    public boolean isHostNetwork() {
        return hostNetwork;
    }

    public void setHostNetwork(boolean hostNetwork) {
        this.hostNetwork = hostNetwork;
    }

    public boolean isCreateJob() {
        return createJob;
    }

    public void setCreateJob(boolean createJob) {
        this.createJob = createJob;
    }

    public String getDeploymentServiceAccountName() {
        return deploymentServiceAccountName;
    }

    public void setDeploymentServiceAccountName(String deploymentServiceAccountName) {
        this.deploymentServiceAccountName = deploymentServiceAccountName;
    }

    public int getMaximumConcurrentTasks() {
        return maximumConcurrentTasks;
    }

    public void setMaximumConcurrentTasks(int maximumConcurrentTasks) {
        this.maximumConcurrentTasks = maximumConcurrentTasks;
    }

    public void setNodeSelector(String nodeSelector) {
        this.nodeSelector = nodeSelector;
    }

    public String getNodeSelector() {
        return nodeSelector;
    }

    public void setPodSecurityContext(PodSecurityContext podSecurityContext) {
        this.podSecurityContext = podSecurityContext;
    }

    public PodSecurityContext getPodSecurityContext() {
        return podSecurityContext;
    }

    public void setContainerSecurityContext(ContainerSecurityContext containerSecurityContext) {
        this.containerSecurityContext = containerSecurityContext;
    }

    public ContainerSecurityContext getContainerSecurityContext() {
        return containerSecurityContext;
    }

    public NodeAffinity getNodeAffinity() {
        return nodeAffinity;
    }

    public void setNodeAffinity(NodeAffinity nodeAffinity) {
        this.nodeAffinity = nodeAffinity;
    }

    public PodAffinity getPodAffinity() {
        return podAffinity;
    }

    public void setPodAffinity(PodAffinity podAffinity) {
        this.podAffinity = podAffinity;
    }

    public PodAntiAffinity getPodAntiAffinity() {
        return podAntiAffinity;
    }

    public void setPodAntiAffinity(PodAntiAffinity podAntiAffinity) {
        this.podAntiAffinity = podAntiAffinity;
    }

    public String getStatefulSetInitContainerImageName() {
        return statefulSetInitContainerImageName;
    }

    public void setStatefulSetInitContainerImageName(String statefulSetInitContainerImageName) {
        this.statefulSetInitContainerImageName = statefulSetInitContainerImageName;
    }

    public InitContainer getInitContainer() {
        return initContainer;
    }

    public void setInitContainer(InitContainer initContainer) {
        this.initContainer = initContainer;
    }

    public List<Container> getAdditionalContainers() {
        return this.additionalContainers;
    }

    public void setAdditionalContainers(List<Container> additionalContainers) {
        this.additionalContainers = additionalContainers;
    }

    public String getLivenessHttpProbeScheme() {
        return livenessHttpProbeScheme;
    }

    public void setLivenessHttpProbeScheme(String livenessHttpProbeScheme) {
        this.livenessHttpProbeScheme = livenessHttpProbeScheme;
    }

    public String getReadinessHttpProbeScheme() {
        return readinessHttpProbeScheme;
    }

    public void setReadinessHttpProbeScheme(String readinessHttpProbeScheme) {
        this.readinessHttpProbeScheme = readinessHttpProbeScheme;
    }

    Lifecycle getLifecycle() {
        return lifecycle;
    }

    void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getDeploymentLabels() {
        return deploymentLabels;
    }

    public void setDeploymentLabels(String deploymentLabels) {
        this.deploymentLabels = deploymentLabels;
    }

    public AppAdmin getAppAdmin() {
        return appAdmin;
    }

    public void setAppAdmin(AppAdmin appAdmin) {
        this.appAdmin = appAdmin;
    }

	public CronConfig getCron() {
		return cron;
	}

	public void setCron(CronConfig cron) {
		this.cron = cron;
	}
}
