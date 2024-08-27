/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.cloud.deployer.spi.cloudfoundry;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Turanski
 */
public class CfEnvAwareResourceTests {

	@Test
	public void testCfEnvResolverWithCfEnvJava17() throws IOException {
		// this task app is compiled from a spring-cloud-task sample using jdk17
		// and cfenv added to build
		CfEnvAwareResource resource = CfEnvAwareResource.of(new ClassPathResource("timestamp-task-3.1.2-SNAPSHOT.jar"));
		assertThat(resource.hasCfEnv()).isTrue();
	}

	@Test
	public void testCfEnvResolverWithCfEnv() throws IOException {
		CfEnvAwareResource resource = CfEnvAwareResource.of(new ClassPathResource("log-sink-rabbit-3.0.0.BUILD-SNAPSHOT.jar"));
		assertThat(resource.hasCfEnv()).isTrue();
	}

	@Test
	public void testCfEnvResolverWithNoCfEnv() throws IOException {
		CfEnvAwareResource resource = CfEnvAwareResource.of(new ClassPathResource("batch-job-1.0.0.BUILD-SNAPSHOT.jar"));
		assertThat(resource.hasCfEnv()).isFalse();
	}

	@Test
	public void testDoesNothingWithDocker() throws IOException, URISyntaxException {
		Resource docker = mock(Resource.class);
		when(docker.getURI()).thenReturn(new URI("docker://fake/news"));
		assertThat(CfEnvAwareResource.of(docker).hasCfEnv()).isFalse();
	}
}
