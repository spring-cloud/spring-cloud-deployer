/*
 * Copyright 2016-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.local;

import java.net.URL;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * Strategy interface for Execution Command builder.
 *
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Michael Minella
 * @author Christian Tzolov
 */
public interface CommandBuilder {

	/**
	 * Builds the execution command for an application.
	 *
	 * @param request the request for the application to execute.
	 * @param appInstanceNumber application instance id.
	 * @param appInstanceEnv the env vars tha might be needed when building the execution command.
	 * @param debugAddress application remote debug address.
	 * @return the build command as a string array.
	 */
	ProcessBuilder buildExecutionCommand(AppDeploymentRequest request,
			Map<String, String> appInstanceEnv, String deployerId,
			Optional<Integer> appInstanceNumber,
			LocalDeployerProperties localDeployerProperties,
			Optional<DebugAddress> debugAddress);

	/**
	 * Compute an App unique URL over apps deployerId, instance index and computed port.
	 * @param deploymentId App deployment id.
	 * @param index App instance index.
	 * @param port App port.
	 * @return Returns app's URL.
	 */
	URL getBaseUrl(String deploymentId, int index, int port);

	/**
	 * Allow the concrete implementation to suggests the target application port.
	 * @param localDeployerProperties
	 * @return Returns a port suggestion.
	 */
	int getPortSuggestion(LocalDeployerProperties localDeployerProperties);

	/**
	 * Computes the JDWP options with the provided suspend and address arguments.
	 * @param suspend suspend debug argument.
	 * @param address debug address.
	 * @return Returns the JDWP options with the provided suspend and address arguments.
	 */
	default String getJdwpOptions(String suspend, String address) {
		return String.format("-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s", suspend, address);
	}
}
