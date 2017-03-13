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

import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;
import org.springframework.core.SpringVersion;

/**
 * Tests for constructing a {@link RuntimeEnvironmentInfo}
 */
public class RuntimeEnvironmentInfoBuilderTests {

	@Test
	public void testCreatingRuntimeEnvironmentInfo() {
		RuntimeEnvironmentInfo rei = new RuntimeEnvironmentInfo.Builder()
				.spiClass(AppDeployer.class)
				.implementationName("TestDeployer")
				.implementationVersion("1.0.0")
				.platformClientVersion("1.2.0")
				.platformHostVersion("1.1.0")
				.platformType("Test")
				.platformApiVersion("1")
				.addPlatformSpecificInfo("foo", "bar")
				.build();
		assertThat(rei.getSpiVersion(), is(RuntimeVersionUtils.getVersion(AppDeployer.class)));
		assertThat(rei.getImplementationName(), is("TestDeployer"));
		assertThat(rei.getImplementationVersion(), is("1.0.0"));
		assertThat(rei.getPlatformType(), is("Test"));
		assertThat(rei.getPlatformApiVersion(), is("1"));
		assertThat(rei.getPlatformClientVersion(), is("1.2.0"));
		assertThat(rei.getPlatformHostVersion(), is("1.1.0"));
		assertThat(rei.getJavaVersion(), is(System.getProperty("java.version")));
		assertThat(rei.getSpringVersion(), is(SpringVersion.getVersion()));
		assertThat(rei.getSpringBootVersion(), is(RuntimeVersionUtils.getSpringBootVersion()));
		assertThat(rei.getPlatformSpecificInfo().get("foo"), is("bar"));
	}
}
