/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.task;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Status of a task launch which is initially constructed from an
 * {@link org.springframework.cloud.deployer.spi.core.AppDeploymentRequest} and
 * runtime properties by a {@link TaskLauncher}.
 * <p>
 * Consumers of the SPI obtain task status via
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher#status},
 * whereas SPI implementations create instances of this class via
 * {@link #TaskStatus(String, LaunchState, Map)}
 *
 * @author Patrick Peralta
 * @author Mark Fisher
 */
public class TaskStatus {

	/**
	 * The id of the task this status is for.
	 */
	private final String id;

	/**
	 * The {@link LaunchState} of the task.
	 */
	private final LaunchState state;

	/**
	 * A map of attributes for the task.
	 */
	private final Map<String, String> attributes;

	/**
	 * Construct a new {@code TaskStatus}.
	 * @param id the id of the task launch this status is for
	 * @param state the {@link LaunchState} of the task
	 * @param attributes map of attributes for the task
	 */
	public TaskStatus(String id, LaunchState state, Map<String, String> attributes) {
		this.id = id;
		this.state = state;
		this.attributes = attributes == null ? Collections.<String, String> emptyMap()
				: Collections.unmodifiableMap(new HashMap<>(attributes));
	}

	/**
	 * Return the task launch id for the task.
	 * @return task launch id
	 */
	public String getTaskLaunchId() {
		return id;
	}

	/**
	 * Return the state for the the task.
	 *
	 * @return state for the task
	 */
	public LaunchState getState() {
		return this.state;
	}

	/**
	 * Return a string representation of this status.
	 *
	 * @return string representation of this status
	 */
	public String toString() {
		return this.getState().name();
	}

	/**
	 * Return a map of attributes for the launched task. The specific keys and
	 * values returned are dependent on the runtime where the task has been
	 * launched. This may include extra information such as execution location
	 * or specific error messages in the case of failure.
	 *
	 * @return map of attributes for the task
	 */
	public Map<String, String> getAttributes() {
		return this.attributes;
	}
}
