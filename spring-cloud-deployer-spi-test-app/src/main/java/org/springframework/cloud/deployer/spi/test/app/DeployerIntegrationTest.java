/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.deployer.spi.test.app;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * An app that can misbehave, useful for integration testing of app deployers.
 *
 * @author Eric Bottard
 */
@EnableConfigurationProperties(DeployerIntegrationTestProperties.class)
@Configuration
public class DeployerIntegrationTest {

	@Autowired
	private DeployerIntegrationTestProperties properties;

	@PostConstruct
	public void init() throws InterruptedException {
		String parameterThatMayNeedEscaping = properties.getParameterThatMayNeedEscaping();
		if (parameterThatMayNeedEscaping != null && !DeployerIntegrationTestProperties.FUNNY_CHARACTERS.equals(parameterThatMayNeedEscaping)) {
			throw new IllegalArgumentException(
					String.format("Expected 'parameterThatMayNeedEscaping' value to be equal to '%s', but was '%s'",
							DeployerIntegrationTestProperties.FUNNY_CHARACTERS, parameterThatMayNeedEscaping));
		}

		String commandLineArgValueThatMayNeedEscaping = properties.getCommandLineArgValueThatMayNeedEscaping();
		if (commandLineArgValueThatMayNeedEscaping != null && !DeployerIntegrationTestProperties.FUNNY_CHARACTERS.equals(commandLineArgValueThatMayNeedEscaping)) {
			throw new IllegalArgumentException(String.format(
					"Expected 'commandLineArgValueThatMayNeedEscaping' value to be equal to '%s', but was '%s'",
					DeployerIntegrationTestProperties.FUNNY_CHARACTERS, commandLineArgValueThatMayNeedEscaping));
		}

		Assert.notNull(properties.getInstanceIndex(), "instanceIndex should have been set by deployer or runtime");

		if (properties.getMatchInstances().isEmpty() || properties.getMatchInstances().contains(properties.getInstanceIndex())) {
			System.out.format("Waiting for %dms before allowing further initialization and actuator startup...", properties.getInitDelay());
			Thread.sleep(properties.getInitDelay());
			System.out.println("... done");
			if (properties.getKillDelay() >= 0) {
				System.out.format("Will kill this process in %dms%n", properties.getKillDelay());
				new Thread() {

					@Override
					public void run() {
						try {
							Thread.sleep(properties.getKillDelay());
							System.exit(properties.getExitCode());
						}
						catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
				}.start();
			}
		}
	}

}
