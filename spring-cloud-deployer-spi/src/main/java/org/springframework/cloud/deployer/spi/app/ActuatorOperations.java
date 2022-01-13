/*
 * Copyright 2022 the original author or authors.
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

package org.springframework.cloud.deployer.spi.app;

/**
 * Deployers may implement this extension to invoke actuator endpoints of deployed app instances.
 *
 * @author David Turanski
 */
public interface ActuatorOperations {

	/**
	 * Get a resource from an actuator path.
	 * @param deploymentId the deployment ID of the deployed app.
	 * @param guid unique id for the app instance.
	 * @param endpoint the endpoint path relative to the base actuator URL for the instance, with or without preceding '/'.
	 * @param responseType the expected response type.
	 * @return the contents as the given type.
	 */
	<T> T getFromActuator(String deploymentId, String guid, String endpoint, Class<T> responseType);

	/**
	 * Get a resource from an actuator path.
	 * @param deploymentId the deployment ID of the deployed app.
	 * @param guid unique id for the app instance.
	 * @param endpoint the endpoint path relative to the base actuator URL for the instance, with or without preceding '/'.
	 * @return the contents as a {@code String}.
	 */
	default String getFromActuator(String deploymentId, String guid, String endpoint){
		return getFromActuator(deploymentId, guid, endpoint, String.class);
	}
	/**
	 * Post to resource on actuator path.
	 * @param deploymentId the deployment ID of the deployed app.
	 * @param guid unique id for the app instance.
	 * @param endpoint the endpoint path relative to the base actuator URL for the instance, with or without preceding '/'.
	 * @param body the request body.
	 * @param responseType the expected response type.
	 * @return the result (response body).
	 */
	<T,R> R postToActuator(String deploymentId, String guid, String endpoint, T body, Class<R> responseType);
}
