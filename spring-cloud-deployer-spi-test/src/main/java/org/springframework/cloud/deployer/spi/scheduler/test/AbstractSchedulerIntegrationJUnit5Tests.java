/*
 * Copyright 2018-2021 the original author or authors.
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
package org.springframework.cloud.deployer.spi.scheduler.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerException;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Contains base set of tests that are required for each implementation of
 * Spring Cloud Scheduler to pass.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ContextConfiguration(classes = AbstractSchedulerIntegrationJUnit5Tests.Config.class)
public abstract class AbstractSchedulerIntegrationJUnit5Tests {

	@Autowired
	protected MavenProperties mavenProperties;

	private SchedulerWrapper schedulerWrapper;

	/**
	 * Return the timeout to use for repeatedly querying that a task has been scheduled.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	private Timeout scheduleTimeout = new Timeout(30, 5000);

	/**
	 * Return the timeout to use for repeatedly querying whether a task has been unscheduled.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	private Timeout unScheduleTimeout = new Timeout(12, 5000);

	protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private String testName;

	@AfterEach
	public void tearDown() {
		List<ScheduleRequest> scheduleRequests = new ArrayList<>(schedulerWrapper.getScheduledTasks().values());

		for (ScheduleRequest scheduleRequest : scheduleRequests) {
			unscheduleTestSchedule(scheduleRequest.getScheduleName());
		}

	}

	/**
	 * Subclasses should call this method to interact with the Scheduler under test.
	 * Returns a wrapper around the scheduler returned by {@link #provideScheduler()}, that keeps
	 * track of which tasks have been scheduled and unscheduled.
	 */
	protected Scheduler taskScheduler() {
		return this.schedulerWrapper;
	}

	/**
	 * To be implemented by subclasses, which should return the instance of Scheduler that needs
	 * to be tested. Can be used if subclasses decide to add additional implementation-specific tests.
	 * @return the scheduler
	 */
	protected abstract Scheduler provideScheduler();

	/**
	 * To be implemented by subclasses, which should return the commandLineArgs that
	 * will be used for the tests.
	 * @return the command line arguments
	 */
	protected abstract List<String> getCommandLineArgs();

	/**
	 * To be implemented by subclasses, which should return the schedulerProperties that
	 * will be used for the tests.
	 * @return the scheduler properties
	 */
	protected abstract Map<String, String> getSchedulerProperties();

	/**
	 * To be implemented by subclasses, which should return the deploymentProperties that
	 * will be used for the tests.
	 * @return the deployment properties
	 */
	protected abstract Map<String, String> getDeploymentProperties();

	/**
	 * To be implemented by subclasses, which should return the appProperties that
	 * will be used for the tests.
	 * @return the application properties
	 */
	protected abstract Map<String, String> getAppProperties();

	@BeforeEach
	public void wrapScheduler(TestInfo testInfo) {
		this.schedulerWrapper = new SchedulerWrapper(provideScheduler());
        Optional<Method> testMethod = testInfo.getTestMethod();
        if (testMethod.isPresent()) {
            this.testName = testMethod.get().getName();
        }
	}

	@Test
	public void testSimpleSchedule() {
		createAndVerifySchedule();
	}

	@Test
	public void testUnschedule() {
		int initialSize = taskScheduler().list().size();
		ScheduleInfo scheduleInfo = createAndVerifySchedule();
		unscheduleTestSchedule(scheduleInfo.getScheduleName());
		assertThat(taskScheduler().list().size() - initialSize).isEqualTo(0);
	}

	@Test
	public void testDuplicateSchedule() {
		ScheduleRequest request = createScheduleRequest();
		taskScheduler().schedule(request);
		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(request.getScheduleName());

		verifySchedule(scheduleInfo);
        assertThatThrownBy(() -> {
            taskScheduler().schedule(request);
        }).isInstanceOf(CreateScheduleException.class).hasMessageContaining("Failed to create schedule %s",
                request.getScheduleName());
	}

	@Test
	public void testUnScheduleNoEntry() {
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;

        assertThatThrownBy(() -> {
            unscheduleTestSchedule(scheduleName);
		}).isInstanceOf(SchedulerException.class).hasMessage("Failed to unschedule %s",
                scheduleName);
	}

	@Test
	public void testInvalidCronExpression() {
		final String INVALID_EXPRESSION = "BAD";
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;
		Map<String, String> properties = new HashMap<>(getDeploymentProperties());
		properties.put(SchedulerPropertyKeys.CRON_EXPRESSION, INVALID_EXPRESSION);
		AppDefinition definition = new AppDefinition(definitionName, properties);
		ScheduleRequest request = new ScheduleRequest(definition, properties, getCommandLineArgs(), scheduleName, testApplication());
        assertThatThrownBy(() -> {
            taskScheduler().schedule(request);
        }).isInstanceOf(CreateScheduleException.class);
	}

	@Test
	public void testMultipleSchedule() {
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;
		for (int i = 0; i < 4; i++) {
			ScheduleRequest request = createScheduleRequest(scheduleName + i, definitionName + i);
			taskScheduler().schedule(request);
		}
		List<ScheduleInfo> scheduleInfos = taskScheduler().list();

		for (ScheduleInfo scheduleInfo : scheduleInfos) {
			verifySchedule(scheduleInfo);
		}
	}

	@Test
	public void testListFilter() {
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;
		for (int i = 0; i < 4; i++) {
			ScheduleRequest request = createScheduleRequest(scheduleName + i, definitionName + i%2);
			taskScheduler().schedule(request);
		}
		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(scheduleName + 0);
		scheduleInfo.setTaskDefinitionName(definitionName + 0);
        await().pollInterval(Duration.ofMillis(this.scheduleTimeout.pause))
                .atMost(Duration.ofMillis(this.scheduleTimeout.totalTime))
                .untilAsserted(() -> {
                    ListScheduleInfoAssert.assertThat(taskScheduler().list(definitionName + 0))
                            .hasExpectedScheduleCount(definitionName + 0, 2);
                });
	}

	public Timeout getScheduleTimeout() {
		return scheduleTimeout;
	}

	public void setScheduleTimeout(Timeout scheduleTimeout) {
		this.scheduleTimeout = scheduleTimeout;
	}

	public Timeout getUnScheduleTimeout() {
		return unScheduleTimeout;
	}

	public void setUnScheduleTimeout(Timeout unScheduleTimeout) {
		this.unScheduleTimeout = unScheduleTimeout;
	}

	private ScheduleInfo createAndVerifySchedule() {
		ScheduleRequest request = createScheduleRequest();
		taskScheduler().schedule(request);
		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(request.getScheduleName());
		verifySchedule(scheduleInfo);
		return scheduleInfo;
	}

	private ScheduleRequest createScheduleRequest() {
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;
		return createScheduleRequest(scheduleName, definitionName);
	}

	private ScheduleRequest createScheduleRequest(String scheduleName, String definitionName) {
		AppDefinition definition = new AppDefinition(definitionName, getAppProperties());
		return new ScheduleRequest(definition, getDeploymentProperties(), getCommandLineArgs(), scheduleName, testApplication());
	}

	private void verifySchedule(ScheduleInfo scheduleInfo) {
        await().pollInterval(Duration.ofMillis(this.scheduleTimeout.pause))
                .atMost(Duration.ofMillis(this.scheduleTimeout.totalTime))
                .untilAsserted(() -> {
            ListScheduleInfoAssert.assertThat(taskScheduler().list()).hasSchedule(scheduleInfo.getScheduleName());
        });
	}

	private void unscheduleTestSchedule(String scheduleName) {
		log.info("unscheduling {}...", scheduleName);

		taskScheduler().unschedule(scheduleName);

		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(scheduleName);
        await().pollInterval(Duration.ofMillis(this.unScheduleTimeout.pause))
                .atMost(Duration.ofMillis(this.unScheduleTimeout.totalTime))
                .untilAsserted(() -> {
            ListScheduleInfoAssert.assertThat(taskScheduler().list()).hasNotSchedule(scheduleName);
        });
	}

	protected String randomName() {
		return this.testName + "-" + UUID.randomUUID();
	}

	protected String scheduleName() {
		return "ScheduleName_";
	}

    protected static class ListScheduleInfoAssert extends AbstractAssert<ListScheduleInfoAssert, List<ScheduleInfo>> {

        public ListScheduleInfoAssert(List<ScheduleInfo> scheduleInfos) {
            super(scheduleInfos, ListScheduleInfoAssert.class);
        }

        public static ListScheduleInfoAssert assertThat(List<ScheduleInfo> scheduleInfos) {
            return new ListScheduleInfoAssert(scheduleInfos);
        }

        public ListScheduleInfoAssert hasSchedule(String scheduleName) {
            isNotNull();
            if (!actual.stream().map(si -> si.getScheduleName()).anyMatch(sc -> sc.equals(scheduleName))) {
                failWithMessage("unable to find specified scheduleName <%s> ", scheduleName);
            }
            return this;
        }

        public ListScheduleInfoAssert hasNotSchedule(String scheduleName) {
            isNotNull();
            if (actual.stream().map(si -> si.getScheduleName()).anyMatch(sc -> sc.equals(scheduleName))) {
                failWithMessage("found specified scheduleName <%s> ", scheduleName);
            }
            return this;
        }

        public ListScheduleInfoAssert hasExpectedScheduleCount(String taskDefinitionName, int expectedScheduleCount) {
            this.isNotNull();
			if (actual.size() != expectedScheduleCount) {
				failWithMessage("given schedule info list doesn't match expected count <%s>, was <%s>",
						expectedScheduleCount, actual.size());
			}
            if (!actual.stream().map(si -> si.getTaskDefinitionName()).anyMatch(sc -> sc.equals(taskDefinitionName))) {
                failWithMessage("found specified scheduleName <%s> ", taskDefinitionName);
            }
            return this;
        }
    }

	/**
	 * Return a resource corresponding to the spring-cloud-deployer-spi-scheduler-test-app app suitable for the target runtime.
	 *
	 * The default implementation returns an uber-jar fetched via Maven. Subclasses may override.
	 * @return the resource of the test application.
	 */
	protected Resource testApplication() {
		Properties properties = new Properties();
		try {
			properties.load(new ClassPathResource("integration-test-app.properties").getInputStream());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to determine which version of spring-cloud-deployer-spi-scheduler-test-app to use", e);
		}
		return new MavenResource.Builder(mavenProperties)
				.groupId("org.springframework.cloud")
				.artifactId("spring-cloud-deployer-spi-scheduler-test-app")
				.classifier("exec")
				.version(properties.getProperty("version"))
				.extension("jar")
				.build();
	}

	/**
	 * A decorator for Scheduler that keeps track of scheduled/unscheduled tasks.
	 *
	 * @author Glenn Renfro
	 */
	protected static class SchedulerWrapper implements Scheduler {
		private final Scheduler wrapped;

		private final Map<String,ScheduleRequest> scheduledTasks = new HashMap<>();


		public SchedulerWrapper(Scheduler wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void schedule(ScheduleRequest scheduleRequest) {
			wrapped.schedule(scheduleRequest);
			scheduledTasks.put(scheduleRequest.getScheduleName(), scheduleRequest);
		}

		@Override
		public void unschedule(String scheduleName) {
			wrapped.unschedule(scheduleName);
			scheduledTasks.remove(scheduleName);
		}

		@Override
		public List<ScheduleInfo> list(String taskDefinitionName) {
			return wrapped.list(taskDefinitionName);
		}

		@Override
		public List<ScheduleInfo> list() {
			return wrapped.list();
		}

		public Map<String, ScheduleRequest> getScheduledTasks() {
			return  Collections.unmodifiableMap(scheduledTasks);
		}
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
