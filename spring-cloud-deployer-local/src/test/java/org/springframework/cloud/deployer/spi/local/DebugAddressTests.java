/*
 * Copyright 2020-2021 the original author or authors.
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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class DebugAddressTests {

	@Test
	public void testDebugEmptyConfiguration() {
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(new LocalDeployerProperties(), 0);
		assertThat(debugAddress.isPresent()).isFalse();
	}

	@Test
	public void testDebugPort() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugPort(20075);
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 0);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isNull();
		assertThat(debugAddress.get().getPort()).isEqualTo("20075");
		assertThat(debugAddress.get().getAddress()).isEqualTo("20075");
	}

	@Test
	public void testDebugPortWithInstance() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugPort(20075);
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isNull();
		assertThat(debugAddress.get().getPort()).isEqualTo("20175");
		assertThat(debugAddress.get().getAddress()).isEqualTo("20175");
	}

	@Test
	public void testDebugPortInvalidValue() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugPort(-666);
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 0);
		assertThat(debugAddress.isPresent()).isFalse();
	}

	@Test
	public void testDebugAddressPortOnly() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugAddress("20075");
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isNull();
		assertThat(debugAddress.get().getPort()).isEqualTo("20175");
		assertThat(debugAddress.get().getAddress()).isEqualTo("20175");
	}

	@Test
	public void testDebugAddressWildcardHost() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugAddress("*:20075");
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isEqualTo("*");
		assertThat(debugAddress.get().getPort()).isEqualTo("20175");
		assertThat(debugAddress.get().getAddress()).isEqualTo("*:20175");
	}


	@Test
	public void testDebugAddressWithIP() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugAddress("127.0.0.1:20075");
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isEqualTo("127.0.0.1");
		assertThat(debugAddress.get().getPort()).isEqualTo("20175");
		assertThat(debugAddress.get().getAddress()).isEqualTo("127.0.0.1:20175");
	}

	@Test
	public void testDebugAddressWithHostname() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugAddress("localhost:20075");
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isTrue();
		assertThat(debugAddress.get().getHost()).isEqualTo("localhost");
		assertThat(debugAddress.get().getPort()).isEqualTo("20175");
		assertThat(debugAddress.get().getAddress()).isEqualTo("localhost:20175");
	}

	@Test
	public void testDebugAddressWithInvalidIP() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setDebugAddress("127.0.:20075");
		Optional<DebugAddress> debugAddress =
				DebugAddress.from(properties, 100);
		assertThat(debugAddress.isPresent()).isFalse();
	}

}
