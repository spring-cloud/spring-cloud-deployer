/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.app;

import java.util.Map;
import java.util.Optional;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Representation of an app scale request. This includes the application's deployment Id, the number of desired
 * instances, and optionally, properties that may be applied during the scaling operation.
 *
 * @author David Turanski
 * @since 2.1.0
 */
public class AppScaleRequest {
    private final int desiredInstanceCount;
    private final String deploymentId;
    private final Optional<Map<String,Object>> properties;

    /**
     *
     * @param deploymentId the unique deployment ID of the application.
     * @param desiredInstanceCount the desired instance count.
     */
    public AppScaleRequest(String deploymentId, int desiredInstanceCount) {
        this(deploymentId, desiredInstanceCount, null);
    }

    /**
     *
     * @param deploymentId the unique deployment ID of the application.
     * @param desiredInstanceCount the desired instance count.
     * @param properties optional properties that may be applied during the scale operation.
     */
    public AppScaleRequest(String deploymentId, int desiredInstanceCount, @Nullable  Map<String, Object> properties) {
        Assert.hasText(deploymentId,"'deploymentId', must not be empty or null");
        Assert.state(desiredInstanceCount >= 0, "'desiredInstanceCount' must be >= 0");
        this.deploymentId = deploymentId;
        this.desiredInstanceCount = desiredInstanceCount;
        this.properties = Optional.ofNullable(properties);
    }

    /**
     *
     * @return the desired instance count.
     */
    public int getDesiredInstanceCount() {
        return desiredInstanceCount;
    }

    /**
     *
     * @return the deployment ID.
     */
    public String getDeploymentId() {
        return deploymentId;
    }

    /**
     *
     * @return the {@link Optional} properties.
     */
    public Optional<Map<String, Object>> getProperties() {
        return properties;
    }
}
