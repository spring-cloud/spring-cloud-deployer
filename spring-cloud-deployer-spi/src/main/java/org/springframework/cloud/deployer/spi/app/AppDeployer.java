/*
 * Copyright 2016-2019 the original author or authors.
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

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

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
 * @author Thomas Risberg
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
public interface AppDeployer {

	/**
	 * Common prefix used for deployment properties.
	 */
	static final String PREFIX = "spring.cloud.deployer.";

	/**
	 * The deployment property for the count (number of app instances).
	 * If not provided, a deployer should assume 1 instance.
	 */
	static final String COUNT_PROPERTY_KEY = PREFIX + "count";

	/**
	 * The deployment property for the group to which an app belongs.
	 * If not provided, a deployer should assume no group.
	 */
	static final String GROUP_PROPERTY_KEY = PREFIX + "group";

	/**
	 * The deployment property that indicates if each app instance should have an index value
	 * within a sequence from 0 to N-1, where N is the value of the {@value #COUNT_PROPERTY_KEY}
	 * property. If not provided, a deployer should assume app instance indexing is not necessary.
	 */
	static final String INDEXED_PROPERTY_KEY = PREFIX + "indexed";

	/**
	 * The property to be set at each instance level to specify the sequence number
	 * amongst 0 to N-1, where N is the value of the {@value #COUNT_PROPERTY_KEY} property.
	 * Specified as CAPITAL_WITH_UNDERSCORES as this is typically passed as an environment
	 * variable, but when targeting a Spring app, other variations may apply.
	 *
	 * @see #INDEXED_PROPERTY_KEY
	 */
	static final String INSTANCE_INDEX_PROPERTY_KEY = "INSTANCE_INDEX";

	/**
	 * The deployment property for the memory setting for the container that will run the app.
	 * The memory is specified in <a href="https://en.wikipedia.org/wiki/Mebibyte">Mebibytes</a>,
	 * by default, with optional case-insensitive trailing unit 'm' and 'g' being supported,
	 * for mebi- and giga- respectively.
	 * <p>
	 * 1 MiB = 2^20 bytes = 1024*1024 bytes vs. the decimal based 1MB = 10^6 bytes = 1000*1000 bytes,
	 * <p>
	 * Implementations are expected to translate this value to the target platform as faithfully as possible.
	 *
	 * @see org.springframework.cloud.deployer.spi.util.ByteSizeUtils
	 */
	static final String MEMORY_PROPERTY_KEY = PREFIX + "memory";

	/**
	 * The deployment property for the disk setting for the container that will run the app.
	 * The memory is specified in <a href="https://en.wikipedia.org/wiki/Mebibyte">Mebibytes</a>,
	 * by default, with optional case-insensitive trailing unit 'm' and 'g' being supported,
	 * for mebi- and giga- respectively.
	 * <p>
	 * 1 MiB = 2^20 bytes = 1024*1024 bytes vs. the decimal based 1MB = 10^6 bytes = 1000*1000 bytes,
	 * <p>
	 * Implementations are expected to translate this value to the target platform as faithfully as possible.
	 *
	 * @see org.springframework.cloud.deployer.spi.util.ByteSizeUtils
	 */
	static final String DISK_PROPERTY_KEY = PREFIX + "disk";

	/**
	 * The deployment property for the cpu setting for the container that will run the app.
	 * The cpu is specified as whole multiples or decimal fractions of virtual cores. Some platforms will not
	 * support setting cpu and will ignore this setting. Other platforms may require whole numbers and might
	 * round up. Exactly how this property affects the deployments will vary between implementations.
	 */
	static final String CPU_PROPERTY_KEY = PREFIX + "cpu";

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

	/**
	 * Return the environment info for this deployer.
	 *
	 * @return the runtime environment info
	 */
	RuntimeEnvironmentInfo environmentInfo();

	/**
	 * Return the log of the application identified by the deployment id.
	 *
	 * @return the application log
	 */
	default String getLog(String id) {
		throw new UnsupportedOperationException("'getLog' is not implemented.");
	}

	/**
	 * Scale an app according to given values.
	 *
	 * @param appScaleRequest an {@link AppScaleRequest}.
	 */
	 default void scale(AppScaleRequest appScaleRequest) {
		throw new UnsupportedOperationException("'scale' is not implemented.");
	}
}
