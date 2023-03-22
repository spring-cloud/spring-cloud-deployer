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

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;

import static org.mockito.Mockito.when;

/**
 * @author Mark Pollack
 */
@ExtendWith(MockitoExtension.class)
public class RandomPortTests {

	private AbstractLocalDeployerSupport localDeployerSupport;

	@Mock
	AppDeploymentRequest appDeploymentRequest;

	@BeforeEach
	public void setUp() {
		localDeployerSupport = new AbstractLocalDeployerSupport(new LocalDeployerProperties()) {};
		when(appDeploymentRequest.getResource()).thenReturn(new ClassPathResource(""));
	}

	@Test
	public void portTests() {
		//No exception should be thrown
		for (int i = 0; i < 100; i++) {
			localDeployerSupport.getRandomPort(appDeploymentRequest);
		}
	}

}
