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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import org.springframework.cloud.deployer.spi.util.DeployerVersionUtils;
import org.springframework.core.SpringVersion;

/**
 * Tests for constructing a {@link DeployerEnvironmentInfo}
 */
public class DeployerEnvironmentInfoBuilderTests {

	@Test
	public void testCreatingDeployerEnvironmentInfo() {
		DeployerEnvironmentInfo dei = new DeployerEnvironmentInfo.Builder()
				.deployerImplementationVersion("1.0.0")
				.platformClientVersion("1.2.0")
				.platformHostVersion("1.1.0")
				.platformType("Test").build();
		assertThat(dei.getDeployerSpiVersion(), is(DeployerVersionUtils.getVersion(AppDeployer.class)));
		assertThat(dei.getDeployerImplementationVersion(), is("1.0.0"));
		assertThat(dei.getPlatformType(), is("Test"));
		assertThat(dei.getPlatformClientVersion(), is("1.2.0"));
		assertThat(dei.getPlatformHostVersion(), is("1.1.0"));
		assertThat(dei.getJavaVersion(), is(System.getProperty("java.version")));
		assertThat(dei.getSpringVersion(), is(SpringVersion.getVersion()));
		assertThat(dei.getSpringBootVersion(), is(DeployerVersionUtils.getSpringBootVersion()));
	}
}
