/*
 * Copyright 2016-2021 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationJUnit5Tests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration tests for CloudFoundryAppDeployer.
 *
 * @author Eric Bottard
 * @author Greg Turnquist
 */


@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.NONE,
	properties = {"spring.cloud.deployer.cloudfoundry.enableRandomAppNamePrefix=false"})
@ContextConfiguration(classes = CloudFoundryAppDeployerIntegrationIT.Config.class)
public class CloudFoundryAppDeployerIntegrationIT extends AbstractAppDeployerIntegrationJUnit5Tests {

	@Autowired
	private AppDeployer appDeployer;

	@Override
	protected AppDeployer provideAppDeployer() {
		return appDeployer;
	}

	/**
	 * Execution environments may override this default value to have tests wait longer for a deployment, for example if
	 * running in an environment that is known to be slow.
	 */
	protected double timeoutMultiplier = 1.0D;

	protected int maxRetries = 60;

	@BeforeEach
	public void init() {
		String multiplier = System.getenv("CF_DEPLOYER_TIMEOUT_MULTIPLIER");
		if (multiplier != null) {
			timeoutMultiplier = Double.parseDouble(multiplier);
		}
	}

	@Override
	@Test
	@Disabled("Need to look into args escaping better. Disabling for the time being")
	public void testCommandLineArgumentsPassing() {
	}

	@Override
	protected String randomName() {
		// This will become the hostname part and is limited to 63 chars
		String name = super.randomName();
		return name.substring(0, Math.min(63, name.length()));
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(maxRetries, (int) (5000 * timeoutMultiplier));
	}

	@Override
	protected Timeout undeploymentTimeout() {
		return new Timeout(maxRetries, (int) (5000 * timeoutMultiplier));
	}

	/**
	 * This triggers the use of {@link CloudFoundryDeployerAutoConfiguration}.
	 *
	 * @author Eric Bottard
	 */
	@Configuration
	@EnableAutoConfiguration
	@EnableConfigurationProperties
	public static class Config {

	}
}
