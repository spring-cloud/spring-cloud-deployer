/*
 * Copyright 2018-2019 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerException;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.cloud.deployer.spi.test.EventuallyMatcher;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
/**
 * Contains base set of tests that are required for each implementation of
 * Spring Cloud Scheduler to pass.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ContextConfiguration(classes = AbstractSchedulerIntegrationTests.Config.class)
public abstract class AbstractSchedulerIntegrationTests {

	@Autowired
	protected MavenProperties mavenProperties;

	private SchedulerWrapper schedulerWrapper;

	/**
	 * Return the timeout to use for repeatedly querying that a task has been scheduled.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	private Timeout scheduleTimeout = new Timeout(12, 5000);

	/**
	 * Return the timeout to use for repeatedly querying whether a task has been unscheduled.
	 * Default value is one minute, being queried every 5 seconds.
	 */
	private Timeout unScheduleTimeout = new Timeout(12, 5000);

	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Rule
	public TestName name = new TestName();

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@After
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
	 */
	protected abstract Scheduler provideScheduler();

	/**
	 * To be implemented by subclasses, which should return the commandLineArgs that
	 * will be used for the tests.
	 */
	protected abstract List<String> getCommandLineArgs();

	/**
	 * To be implemented by subclasses, which should return the schedulerProperties that
	 * will be used for the tests.
	 */
	@Deprecated
	protected abstract Map<String, String> getSchedulerProperties();

	/**
	 * To be implemented by subclasses, which should return the deploymentProperties that
	 * will be used for the tests.
	 */
	protected abstract Map<String, String> getDeploymentProperties();

	/**
	 * To be implemented by subclasses, which should return the appProperties that
	 * will be used for the tests.
	 */
	protected abstract Map<String, String> getAppProperties();

	@Before
	public void wrapScheduler() {
		this.schedulerWrapper = new SchedulerWrapper(provideScheduler());
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
		assertEquals(0, taskScheduler().list().size() - initialSize);
	}

	@Test
	public void testDuplicateSchedule() {
		ScheduleRequest request = createScheduleRequest();
		taskScheduler().schedule(request);
		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(request.getScheduleName());

		this.expectedException.expect(CreateScheduleException.class);
		this.expectedException.expectMessage(String.format("Failed to create schedule %s", request.getScheduleName()));

		verifySchedule(scheduleInfo);
		taskScheduler().schedule(request);
	}

	@Test
	public void testUnScheduleNoEntry() {
		String definitionName = randomName();
		String scheduleName = scheduleName() + definitionName;

		this.expectedException.expect(SchedulerException.class);
		this.expectedException.expectMessage(String.format("Failed to unschedule schedule %s does not exist.",
				scheduleName));
		unscheduleTestSchedule(scheduleName);
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
		this.expectedException.expect(CreateScheduleException.class);

		taskScheduler().schedule(request);
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
		scheduleInfo.setScheduleName(scheduleName+0);
		scheduleInfo.setTaskDefinitionName(definitionName+0);
		assertThat(scheduleInfo, EventuallyMatcher.eventually(
				hasSpecifiedSchedulesByTaskDefinitionName(taskScheduler().list(definitionName+0),
						scheduleInfo.getTaskDefinitionName(), 2),
				this.scheduleTimeout.maxAttempts, this.scheduleTimeout.pause));
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
		assertThat(scheduleInfo, EventuallyMatcher.eventually(hasSpecifiedSchedule(taskScheduler().list(),
				scheduleInfo.getScheduleName()), this.scheduleTimeout.maxAttempts,
				this.scheduleTimeout.pause));
	}

	private void unscheduleTestSchedule(String scheduleName) {
		logger.info("unscheduling {}...", scheduleName);

		taskScheduler().unschedule(scheduleName);

		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(scheduleName);
		assertThat(scheduleInfo, EventuallyMatcher.eventually(specifiedScheduleNotPresent(
				taskScheduler().list(), scheduleName),
				this.unScheduleTimeout.maxAttempts, this.unScheduleTimeout.pause));

	}

	protected String randomName() {
		return name.getMethodName() + "-" + UUID.randomUUID().toString();
	}

	protected String scheduleName() {
		return "ScheduleName_";
	}

	/**
	 * A Hamcrest Matcher that queries the schedule list for a schedule name.
	 *
	 * @author Glenn Renfro
	 */
	protected Matcher<ScheduleInfo> hasSpecifiedSchedule(final List<ScheduleInfo> schedules, String scheduleName) {
		return new BaseMatcher<ScheduleInfo>() {

			@Override
			public boolean matches(Object item) {
				boolean result = false;
				for (ScheduleInfo scheduleInfo : schedules) {
					if (scheduleInfo.getScheduleName().equals(scheduleName)) {
						result = true;
						break;
					}
				}
				return result;
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("unable to find specified scheduleName ").appendValue(item).appendText(" ");
			}


			@Override
			public void describeTo(Description description) {
				description.appendText("unable to find specified scheduleName ");
			}
		};
	}

	/**
	 * A Hamcrest Matcher that queries the schedule list for a task definition name.
	 *
	 * @author Glenn Renfro
	 */
	protected Matcher<ScheduleInfo> hasSpecifiedSchedulesByTaskDefinitionName(final List<ScheduleInfo> schedules, String taskDefinitionName, int expectedScheduleCount) {
		return new BaseMatcher<ScheduleInfo>() {

			@Override
			public boolean matches(Object item) {
				boolean result = false;
				if(schedules.size() == expectedScheduleCount) {
					for (ScheduleInfo scheduleInfo : schedules) {
						if (scheduleInfo.getTaskDefinitionName().equals(taskDefinitionName)) {
							result = true;
							break;
						}
					}
				}
				return result;
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("unable to find specified taskDefinitionName ").appendValue(item).appendText(" ");
			}


			@Override
			public void describeTo(Description description) {
				description.appendText("unable to find specified taskDefinitionName ");
			}
		};
	}

	/**
	 * A Hamcrest Matcher that queries the schedule list for a definition name.
	 *
	 * @author Glenn Renfro
	 */
	protected Matcher<ScheduleInfo> specifiedScheduleNotPresent(final List<ScheduleInfo> schedules, String scheduleName) {
		return new BaseMatcher<ScheduleInfo>() {

			@Override
			public boolean matches(Object item) {
				boolean result = true;
				for (ScheduleInfo schedule : schedules) {
					if (schedule.getScheduleName().equals(scheduleName)) {
						result = false;
						break;
					}
				}
				return result;
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("unable to find specified scheduleName ").appendValue(item).appendText(" ");
			}


			@Override
			public void describeTo(Description description) {
				description.appendText("unable to find specified scheduleName ");
			}
		};
	}

	/**
	 * Return a resource corresponding to the spring-cloud-deployer-spi-scheduler-test-app app suitable for the target runtime.
	 *
	 * The default implementation returns an uber-jar fetched via Maven. Subclasses may override.
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
