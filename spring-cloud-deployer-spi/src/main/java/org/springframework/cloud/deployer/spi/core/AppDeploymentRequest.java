/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.deployer.spi.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * Representation of an app deployment request. This includes the
 * {@link AppDefinition}, the {@link Resource} representing its deployable
 * artifact, and any deployment properties.
 *
 * Deployment properties are related to a specific implementation of the SPI
 * and will never be passed into an app itself. For example, a runtime container
 * may allow the definition of various settings for a context where the actual
 * app is executed, such as allowed memory, cpu or simply a way to define
 * colocation like node labeling.
 *
 * For passing properties into the app itself, use {@link AppDefinition#getProperties()}.
 * Those could be passed as env vars, or whatever approach is best for the target
 * platform. Each deployer implementation should clearly document how it handles
 * these properties.
 *
 * For passing command line arguments into the app itself, use {@link #commandlineArguments}.
 *
 * @author Mark Fisher
 * @author Janne Valkealahti
 * @author Oleg Zhurakousky
 */
public class AppDeploymentRequest {

	/**
	 * App definition.
	 */
	private final AppDefinition definition;

	/**
	 * Resource representing the artifact of the underlying app.
	 */
	private final Resource resource;

	/**
	 * Map of deployment properties to be used by the deployer.
	 */
	private final Map<String, String> deploymentProperties;

	/**
	 * List of command line arguments for the target runtime of the app.
	 */
	private final List<String> commandlineArguments;

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition
	 * @param resource resource for the underlying app's artifact
	 * @param deploymentProperties map of deployment properties; may be {@code null}
	 */
	public AppDeploymentRequest(AppDefinition definition, Resource resource,
			Map<String, String> deploymentProperties) {
		this(definition, resource, deploymentProperties, null);
	}

	/**
	 * Construct an {@code AppDeploymentRequest}.
	 *
	 * @param definition app definition
	 * @param resource resource for the underlying app's artifact
	 * @param deploymentProperties map of deployment properties; may be {@code null}
	 * @param commandlineArguments set of command line arguments; may be {@code null}
	 */
	public AppDeploymentRequest(AppDefinition definition, Resource resource,
			Map<String, String> deploymentProperties, List<String> commandlineArguments) {
		Assert.notNull(definition, "definition must not be null");
		Assert.notNull(resource, "resource must not be null");
		this.definition = definition;
		this.resource = resource;
		this.deploymentProperties = deploymentProperties == null
				? Collections.<String, String>emptyMap()
				: Collections.unmodifiableMap(deploymentProperties);
		this.commandlineArguments = commandlineArguments == null
				? Collections.<String>emptyList()
				: Collections.unmodifiableList(commandlineArguments);
	}

	/**
	 * Construct an {@code AppDeploymentRequest} with no deployment properties.
	 *
	 * @param definition app definition
	 * @param resource resource for the underlying app's artifact
	 */
	public AppDeploymentRequest(AppDefinition definition, Resource resource) {
		this(definition, resource, null);
	}

	/**
	 * @see #definition
	 */
	public AppDefinition getDefinition() {
		return definition;
	}

	/**
	 * @see #resource
	 */
	public Resource getResource() {
		return resource;
	}

	/**
	 * @see #deploymentProperties
	 */
	public Map<String, String> getDeploymentProperties() {
		return deploymentProperties;
	}

	/**
	 * @see #commandlineArguments
	 */
	public List<String> getCommandlineArguments() {
		return commandlineArguments;
	}

	@Override
	public String toString(){
		return new ToStringCreator(this)
				.append("commandlineArguments", this.commandlineArguments)
				.append("deploymentProperties", this.deploymentProperties)
				.append("definition", this.definition)
				.append("resource", this.resource)
				.toString();
	}
}
