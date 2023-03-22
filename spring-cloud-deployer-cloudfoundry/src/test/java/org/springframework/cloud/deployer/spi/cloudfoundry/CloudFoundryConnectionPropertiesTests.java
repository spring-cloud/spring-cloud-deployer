/*
 * Copyright 2019-2021 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class CloudFoundryConnectionPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void setAllProperties() {
		this.contextRunner
		.withInitializer(context -> {
			Map<String, Object> map = new HashMap<>();
			map.put("spring.cloud.deployer.cloudfoundry.org", "org");
			map.put("spring.cloud.deployer.cloudfoundry.space", "space");
			map.put("spring.cloud.deployer.cloudfoundry.url", "http://example.com");
			map.put("spring.cloud.deployer.cloudfoundry.username", "username");
			map.put("spring.cloud.deployer.cloudfoundry.password", "password");
			map.put("spring.cloud.deployer.cloudfoundry.client-id", "id");
			map.put("spring.cloud.deployer.cloudfoundry.client-secret", "secret");
			map.put("spring.cloud.deployer.cloudfoundry.login-hint", "hint");
			map.put("spring.cloud.deployer.cloudfoundry.skip-ssl-validation", "true");
			context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
				StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, map));
			})
			.withUserConfiguration(Config1.class)
			.run((context) -> {
				CloudFoundryConnectionProperties properties = context.getBean(CloudFoundryConnectionProperties.class);
				assertThat(properties.getOrg()).isEqualTo("org");
				assertThat(properties.getSpace()).isEqualTo("space");
				assertThat(properties.getUrl().toString()).isEqualTo("http://example.com");
				assertThat(properties.getUsername()).isEqualTo("username");
				assertThat(properties.getPassword()).isEqualTo("password");
				assertThat(properties.getClientId()).isEqualTo("id");
				assertThat(properties.getClientSecret()).isEqualTo("secret");
				assertThat(properties.getLoginHint()).isEqualTo("hint");
				assertThat(properties.isSkipSslValidation()).isTrue();
			});
	}

	@EnableConfigurationProperties
	private static class Config1 {

		@Bean
		@ConfigurationProperties(prefix = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES)
		public TestCloudFoundryConnectionProperties testCloudFoundryConnectionProperties() {
			return new TestCloudFoundryConnectionProperties();
		}
	}

	private static class TestCloudFoundryConnectionProperties extends CloudFoundryConnectionProperties {
		// not to get Configuration Processor @Bean Duplicate Prefix Definition error
	}
}
