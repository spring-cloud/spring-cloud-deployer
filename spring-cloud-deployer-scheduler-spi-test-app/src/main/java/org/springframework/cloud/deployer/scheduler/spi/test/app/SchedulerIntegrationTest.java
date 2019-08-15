/*
 * Copyright 2018-2019 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.deployer.scheduler.spi.test.app;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import static org.springframework.cloud.deployer.scheduler.spi.test.app.SchedulerIntegrationTestProperties.FUNNY_CHARACTERS;

/**
 * An app that can misbehave, useful for integration testing of app deployers.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
@EnableConfigurationProperties(SchedulerIntegrationTestProperties.class)
@Configuration
public class SchedulerIntegrationTest {

	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private SchedulerIntegrationTestProperties properties;

	@PostConstruct
	public void init() throws InterruptedException {
		String parameterThatMayNeedEscaping = properties.getParameterThatMayNeedEscaping();
		if (parameterThatMayNeedEscaping != null && !FUNNY_CHARACTERS.equals(parameterThatMayNeedEscaping)) {
			throw new IllegalArgumentException(String.format("Expected 'parameterThatMayNeedEscaping' value to be equal to '%s', but was '%s'", FUNNY_CHARACTERS, parameterThatMayNeedEscaping));
		}

		String commandLineArgValueThatMayNeedEscaping = properties.getCommandLineArgValueThatMayNeedEscaping();
		if (commandLineArgValueThatMayNeedEscaping != null && !FUNNY_CHARACTERS.equals(commandLineArgValueThatMayNeedEscaping)) {
			throw new IllegalArgumentException(String.format("Expected 'commandLineArgValueThatMayNeedEscaping' value to be equal to '%s', but was '%s'", FUNNY_CHARACTERS, commandLineArgValueThatMayNeedEscaping));
		}

		Assert.notNull(properties.getInstanceIndex(), "instanceIndex should have been set by deployer or runtime");

		if (properties.getMatchInstances().isEmpty() || properties.getMatchInstances().contains(properties.getInstanceIndex())) {
			log.info("Waiting for %dms before allowing further initialization and actuator startup...", properties.getInitDelay());
			Thread.sleep(properties.getInitDelay());
			log.info("... done");
			if (properties.getKillDelay() >= 0) {
				log.info("Will kill this process in %dms%n", properties.getKillDelay());
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
