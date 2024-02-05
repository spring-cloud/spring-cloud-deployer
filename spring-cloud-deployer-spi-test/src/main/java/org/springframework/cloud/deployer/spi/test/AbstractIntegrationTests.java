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

package org.springframework.cloud.deployer.spi.test;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Abstract base class containing infrastructure for the TCK, common to both
 * {@link org.springframework.cloud.deployer.spi.app.AppDeployer} and
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher} tests.
 *
 * <p>Subclasses should explicitly declare additional config that should be used via the use of
 * {@link ContextConfiguration}.</p>
 *
 * @author Eric Bottard
 */

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment= WebEnvironment.NONE)
@ContextConfiguration(classes = AbstractIntegrationTests.Config.class)
public abstract class AbstractIntegrationTests {

	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Rule
	public TestName name = new TestName();

	@Autowired
	protected MavenProperties mavenProperties;

	protected String randomName() {
		return name.getMethodName() + "-" + UUID.randomUUID().toString();
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 * @return the deployment timeout value
	 */
	protected Timeout deploymentTimeout() {
		return new Timeout(30, 5000);
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being un-deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 * @return the undeployment timeout value
	 */
	protected Timeout undeploymentTimeout() {
		return new Timeout(20, 5000);
	}

	/**
	 * Return the time to wait between reusing deployment requests. This could be necessary to give
	 * some platforms time to clean up after undeployment.
	 * @return the redeployment pause
	 */
	protected int redeploymentPause() {
		return 0;
	}

	/**
	 * Return a resource corresponding to the spring-cloud-deployer-spi-test-app app suitable for the target runtime.
	 *
	 * The default implementation returns an uber-jar fetched via Maven. Subclasses may override.
	 * @return the resource for the application
	 */
	protected Resource testApplication() {
		Properties properties = new Properties();
		try {
			properties.load(new ClassPathResource("integration-test-app.properties").getInputStream());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to determine which version of spring-cloud-deployer-spi-test-app to use", e);
		}
		return new MavenResource.Builder(mavenProperties)
				.groupId("org.springframework.cloud")
				.artifactId("spring-cloud-deployer-spi-test-app")
				.classifier("exec")
				.version(properties.getProperty("version"))
				.extension("jar")
				.build();
	}

	@Configuration
	public static class Config {
		@Bean
		@ConfigurationProperties("maven")
		public MavenProperties mavenProperties() {
			return new MavenProperties();
		}
	}

}
