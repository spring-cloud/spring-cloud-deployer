/*
 * Copyright 2018-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class RandomPortRangeContextTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(LocalDeployerAutoConfiguration.class));

	@Test
	public void defaultProtRangeProperties() {
		this.contextRunner
				.withUserConfiguration(LocalDeployerAutoConfiguration.class)
				.run((context) -> {
					assertThat(context).hasSingleBean(LocalDeployerProperties.class);
					assertThat(context).hasBean("localDeployerAutoConfiguration");
					assertThat(context).getBean(LocalDeployerProperties.class)
							.hasFieldOrPropertyWithValue("portRange.low", 20000);
					assertThat(context).getBean(LocalDeployerProperties.class)
							.hasFieldOrPropertyWithValue("portRange.high", 61000);
				});
	}

	@Test
	public void presetProtRangeProperties() {
		this.contextRunner
				.withUserConfiguration(LocalDeployerAutoConfiguration.class)
				.withPropertyValues("spring.cloud.deployer.local.portRange.low=20001", "spring.cloud.deployer.local.portRange.high=20003")
				.run((context) -> {
					assertThat(context).hasSingleBean(LocalDeployerProperties.class);
					assertThat(context).hasBean("localDeployerAutoConfiguration");
					assertThat(context).getBean(LocalDeployerProperties.class)
							.hasFieldOrPropertyWithValue("portRange.low", 20001);
					assertThat(context).getBean(LocalDeployerProperties.class)
							.hasFieldOrPropertyWithValue("portRange.high", 20003);
				});
	}
}
