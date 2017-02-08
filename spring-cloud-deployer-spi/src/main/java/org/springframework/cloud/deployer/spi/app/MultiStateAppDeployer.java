/*
 * Copyright 2017 the original author or authors.
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

import java.util.Map;

/**
 * Extension of the AppDeployer interface that adds an additional
 * method to return the DeploymentState for a collection of deployment ids.
 *
 * This method has been introduced for a tactical reason to provide an optimization
 * of individual operation execution in the AppDeployer interface.  It is planned
 * that the next major version of Spring Cloud Deployer will provide a new
 * interface for bulk operations for the full set of AppDeployer methods. As such,
 * this method is annotated as Depreacted.
 */
@Deprecated
public interface MultiStateAppDeployer extends AppDeployer {

    /**
     * Return the {@link DeploymentState} for all the apps represented by
     * a collection of deployment ids.
     *
     * @param ids the collection of app deployment ids, as returned by {@link #deploy}
     * @return a Map of deployment id and DeploymentState
     */
    Map<String, DeploymentState> states(String ... ids);
}
