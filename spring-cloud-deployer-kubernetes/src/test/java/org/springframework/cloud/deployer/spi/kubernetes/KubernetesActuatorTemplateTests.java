/*
 * Copyright 2022 the original author or authors.
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

package org.springframework.cloud.deployer.spi.kubernetes;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.deployer.spi.app.ActuatorOperations;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KubernetesActuatorTemplateTests {

	private final RestTemplate restTemplate = mock(RestTemplate.class);
	private final AppDeployer appDeployer = mock(AppDeployer.class);
	private final ActuatorOperations actuatorOperations = new KubernetesActuatorTemplate(
		restTemplate, appDeployer, new AppAdmin());

	private AppInstanceStatus appInstanceStatus;

	@BeforeEach
	void setUp() {
		appInstanceStatus = mock(AppInstanceStatus.class);
		int port = findRandomOpenPort();

		Map<String, String> attributes = new HashMap<>();
		attributes.put("pod.ip", "127.0.0.1");
		attributes.put("actuator.port", String.valueOf(port));
		attributes.put("actuator.path", "/actuator");
		attributes.put("guid", "test-application-0");

		when(appInstanceStatus.getAttributes()).thenReturn(attributes);
		when(appInstanceStatus.getState()).thenReturn(DeploymentState.deployed);

		AppStatus appStatus = AppStatus.of("test-application-id")
			.with(appInstanceStatus)
			.build();

		when(appDeployer.status(anyString())).thenReturn(appStatus);
		// Mock the actual call to RestTemplate
		Map<String, Object> appInfo = new HashMap<>();
		Map<String, Object> appDetails = new HashMap<>();
		appDetails.put("name", "log-sink-rabbit");
		appInfo.put("app", appDetails);
//		restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(requestHeaders), responseType)
		MultiValueMap<String, String> header  = new LinkedMultiValueMap<>();
		header.add("Content-Type", "application/json");
		header.add("Accept", "application/json");
		when(restTemplate.exchange(getUrl(port) + "/actuator/info",HttpMethod.GET,new HttpEntity<>(header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("app", Collections.singletonMap("name", "log-sink-rabbit")), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/health",HttpMethod.GET,new HttpEntity<>(header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("app", Collections.singletonMap("status", "UP")), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings",HttpMethod.GET,new HttpEntity<>(header), List.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonList(Collections.singletonMap("bindingName", "input")), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings/input",HttpMethod.GET,new HttpEntity<>(header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("bindingName", "input"), HttpStatus.OK));
		when(restTemplate.exchange(getUrl(port) + "/actuator/bindings/input", HttpMethod.POST, new HttpEntity<>(Collections.singletonMap("state", "STOPPED"), header), Map.class))
			.thenReturn(new ResponseEntity<>(Collections.singletonMap("state", "STOPPED"), HttpStatus.OK));
		;

	}

	@Test
	void actuatorInfo() {
		Map<String, Object> info = actuatorOperations
			.getFromActuator("test-application-id", "test-application-0", "/info", Map.class);

		assertThat(((Map<?, ?>) (info.get("app"))).get("name")).isEqualTo("log-sink-rabbit");
	}

	@Test
	void actuatorBindings() {
		List<?> bindings = actuatorOperations
				.getFromActuator("test-application-id", "test-application-0", "/bindings", List.class);

		assertThat(((Map<?, ?>) (bindings.get(0))).get("bindingName")).isEqualTo("input");
	}

	@Test
	void actuatorBindingInput() {
		Map<String, Object> binding = actuatorOperations
				.getFromActuator("test-application-id", "test-application-0", "/bindings/input", Map.class);
		assertThat(binding.get("bindingName")).isEqualTo("input");
	}

	@Test
	void actuatorPostBindingInput() {
		Map<String, Object> state = actuatorOperations
				.postToActuator("test-application-id", "test-application-0", "/bindings/input",
						Collections.singletonMap("state", "STOPPED"), Map.class);
		assertThat(state.get("state")).isEqualTo("STOPPED");
	}

	@Test
	void noInstanceDeployed() {
		when(appInstanceStatus.getState()).thenReturn(DeploymentState.failed);
		assertThatThrownBy(() -> {
			actuatorOperations
					.getFromActuator("test-application-id", "test-application-0", "/info", Map.class);

		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("not deployed");
	}

	public static String getUrl(int port) {
		return "http://127.0.0.1:" + port;
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
