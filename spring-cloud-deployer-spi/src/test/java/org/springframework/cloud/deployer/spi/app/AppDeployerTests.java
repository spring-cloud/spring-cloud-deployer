/*
 * Copyright 2019 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link AppDeployer}
 */
public class AppDeployerTests {

	@Test
	public void testAppDeployerGetLogDefaultMethod() {
		AppDeployer customAppDeployer = new AppDeployer() {
			@Override
			public String deploy(AppDeploymentRequest request) {
				return "Deployment request received.";
			}

			@Override
			public void undeploy(String id) {
			}

			@Override
			public AppStatus status(String id) {
				return AppStatus.of("id").build();
			}

			@Override
			public RuntimeEnvironmentInfo environmentInfo() {
				return new RuntimeEnvironmentInfo.Builder().build();
			}

			@Override
			public void scale(AppScaleRequest appScaleRequest) {

			}
		};
		try {
			customAppDeployer.getLog("test");
			fail();
		}
		catch (UnsupportedOperationException e) {
			assertEquals(e.getMessage(), "'getLog' is not implemented.");
		}

	}
}
