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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.app.ActuatorOperations;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class CloudFoundryActuatorTemplateTests extends AbstractAppDeployerTestSupport {

	private ActuatorOperations actuatorOperations;
	private static MockWebServer mockActuator;
	String appBaseUrl;

	@BeforeAll
	static void setupMockServer() throws IOException {
		mockActuator = new MockWebServer();
		mockActuator.start();
		mockActuator.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
				assertThat(recordedRequest.getHeader("X-Cf-App-Instance")).isEqualTo("test-application-id:0");
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

	@Override
	protected void postSetUp() {
		this.actuatorOperations = new CloudFoundryActuatorTemplate(new RestTemplate(), this.deployer, new AppAdmin());
		this.appBaseUrl = String.format("localhost:%s", mockActuator.getPort());
		givenRequestGetApplication("test-application-id", Mono.just(ApplicationDetail.builder()
				.diskQuota(0)
				.id("test-application-id")
				.instances(1)
				.memoryLimit(0)
				.name("test-application")
				.requestedState("RUNNING")
				.runningInstances(1)
				.stack("test-stack")
				.urls(appBaseUrl)
				.instanceDetail(InstanceDetail.builder().state("RUNNING").index("1").build())
				.build()));
	}

	@Test
	void actuatorInfo() {
		Map<String,Object> info = actuatorOperations
				.getFromActuator("test-application-id", "test-application:0", "/info", Map.class);

		assertThat(((Map<?,?>) (info.get("app"))).get("name")).isEqualTo("log-sink-rabbit");
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

	private static String resourceAsString(String path) {
		try {
			return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}
}
