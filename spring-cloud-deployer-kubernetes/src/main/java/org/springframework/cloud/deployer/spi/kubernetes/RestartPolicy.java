/*
 * Copyright 2018-2020 the original author or authors.
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

/**
 * Defines restart policies that are available.
 *
 * @author Chris Schaefer
 */
public enum RestartPolicy {
	/**
	 * Always restart a failed container.
	 */
	Always,

	/**
	 * Restart a failed container with an exponential back-off delay, capped at 5 minutes.
	 */
	OnFailure,

	/**
	 * Never restarts a successful or failed container.
	 */
	Never
}
