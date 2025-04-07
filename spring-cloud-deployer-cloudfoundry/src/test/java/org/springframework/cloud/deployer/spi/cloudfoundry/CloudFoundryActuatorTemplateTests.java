/*
 * Copyright 2022-2025 the original author or authors.
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
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.cloud.deployer.spi.app.ActuatorOperations;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CloudFoundryActuatorTemplateTests extends AbstractAppDeployerTestSupport {

	@Mock
	private RestTemplate restTemplate;

	private ActuatorOperations actuatorOperations;

	@Override
	protected void postSetUp() {
		this.actuatorOperations = new CloudFoundryActuatorTemplate(restTemplate, this.deployer, new AppAdmin());
		int port = findRandomOpenPort();
		givenRequestGetApplication("test-application-id", Mono.just(
			ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.urls("localhost:" + port) // No longer dynamic
				.instanceDetail(InstanceDetail.builder().state("RUNNING").index("1").build())
				.build()
		));

		MultiValueMap<String, String> header  = new LinkedMultiValueMap<>();
		header.add("X-Cf-App-Instance", "test-application-id:0");
		header.add("Accept", "application/json");
		header.add("Content-Type", "application/json");
		when(restTemplate.exchange(getUrl(port) + "/actuator/info", HttpMethod.GET,new HttpEntity<>(header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("app", Collections.singletonMap("name", "log-sink-rabbit")), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings",HttpMethod.GET,new HttpEntity<>(header), List.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonList(Collections.singletonMap("bindingName", "input")), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings/input",HttpMethod.GET,new HttpEntity<>(header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("bindingName", "input"), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings/input", HttpMethod.POST, new HttpEntity<>(Collections.singletonMap("state", "STOPPED"), header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("state", "STOPPED"), HttpStatus.OK));
	}

	@Test
	void actuatorInfo() {
		Map<String, Object> info = actuatorOperations
			.getFromActuator("test-application-id", "test-application:0", "/info", Map.class);
		assertThat(((Map<?, ?>) info.get("app")).get("name")).isEqualTo("log-sink-rabbit");
	}

	@Test
	void actuatorBindings() {
		List<?> bindings = actuatorOperations
				.getFromActuator("test-application-id", "test-application:0", "/bindings", List.class);

		assertThat(((Map<?,?>) (bindings.get(0))).get("bindingName")).isEqualTo("input");
	}

	@Test
	void actuatorBindingInput() {
		Map<String, Object> binding = actuatorOperations
				.getFromActuator("test-application-id",  "test-application:0", "/bindings/input", Map.class);
		assertThat(binding.get("bindingName")).isEqualTo("input");
	}

	@Test
	void actuatorPostBindingInput() {
		Map<String, Object> state = actuatorOperations
				.postToActuator("test-application-id",  "test-application:0", "/bindings/input",
						Collections.singletonMap("state", "STOPPED"), Map.class);
		assertThat(state.get("state")).isEqualTo("STOPPED");
	}

	public static String getUrl(int port) {
		return "http://localhost:" + port;
	}
	public static int findRandomOpenPort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new RuntimeException("Failed to find a random open port", e);
		}
	}
}
