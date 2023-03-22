/*
 * Copyright 2018 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class RandomPortRangeTests {

	private AbstractLocalDeployerSupport localDeployerSupport;

	@Mock
	AppDeploymentRequest appDeploymentRequest;

	@BeforeEach
	public void setUp() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.getPortRange().setLow(30001);
		properties.getPortRange().setHigh(30213);

		properties.getDocker().getPortRange().setLow(40001);
		properties.getDocker().getPortRange().setHigh(40213);

		localDeployerSupport = new AbstractLocalDeployerSupport(properties) {};

	}

	@Test
	public void portTests() {
		when(appDeploymentRequest.getResource()).thenReturn(new ClassPathResource(""));
		for (int i = 0; i < 30; i++) {
			int port = localDeployerSupport.getRandomPort(appDeploymentRequest);
			assertThat(port).isGreaterThanOrEqualTo(30001);
			assertThat(port).isLessThanOrEqualTo(30213 + 5);
		}
	}

	@Test
	public void portTests2() {
		when(appDeploymentRequest.getResource()).thenReturn(new DockerResource("/test:test"));
		for (int i = 0; i < 5; i++) {
			int port = localDeployerSupport.getRandomPort(appDeploymentRequest);
			assertThat(port).isGreaterThanOrEqualTo(40001);
			assertThat(port).isLessThanOrEqualTo(40213 + 5);
		}
	}
}
