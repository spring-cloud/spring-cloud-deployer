/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.app;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * SPI defining a runtime environment capable of deploying and managing the
 * lifecycle of apps that are intended to run indefinitely (until undeployed),
 * as opposed to {@link org.springframework.cloud.deployer.spi.task.TaskLauncher
 * tasks}.
 *
 * @author Mark Fisher
 * @author Patrick Peralta
 * @author Marius Bogoevici
 * @author Janne Valkealahti
 * @author Andy Clement
 */
public interface AppDeployer {

	/**
	 * The environment property for the count (number of app instances).
	 */
	public static String COUNT_PROPERTY_KEY = "spring.cloud.deployer.count";

	/**
	 * The environment property for the debug port. Setting this implies
	 * debugging should be enabled.
	 */
	public static String DEBUG_PORT_PROPERTY_KEY = "spring.cloud.deployer.debug-port";
	
	/**
	 * The environment property for the group to which an app belongs.
	 */
	public static String GROUP_PROPERTY_KEY = "spring.cloud.deployer.group";

	/**
	 * Deploy an app using an {@link AppDeploymentRequest}. The returned id is
	 * later used with {@link #undeploy(String)} or {@link #status(String)} to
	 * undeploy an app or check its status, respectively.
	 *
	 * Implementations may perform this operation asynchronously; therefore a
	 * successful deployment may not be assumed upon return. To determine the
	 * status of a deployment, invoke {@link #status(String)}.
	 *
	 * @param request the app deployment request
	 * @return the deployment id for the app
	 * @throws IllegalStateException if the app has already been deployed
	 */
	String deploy(AppDeploymentRequest request);

	/**
	 * Un-deploy an app using its deployment id. Implementations may perform
	 * this operation asynchronously; therefore a successful un-deployment may
	 * not be assumed upon return. To determine the status of a deployment,
	 * invoke {@link #status(String)}.
	 *
	 * @param id the app deployment id, as returned by {@link #deploy}
	 * @throws IllegalStateException if the app has not been deployed
	 */
	void undeploy(String id);

	/**
	 * Return the {@link AppStatus} for an app represented by a deployment id.
	 *
	 * @param id the app deployment id, as returned by {@link #deploy}
	 * @return the app deployment status
	 */
	AppStatus status(String id);
}
