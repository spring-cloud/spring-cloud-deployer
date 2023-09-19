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

package org.springframework.cloud.deployer.spi.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

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

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment= WebEnvironment.NONE)
@ContextConfiguration(classes = AbstractIntegrationTests.Config.class)
public abstract class AbstractIntegrationJUnit5Tests {

	protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected String testName;

	@Autowired
	protected MavenProperties mavenProperties;

	@BeforeEach
	public void setup(TestInfo testInfo) {
        Optional<Method> testMethod = testInfo.getTestMethod();
		testMethod.ifPresent(method -> this.testName = method.getName());
	}

	protected String randomName() {
		return this.testName + "-" + UUID.randomUUID().toString();
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 * @return the timeout
	 */
	protected Timeout deploymentTimeout() {
		return new Timeout(12, 5000);
	}

	/**
	 * Return the timeout to use for repeatedly querying app status while it is being un-deployed.
	 * Default value is one minute, being queried every 5 seconds.
	 * @return the timeout
	 */
	protected Timeout undeploymentTimeout() {
		return new Timeout(20, 5000);
	}

	/**
	 * Return the time to wait between reusing deployment requests. This could be necessary to give
	 * some platforms time to clean up after undeployment.
	 * @return redeployment pause
	 */
	protected int redeploymentPause() {
		return 0;
	}

	/**
	 * Return a resource corresponding to the spring-cloud-deployer-spi-test-app app suitable for the target runtime.
	 *
	 * The default implementation returns an uber-jar fetched via Maven. Subclasses may override.
	 * @return the resource
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
