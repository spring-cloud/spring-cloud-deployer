/*
 * Copyright 2020 the original author or authors.
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
 * Configuration properties for the Kubernetes Task Launcher.
 *
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.kubernetes")
public class KubernetesTaskLauncherProperties {
	/**
	 * The {@link RestartPolicy} to use. Defaults to {@link RestartPolicy#Never}.
	 */
	private RestartPolicy restartPolicy = RestartPolicy.Never;

	/**
	 * The backoff limit to specify the number of retries before considering a Job as failed.
	 */
	private Integer backoffLimit;

	/**
	 * Obtains the {@link RestartPolicy} to use. Defaults to
	 * {@link KubernetesTaskLauncherProperties#restartPolicy}.
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
	 * Get the BackoffLimit value
	 * @return the integer value of BackoffLimit
	 */
	public Integer getBackoffLimit() {
		return backoffLimit;
	}

	/**
	 * Sets the BackoffLimit.
	 *
	 * @param backoffLimit the integer value of BackoffLimit
	 */
	public void setBackoffLimit(Integer backoffLimit) {
		this.backoffLimit = backoffLimit;
	}
}
