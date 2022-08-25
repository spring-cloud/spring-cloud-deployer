/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.cloud.deployer.spi.app.observation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.simple.SpansAssert;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.core.io.PathResource;

/**
 * Tests for {@link AppDeployer}
 */
public class ObservedAppDeployerTests extends SampleTestRunner {

	ObservedAppDeployerTests() {
		super(SampleRunnerConfig.builder().build());
	}

	@Override
	public SampleTestRunnerConsumer yourCode() {
		return (bb, meterRegistry) -> {
			AppDeployer appDeployer = new ObservedAppDeployer(getObservationRegistry(), appDeployer(), 100L);

			String id = appDeployer.deploy(deploymentRequest());
			appDeployer.status(id);
			appDeployer.scale(new AppScaleRequest(id, 2));
			appDeployer.undeploy(id);

			SpansAssert.assertThat(bb.getFinishedSpans())
					.haveSameTraceId()
					.hasASpanWithName("deploy", spanAssert -> spanAssert.hasRemoteServiceNameEqualTo("Test")
							.hasTag("spring.cloud.deployer.app.id", "Deployment request received")
							.hasTag("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz")
							.hasKindEqualTo(Span.Kind.PRODUCER))
					.hasASpanWithName("status", spanAssert -> spanAssert.hasRemoteServiceNameEqualTo("Test")
							.hasTag("spring.cloud.deployer.app.id", "Deployment request received")
							.hasTag("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz")
							.hasKindEqualTo(Span.Kind.PRODUCER))
					.hasASpanWithName("scale", spanAssert -> spanAssert.hasRemoteServiceNameEqualTo("Test")
							.hasTag("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz")
							.hasTag("spring.cloud.deployer.scale.count", "2")
							.hasKindEqualTo(Span.Kind.PRODUCER))
					.hasASpanWithName("undeploy", spanAssert -> spanAssert.hasRemoteServiceNameEqualTo("Test")
							.hasTag("spring.cloud.deployer.app.id", "Deployment request received")
							.hasTag("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz")
							.hasKindEqualTo(Span.Kind.PRODUCER))
					.hasSize(4);

			MeterRegistryAssert.assertThat(getMeterRegistry())
					.hasTimerWithNameAndTags("spring.cloud.deployer.deploy", Tags.of("error", "none", "spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.undeploy.active", Tags.of("spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.deploy.status-change", Tags.of("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.scale.active", Tags.of("spring.cloud.deployer.scale.deploymentId", "Deployment request received", "spring.cloud.deployer.scale.count", "2"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.deploy.start", Tags.of("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.undeploy.start", Tags.of("spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasTimerWithNameAndTags("spring.cloud.deployer.status", Tags.of("error", "none", "spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasTimerWithNameAndTags("spring.cloud.deployer.undeploy", Tags.of("error", "none", "spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasTimerWithNameAndTags("spring.cloud.deployer.scale", Tags.of("error", "none", "spring.cloud.deployer.scale.count", "2", "spring.cloud.deployer.scale.deploymentId", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.undeploy.status-change", Tags.of("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.status.active", Tags.of("spring.cloud.deployer.app.id", "Deployment request received", "spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"))
					.hasMeterWithNameAndTags("spring.cloud.deployer.deploy.active", Tags.of("spring.cloud.deployer.platform.k8s.url", "https://foo.bar/baz"));
		};
	}

	private AppDeploymentRequest deploymentRequest() {
		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.platformName", "tap");
		props.put("spring.cloud.deployer.appName", "app");
		props.put("spring.cloud.deployer.group", "group");
		AppDefinition definition = new AppDefinition(UUID.randomUUID().toString(), props);
		return new AppDeploymentRequest(definition, new PathResource("foo"));
	}

	private AppDeployer appDeployer() {
		return new AppDeployer() {
			@Override
			public String deploy(AppDeploymentRequest request) {
				return "Deployment request received";
			}

			@Override
			public void undeploy(String id) {
			}

			@Override
			public AppStatus status(String id) {
				return AppStatus.of("id").build();
			}

			@Override
			public RuntimeEnvironmentInfo environmentInfo() {
				return new RuntimeEnvironmentInfo.Builder()
						.spiClass(AppDeployer.class)
						.implementationName("TestDeployer")
						.implementationVersion("1.0.0")
						.platformClientVersion("1.2.0")
						.platformHostVersion("1.1.0")
						.platformType("Test")
						.platformApiVersion("1")
						.addPlatformSpecificInfo("foo", "bar")
						.addPlatformSpecificInfo("master-url", "https://foo.bar/baz")
						.build();
			}

			@Override
			public Mono<AppStatus> statusReactive(String id) {
				return Mono.just(AppStatus.of(id).build());
			}

			@Override
			public void scale(AppScaleRequest appScaleRequest) {

			}
		};
	}
}
