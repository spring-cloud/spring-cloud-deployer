/*
 * Copyright 2016-2022 the original author or authors.
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

import java.util.SortedSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link DeployerSocketUtils}.
 *
 * @author Sam Brannen
 * @author Gary Russell
 * @author Glenn Renfro
 */
class DeployerSocketUtilsTests {

	@Test
	void findAvailableTcpPortWithZeroMinPort() {
		assertThatIllegalArgumentException().isThrownBy(() -> DeployerSocketUtils.findAvailableTcpPort(0));
	}

	@Test
	void findAvailableTcpPortWithNegativeMinPort() {
		assertThatIllegalArgumentException().isThrownBy(() -> DeployerSocketUtils.findAvailableTcpPort(-500));
	}

	@Test
	void findAvailableTcpPortWithMin() {
		int port = DeployerSocketUtils.findAvailableTcpPort(50000);
		assertPortInRange(port, 50000, DeployerSocketUtils.PORT_RANGE_MAX);
	}

	@Test
	void find4AvailableTcpPortsInRange() {
		findAvailableTcpPorts(4, 30000, 35000);
	}

	@Test
	void find50AvailableTcpPortsInRange() {
		findAvailableTcpPorts(50, 40000, 45000);
	}

	@Test
	void findAvailableTcpPortsWithRequestedNumberGreaterThanSizeOfRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> findAvailableTcpPorts(50, 45000, 45010));
	}

	// Helpers

	private void findAvailableTcpPorts(int numRequested, int minPort, int maxPort) {
		SortedSet<Integer> ports = DeployerSocketUtils.findAvailableTcpPorts(numRequested, minPort, maxPort);
		assertAvailablePorts(ports, numRequested, minPort, maxPort);
	}

	private void assertPortInRange(int port, int minPort, int maxPort) {
		assertThat(port >= minPort).as("port [" + port + "] >= " + minPort).isTrue();
		assertThat(port <= maxPort).as("port [" + port + "] <= " + maxPort).isTrue();
	}

	private void assertAvailablePorts(SortedSet<Integer> ports, int numRequested, int minPort, int maxPort) {
		assertThat(ports.size()).as("number of ports requested").isEqualTo(numRequested);
		for (int port : ports) {
			assertPortInRange(port, minPort, maxPort);
		}
	}

}
