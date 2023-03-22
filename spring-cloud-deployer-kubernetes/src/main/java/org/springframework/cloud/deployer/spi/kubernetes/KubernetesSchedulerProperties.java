/*
 * Copyright 2018-2021 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kubernetes Scheduler.
 *
 * @author Chris Schaefer
 */
@Deprecated
@ConfigurationProperties(prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX)
public class KubernetesSchedulerProperties extends KubernetesDeployerProperties {
	/**
	 * Namespace to use for Kubernetes Scheduler properties.
	 */
	public static final String KUBERNETES_SCHEDULER_PROPERTIES_PREFIX = "spring.cloud.scheduler.kubernetes";

	/**
	 * The {@link RestartPolicy} to use. Defaults to {@link RestartPolicy#Never}.
	 */
	private RestartPolicy restartPolicy = RestartPolicy.Never;

	/**
	 * The default service account name to use for tasks.
	 */
	protected static final String DEFAULT_TASK_SERVICE_ACCOUNT_NAME = "default";

	/**
	 * Service account name to use for tasks, defaults to:
	 * {@link KubernetesSchedulerProperties#DEFAULT_TASK_SERVICE_ACCOUNT_NAME}
	 */
	private String taskServiceAccountName = DEFAULT_TASK_SERVICE_ACCOUNT_NAME;

	/**
	 * Obtains the {@link RestartPolicy} to use. Defaults to
	 * {@link KubernetesSchedulerProperties#restartPolicy}.
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
}
