/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.deployer.spi.scheduler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.style.ToStringCreator;

/**
 * Representation of a schedule request. This includes the {@link AppDefinition}
 * and any deployment properties.
 *
 * Deployment properties are related to a specific implementation of the SPI
 * and will never be passed into an app itself. For example, a runtime container
 * may allow the definition of various settings for a context where the actual
 * app is executed, such as allowed memory, cpu or simply a way to define
 * collocation like node labeling.
 *
 * For passing properties into the app itself, use {@link AppDefinition#getProperties()}.
 * Those could be passed as env vars, or whatever approach is best for the target
 * platform. Each deployer implementation should clearly document how it handles
 * these properties.
 *
 * For passing command line arguments into the app itself, use {@link #commandlineArguments}.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
public class ScheduleRequest extends AppDeploymentRequest{

	/**
	 * The name of the Schedule.
	 */
	private final String scheduleName;

	private Map<String, String> schedulerProperties;

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition.
	 * @param schedulerProperties properties that contain scheduler specific information.
	 * @param deploymentProperties map of deployment properties; may be {@code null}.
	 * @param scheduleName the name associated with the schedule.
	 * @param resource the resource associated with the request.
	 */
	@Deprecated
	public ScheduleRequest(AppDefinition definition, Map<String, String> schedulerProperties,
			Map<String, String> deploymentProperties, String scheduleName, Resource resource) {
		this(definition, schedulerProperties, deploymentProperties, null, scheduleName, resource);
	}

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition
	 * @param schedulerProperties properties that contain scheduler specific information.
	 * @param deploymentProperties map of deployment properties; may be {@code null}
	 * @param commandlineArguments set of command line arguments; may be {@code null}
	 * @param scheduleName the name associated with the schedule.
	 * @param resource the resource associated with the request.
	 */
	@Deprecated
	public ScheduleRequest(AppDefinition definition,  Map<String, String> schedulerProperties,
			Map<String, String> deploymentProperties, List<String> commandlineArguments,
			String scheduleName, Resource resource) {
		super(definition, resource, deploymentProperties, commandlineArguments);
		this.scheduleName = scheduleName;
		this.schedulerProperties = schedulerProperties == null
				? Collections.emptyMap()
				: Collections.unmodifiableMap(schedulerProperties);
	}

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition.
	 * @param deploymentProperties map of deployment properties; may be {@code null}.
	 * @param scheduleName the name associated with the schedule.
	 * @param resource the resource associated with the request.
	 */
	public ScheduleRequest(AppDefinition definition,
			Map<String, String> deploymentProperties, String scheduleName, Resource resource) {
		this(definition, deploymentProperties, (List<String>) null, scheduleName, resource);
	}

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition
	 * @param deploymentProperties map of deployment properties; may be {@code null}
	 * @param commandlineArguments set of command line arguments; may be {@code null}
	 * @param scheduleName the name associated with the schedule.
	 * @param resource the resource associated with the request.
	 */
	public ScheduleRequest(AppDefinition definition,
			Map<String, String> deploymentProperties, List<String> commandlineArguments,
			String scheduleName, Resource resource) {
		super(definition, resource, deploymentProperties, commandlineArguments);
		this.scheduleName = scheduleName;
	}

	/**
	 * @see #scheduleName
	 * @return the schedule name
	 */
	public String getScheduleName() {
		return scheduleName;
	}

	@Deprecated
	public Map<String, String> getSchedulerProperties() {
		return schedulerProperties;
	}

	@Deprecated
	public void setSchedulerProperties(Map<String, String> schedulerProperties) {
		this.schedulerProperties = schedulerProperties;
	}

	@Override
	public String toString(){
		return new ToStringCreator(this)
				.append("scheduleName", this.scheduleName)
				.append("schedulerProperties", this.schedulerProperties)
				.toString();
	}
}
