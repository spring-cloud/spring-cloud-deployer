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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.app.ActuatorOperations;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KubernetesActuatorTemplateTests {
	private static MockWebServer mockActuator;

	private final AppDeployer appDeployer = mock(AppDeployer.class);

	private final ActuatorOperations actuatorOperations = new KubernetesActuatorTemplate(new RestTemplate(),
			appDeployer, new AppAdmin());

	private AppInstanceStatus appInstanceStatus;

	@BeforeAll
	static void setupMockServer() throws IOException {
		mockActuator = new MockWebServer();
		mockActuator.start();
		mockActuator.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
				switch (recordedRequest.getPath()) {
				case "/actuator/info":
					return new MockResponse().setBody(resourceAsString("actuator-info.json"))
							.addHeader("Content-Type", "application/json").setResponseCode(200);
				case "/actuator/health":
					return new MockResponse().setBody("\"status\":\"UP\"}")
							.addHeader("Content-Type", "application/json").setResponseCode(200);
				case "/actuator/bindings":
					return new MockResponse().setBody(resourceAsString("actuator-bindings.json"))
							.addHeader("Content-Type", "application/json").setResponseCode(200);
				case "/actuator/bindings/input":
					if (recordedRequest.getMethod().equals("GET")) {
						return new MockResponse().setBody(resourceAsString("actuator-binding-input.json"))
								.addHeader("Content-Type", "application/json")
								.setResponseCode(200);
					}
					else if (recordedRequest.getMethod().equals("POST")) {
						if (!StringUtils.hasText(recordedRequest.getBody().toString())) {
							return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
						}
						else {
							return new MockResponse().setBody(recordedRequest.getBody())
									.addHeader("Content-Type", "application/json").setResponseCode(200);
						}
					}
					else {
						return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
					}
				default:
					return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
				}
			}
		});
	}

	@AfterAll
	static void tearDown() throws IOException {
		mockActuator.shutdown();
	}

	@BeforeEach
	void setUp() {
		appInstanceStatus = mock(AppInstanceStatus.class);
		Map<String, String> attributes = new HashMap<>();
		attributes.put("pod.ip", "127.0.0.1");
		attributes.put("actuator.port", String.valueOf(mockActuator.getPort()));
		attributes.put("actuator.path", "/actuator");
		attributes.put("guid", "test-application-0");
		when(appInstanceStatus.getAttributes()).thenReturn(attributes);
		when(appInstanceStatus.getState()).thenReturn(DeploymentState.deployed);
		AppStatus appStatus = AppStatus.of("test-application-id")
				.with(appInstanceStatus)
				.build();
		when(appDeployer.status(anyString())).thenReturn(appStatus);
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

	private static String resourceAsString(String path) {
		try {
			return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}
}
