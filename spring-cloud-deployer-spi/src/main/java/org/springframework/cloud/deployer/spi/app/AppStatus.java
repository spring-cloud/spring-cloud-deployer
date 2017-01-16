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

package org.springframework.cloud.deployer.spi.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * Status of an app which is initially constructed from an
 * {@link org.springframework.cloud.deployer.spi.core.AppDeploymentRequest} and
 * runtime deployment properties by a deployer during deployment. This status is
 * composed of an aggregate of all individual app instance deployments.
 * <p>
 * Consumers of the SPI obtain the app status via
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer#status(String)}
 * , whereas SPI implementations create instances of this class via
 * {@link AppStatus.Builder}.
 *
 * @author Patrick Peralta
 * @author Mark Fisher
 * @see AppInstanceStatus
 */
public class AppStatus {

	/**
	 * The id of the app this status is for.
	 */
	private final String deploymentId;

	/**
	 * Map of {@link AppInstanceStatus} keyed by a unique identifier
	 * for each app deployment instance.
	 */
	private final Map<String, AppInstanceStatus> instances = new HashMap<String, AppInstanceStatus>();

	private final DeploymentState generalState;

	/**
	 * Construct a new {@code AppStatus}.
	 *
	 * @param deploymentId id of the app this status is for
	 * @param generalState a value for general state of the app, or {@literal null} if this should be derived from instances
	 */
	protected AppStatus(String deploymentId, DeploymentState generalState) {
		this.deploymentId = deploymentId;
		this.generalState = generalState;
	}

	/**
	 * Return the app deployment id.
	 *
	 * @return app deployment id
	 */
	public String getDeploymentId() {
		return deploymentId;
	}

	/**
	 * Return the deployment state for the the app. If the descriptor
	 * indicates multiple instances, this state represents an aggregate
	 * of all individual app instances.
	 *
	 * @return deployment state for the app
	 */
	public DeploymentState getState() {
		if (generalState != null) {
			return generalState;
		}
		Set<DeploymentState> states = new HashSet<>();
		for (Map.Entry<String, AppInstanceStatus> entry : instances.entrySet()) {
			states.add(entry.getValue().getState());
		}
		if (states.size() == 0) {
			return DeploymentState.unknown;
		}
		if (states.size() == 1) {
			return states.iterator().next();
		}
		if (states.contains(DeploymentState.error)) {
			return DeploymentState.error;
		}
		if (states.contains(DeploymentState.deploying)) {
			return DeploymentState.deploying;
		}
		if (states.contains(DeploymentState.deployed) || states.contains(DeploymentState.partial)) {
			return DeploymentState.partial;
		}
		if (states.contains(DeploymentState.failed)) {
			return DeploymentState.failed;
		}
		// reaching here is unlikely; it would require some
		// combination of unknown, undeployed, complete
		return DeploymentState.partial;
	}

	public String toString() {
		return this.getState().name();
	}

	/**
	 * Return a map of {@code AppInstanceStatus} keyed by a unique identifier
	 * for each app instance.
	 * @return map of {@code AppInstanceStatus}
	 */
	public Map<String, AppInstanceStatus> getInstances() {
		return Collections.unmodifiableMap(this.instances);
	}

	private void addInstance(String id, AppInstanceStatus status) {
		this.instances.put(id, status);
	}

	/**
	 * Return a {@code Builder} for {@code AppStatus}.
	 * @param id of the app this status is for
	 * @return {@code Builder} for {@code AppStatus}
	 */
	public static Builder of(String id) {
		return new Builder(id);
	}

	/**
	 * Utility class constructing an instance of {@link AppStatus}
	 * using a builder pattern.
	 */
	public static class Builder {

		private final String id;

		private DeploymentState generalState;

		private List<AppInstanceStatus> statuses = new ArrayList<>();

		/**
		 * Instantiates a new builder.
		 *
		 * @param id the app deployment id
		 */
		private Builder(String id) {
			this.id = id;
		}

		/**
		 * Add an instance of {@code AppInstanceStatus} to build the status for
		 * the app. This will be invoked once per individual app instance.
		 *
		 * @param instance status of individual app deployment
		 * @return this {@code Builder}
		 */
		public Builder with(AppInstanceStatus instance) {
			Assert.isNull(generalState, "Can't build an AppStatus from app instances if generalState has been set");
			statuses.add(instance);
			return this;
		}

		/**
		 * Set the state of the app as a direct value. This is to be used when no information about instances could
		 * be determined (<i>e.g.</i> general error condition).
		 * @param generalState the deployment state to set
		 * @return this {@code Builder}
		 */
		public Builder generalState(DeploymentState generalState) {
			Assert.isTrue(statuses.isEmpty(), "Can't buikd an AppStatus from general state if some instances have been added");
			this.generalState = generalState;
			return this;
		}

		/**
		 * Return a new instance of {@code AppStatus} based on
		 * the provided individual app instances via
		 * {@link #with(AppInstanceStatus)}.
		 * @return new instance of {@code AppStatus}
		 */
		public AppStatus build() {
			AppStatus status = new AppStatus(id, generalState);
			for (AppInstanceStatus instanceStatus : statuses) {
				status.addInstance(instanceStatus.getId(), instanceStatus);
			}
			return status;
		}
	}
}
