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

package org.springframework.cloud.deployer.spi.kubernetes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusCause;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobList;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.v1beta1.JobTemplateSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.cloud.deployer.spi.scheduler.test.AbstractSchedulerIntegrationJUnit5Tests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Kubernetes {@link Scheduler} implementation.
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ContextConfiguration(classes = {KubernetesSchedulerIT.Config.class})
public class KubernetesSchedulerIT extends AbstractSchedulerIntegrationJUnit5Tests {

	@Autowired
	private Scheduler scheduler;

	@Autowired
	private KubernetesClient kubernetesClient;

	@Override
	protected Scheduler provideScheduler() {
		return this.scheduler;
	}

	@Override
	protected List<String> getCommandLineArgs() {
		List<String> commandLineArguments = new ArrayList<>();
		commandLineArguments.add("arg1=value1");
		commandLineArguments.add("arg2=value2");

		return commandLineArguments;
	}

	@Override
	protected Map<String, String> getSchedulerProperties() {
		return Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, "57 13 ? * *");
	}


	private Map<String, String> getSchedulerProperties(String concurrencyPolicy) {
		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(KubernetesScheduler.KUBERNETES_DEPLOYER_CRON_CONCURRENCY_POLICY, concurrencyPolicy);
		return schedulerProperties;
	}

	@Override
	protected Map<String, String> getDeploymentProperties() {
		return Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, "57 13 ? * *");
	}

	@Override
	protected Map<String, String> getAppProperties() {
		Map<String, String> applicationProperties = new HashMap<>();
		applicationProperties.put("prop.1.key", "prop.1.value");
		applicationProperties.put("prop.2.key", "prop.2.value");

		return applicationProperties;
	}

	@Override
	// schedule name must match "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$" and size must be between 0
	// and 63
	protected String randomName() {
		return UUID.randomUUID().toString().substring(0, 18);
	}

	@Override
	// schedule name must match "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$" and size must be between 0
	// and 63
	protected String scheduleName() {
		return "schedulename-";
	}

	protected Resource testApplication() {
		return new DockerResource("springcloud/spring-cloud-deployer-spi-scheduler-test-app:latest");
	}

	@Test
	public void test() {
		super.testListFilter();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testMissingSchedule(boolean isDeprecated) {
		AppDefinition appDefinition = new AppDefinition(randomName(), null);
		ScheduleRequest scheduleRequest = (isDeprecated) ?
				new ScheduleRequest(appDefinition, null, null, null, null, testApplication()) :
				new ScheduleRequest(appDefinition, null, (List<String>) null, null, testApplication());

		assertThatThrownBy(() -> {
			scheduler.schedule(scheduleRequest);
		}).isInstanceOf(CreateScheduleException.class);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testInvalidNameSchedule(boolean isDeprecated) {
		AppDefinition appDefinition = new AppDefinition("AAAAAA", null);
		ScheduleRequest scheduleRequest = (isDeprecated) ?
				new ScheduleRequest(appDefinition, null, null, null, "AAAAA", testApplication()) :
				new ScheduleRequest(appDefinition, null, (List<String>) null, "AAAAA", testApplication());

		assertThatThrownBy(() -> {
			scheduler.schedule(scheduleRequest);
		}).isInstanceOf(CreateScheduleException.class);
	}

	@Test
	public void testSchedulerPropertiesMerge() {
		final String baseScheduleName = "test-schedule1";
		Map<String, String> schedulerProperties = new HashMap<>();
		schedulerProperties.put(SchedulerPropertyKeys.CRON_EXPRESSION, "0/10 * * * *");
		schedulerProperties.put(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX + ".imagePullPolicy", "Never");
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".environmentVariables", "MYVAR1=MYVAL1,MYVAR2=MYVAL2");
		deploymentProperties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".imagePullPolicy", "Always");
		AppDefinition appDefinition = new AppDefinition(randomName(), null);
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, schedulerProperties, deploymentProperties, null,
				baseScheduleName, testApplication());

		Map<String, String> mergedProperties = KubernetesScheduler.mergeSchedulerProperties(scheduleRequest);

		assertThat(mergedProperties
				.get(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".imagePullPolicy"))
				.as("Expected value from Scheduler properties, but found in Deployer properties")
				.isEqualTo("Never");
		assertThat(mergedProperties
				.get(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".environmentVariables"))
				.as("Deployer property is expected to be merged as scheduler property")
				.isEqualTo("MYVAR1=MYVAL1,MYVAR2=MYVAL2");
	}

	@Test
	public void listScheduleWithExternalCronJobs() {
		CronJobList cronJobList = new CronJobList();
		CronJobSpec cronJobSpec = new CronJobSpec();
		JobTemplateSpec jobTemplateSpec = new JobTemplateSpec();
		JobSpec jobSpec = new JobSpec();
		PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
		PodSpec podSpec = new PodSpec();
		Container container = new Container();
		container.setName("test");
		container.setImage("busybox");
		podSpec.setContainers(Arrays.asList(container));
		podSpec.setRestartPolicy("OnFailure");
		podTemplateSpec.setSpec(podSpec);
		jobSpec.setTemplate(podTemplateSpec);
		jobTemplateSpec.setSpec(jobSpec);
		cronJobSpec.setJobTemplate(jobTemplateSpec);
		cronJobSpec.setSchedule("0/10 * * * *");

		CronJob cronJob1 = new CronJob();
		ObjectMeta objectMeta1 = new ObjectMeta();
		Map<String, String> labels = new HashMap<>();
		labels.put("spring-cronjob-id", "test");
		objectMeta1.setLabels(labels);
		objectMeta1.setName("job1");
		cronJob1.setMetadata(objectMeta1);
		cronJob1.setSpec(cronJobSpec);
		ObjectMeta objectMeta2 = new ObjectMeta();
		objectMeta2.setName("job2");
		CronJob cronJob2 = new CronJob();
		cronJob2.setSpec(cronJobSpec);
		cronJob2.setMetadata(objectMeta2);
		ObjectMeta objectMeta3 = new ObjectMeta();
		objectMeta3.setName("job3");
		CronJob cronJob3 = new CronJob();
		cronJob3.setSpec(cronJobSpec);
		cronJob3.setMetadata(objectMeta3);
		cronJobList.setItems(Arrays.asList(cronJob1, cronJob2, cronJob3));
		this.kubernetesClient.batch().cronjobs().create(cronJob1);
		this.kubernetesClient.batch().cronjobs().create(cronJob2);
		this.kubernetesClient.batch().cronjobs().create(cronJob3);
		List<ScheduleInfo> scheduleInfos = this.scheduler.list();
		assertThat(scheduleInfos.size() == 1);
		assertThat(scheduleInfos.get(0).getScheduleName().equals("job1"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testInvalidCronSyntax(boolean isDeprecated) {
		Map<String, String> schedulerProperties = Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, "1 2 3 4");

		AppDefinition appDefinition = new AppDefinition(randomName(), null);
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, null, randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, (List<String>) null, randomName(), testApplication());

		assertThatThrownBy(() -> {
			scheduler.schedule(scheduleRequest);
		}).isInstanceOf(CreateScheduleException.class);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testNameTooLong(boolean isDeprecated) {
		final String baseScheduleName = (isDeprecated) ? "tencharlng-scdf-itcouldbesaidthatthisislongtoowayold" :
				"tencharlng-scdf-itcouldbesaidthatthisislongtoowaytoo";
		Map<String, String> schedulerProperties = Collections.singletonMap(SchedulerPropertyKeys.CRON_EXPRESSION, "0/10 * * * *");

		AppDefinition appDefinition = new AppDefinition(randomName(), null);
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, null, baseScheduleName, testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, (List<String>) null, baseScheduleName, testApplication());

		//verify no validation fired.
		scheduler.schedule(scheduleRequest);

		ScheduleRequest scheduleRequest2 = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, null, baseScheduleName + "1", testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, (List<String>) null, baseScheduleName + "1", testApplication());
		assertThatThrownBy(() -> {
			scheduler.schedule(scheduleRequest2);
		}).isInstanceOf(CreateScheduleException.class)
				.hasMessage(String.format("Failed to create schedule because Schedule Name: '%s' has too many characters.  Schedule name length must be 52 characters or less", baseScheduleName + "1"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testWithExecEntryPoint(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.setEntryPointStyle(EntryPointStyle.exec);

		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getArgs()).as("Command line arguments should not be null").isNotNull();
		assertThat(container.getEnv()).as("Environment variables should not be null").isNotNull();
		assertThat(container.getEnv()).as("Environment variables should only have SPRING_CLOUD_APPLICATION_GUID")
				.hasSize(1);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testWithShellEntryPoint(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.setEntryPointStyle(EntryPointStyle.shell);

		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getSchedulerProperties(), getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getArgs()).as("Command line arguments should not be null").isNotNull();
		assertThat(container.getArgs()).as("Invalid number of command line arguments").isEmpty();

		assertThat(container.getEnv()).as("Environment variables should not be null").isNotNull();
		assertThat(container.getEnv()).as("Invalid number of environment variables").hasSizeGreaterThan(1);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testWithBootEntryPoint(boolean isDeprecated) throws IOException {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setEntryPointStyle(EntryPointStyle.boot);
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getSchedulerProperties(), getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getArgs()).as("Command line arguments should not be null").isNotNull();
		assertThat(container.getArgs()).as("Invalid number of command line arguments").hasSize(2);

		assertThat(container.getEnv()).as("Environment variables should not be null").isNotNull();
		assertThat(container.getEnv()).as("Invalid number of environment variables").hasSizeGreaterThan(1);

		String springApplicationJson = container.getEnv().get(0).getValue();

		Map<String, String> springApplicationJsonValues = new ObjectMapper().readValue(springApplicationJson,
				new TypeReference<HashMap<String, String>>() {
				});

		assertThat(springApplicationJsonValues).as("SPRING_APPLICATION_JSON should not be null").isNotNull();
		assertThat(springApplicationJsonValues).as("Invalid number of SPRING_APPLICATION_JSON entries").hasSize(2);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@Test
	public void testGetExceptionMessageForExistingField() {
		StatusCause statusCause = new StatusCause("spec.schedule", null, null);
		StatusDetails statusDetails = new StatusDetails();
		statusDetails.setCauses(Collections.singletonList(statusCause));

		Status status = new Status();
		status.setCode(0);
		status.setMessage("invalid cron expression");
		status.setDetails(statusDetails);

		KubernetesClientException kubernetesClientException = new KubernetesClientException(status);
		String message = ((KubernetesScheduler) scheduler).getExceptionMessageForField(kubernetesClientException,
				"spec.schedule");

		assertThat(message).as("Field message should not be null").isNotNull();
		assertThat(message).as("Invalid message for field").isEqualTo("invalid cron expression");
	}

	@Test
	public void testGetExceptionMessageForNonExistentField() {
		StatusCause statusCause = new StatusCause("spec.schedule", null, null);
		StatusDetails statusDetails = new StatusDetails();
		statusDetails.setCauses(Collections.singletonList(statusCause));

		Status status = new Status();
		status.setCode(0);
		status.setMessage("invalid cron expression");
		status.setDetails(statusDetails);

		KubernetesClientException kubernetesClientException = new KubernetesClientException(status);
		String message = ((KubernetesScheduler) scheduler).getExceptionMessageForField(kubernetesClientException,
				"spec.restartpolicy");

		assertThat(message).as("Field message should be null").isNull();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testEntryPointStyleOverride(boolean isDeprecated) throws Exception {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		String prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".entryPointStyle", "boot");

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getEnv()).as("Invalid number of environment variables").hasSizeGreaterThan(1);

		String springApplicationJson = container.getEnv().get(0).getValue();

		Map<String, String> springApplicationJsonValues = new ObjectMapper().readValue(springApplicationJson,
				new TypeReference<HashMap<String, String>>() {
				});

		assertThat(springApplicationJsonValues).as("SPRING_APPLICATION_JSON should not be null").isNotNull();
		assertThat(springApplicationJsonValues).as("Invalid number of SPRING_APPLICATION_JSON entries").hasSize(2);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testEntryPointStyleDefault(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), Collections.emptyMap(), getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getEnv()).as("Environment variables should only have SPRING_CLOUD_APPLICATION_GUID")
				.hasSize(1);
		assertThat(container.getArgs()).as("Command line arguments should not be empty").isNotEmpty();

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testImagePullPolicyOverride(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		String prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".imagePullPolicy", "Always");

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getImagePullPolicy()).as("Unexpected image pull policy").isEqualTo("Always");

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testJobAnnotationsAndLabelsFromSchedulerProperties(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setJobAnnotations("test1:value1");
		kubernetesDeployerProperties.setPodAnnotations("podtest1:podvalue1");
		kubernetesDeployerProperties.setDeploymentLabels("label1:value1,label2:value2");
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getSchedulerProperties(), getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getMetadata().getAnnotations().get("test1")).as("Job annotation is not set")
				.isEqualTo("value1");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getAnnotations()
				.get("podtest1")).as("Pod annotation is not set").isEqualTo("podvalue1");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("label1")).as("Pod Label1 is not set").isEqualTo("value1");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("label2")).as("Pod Label2 is not set").isEqualTo("value2");

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@Test
	public void testDefaultLabel() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(),
				null, getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getAnnotations()).isNull();
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.size()).as("Should have one label").isEqualTo(1);
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels().get(
				KubernetesScheduler.SPRING_CRONJOB_ID_KEY)).as("Default label is not set").isNotNull();
		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testJobAnnotationsAndLabelsFromSchedulerRequest(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setJobAnnotations("test1:value1");
		kubernetesDeployerProperties.setPodAnnotations("podtest1:podvalue1");
		kubernetesDeployerProperties.setDeploymentLabels("label1:value1,label2:value2");
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		Map<String, String> scheduleProperties = new HashMap<>();
		scheduleProperties.putAll(getSchedulerProperties());
		if (isDeprecated) {
			scheduleProperties.put("spring.cloud.scheduler.kubernetes.deploymentLabels", "requestLabel1:requestValue1,requestLabel2:requestValue2");
			scheduleProperties.put("spring.cloud.scheduler.kubernetes.podAnnotations", "requestPod1:requestPodValue1");
		}
		else {
			scheduleProperties.put("spring.cloud.deployer.kubernetes.deploymentLabels", "requestLabel1:requestValue1,requestLabel2:requestValue2");
			scheduleProperties.put("spring.cloud.deployer.kubernetes.podAnnotations", "requestPod1:requestPodValue1");
		}
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, scheduleProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, scheduleProperties, getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getMetadata().getAnnotations().get("test1")).as("Job annotation is not set")
				.isEqualTo("value1");
		// Pod annotation from the request should override the top level property values
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getAnnotations()
				.get("requestPod1")).as("Pod annotation is not set").isEqualTo("requestPodValue1");
		// Deployment label from the request should get appended to the top level property values
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("requestLabel1")).as("Pod Label1 from the request is not set").isEqualTo("requestValue1");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("requestLabel2")).as("Pod Label2 from the request is not set").isEqualTo("requestValue2");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("label1")).as("Pod Label1 is not set").isEqualTo("value1");
		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata().getLabels()
				.get("label2")).as("Pod Label2 is not set").isEqualTo("value2");

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testJobAnnotationsOverride(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setJobAnnotations("test1:value1");
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		String prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX;
		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".jobAnnotations", "test1:value2");

		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getMetadata().getAnnotations().get("test1")).as("Job annotation is not set")
				.isEqualTo("value2");

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testImagePullPolicyDefault(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), Collections.emptyMap(), getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(ImagePullPolicy.relaxedValueOf(container.getImagePullPolicy())).as("Unexpected default image pull policy")
				.isEqualTo(ImagePullPolicy.IfNotPresent);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testImagePullSecret(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		String secretName = "mysecret";
		String prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".imagePullSecret", secretName);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		List<LocalObjectReference> secrets = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec()
				.getImagePullSecrets();
		assertThat(secrets.get(0).getName()).as("Unexpected image pull secret").isEqualTo(secretName);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testImagePullSecretDefault(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), Collections.emptyMap(), getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		List<LocalObjectReference> secrets = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec()
				.getImagePullSecrets();
		assertThat(secrets).as("There should be no secrets").isEmpty();

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testImagePullSecretFromSchedulerProperties(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}

		String secretName = "image-secret";
		kubernetesDeployerProperties.setImagePullSecret(secretName);
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(), Collections.emptyMap(), getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		List<LocalObjectReference> secrets = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec()
				.getImagePullSecrets();
		assertThat(secrets.get(0).getName()).as("Unexpected image pull secret").isEqualTo(secretName);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testCustomEnvironmentVariables(boolean isDeprecated) {
		String prefix = (isDeprecated) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".environmentVariables", "MYVAR1=MYVAL1,MYVAR2=MYVAL2");

		EnvVar[] expectedVars = new EnvVar[] {new EnvVar("MYVAR1", "MYVAL1", null),
				new EnvVar("MYVAR2", "MYVAL2", null)};

		testEnvironmentVariables(new KubernetesDeployerProperties(), schedulerProperties, expectedVars, isDeprecated);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testGlobalEnvironmentVariables(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.setEnvironmentVariables(new String[] {"MYVAR1=MYVAL1", "MYVAR2=MYVAL2"});

		EnvVar[] expectedVars = new EnvVar[] {new EnvVar("MYVAR1", "MYVAL1", null),
				new EnvVar("MYVAR2", "MYVAL2", null)};

		testEnvironmentVariables(kubernetesDeployerProperties, getSchedulerProperties(), expectedVars, isDeprecated);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testCustomEnvironmentVariablesWithNestedComma(boolean isDeprecated) {
		String prefix = (isDeprecated) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".environmentVariables", "MYVAR='VAL1,VAL2',MYVAR2=MYVAL2");

		EnvVar[] expectedVars = new EnvVar[] {new EnvVar("MYVAR", "VAL1,VAL2", null),
				new EnvVar("MYVAR2", "MYVAL2", null)};

		testEnvironmentVariables((isDeprecated) ? new KubernetesSchedulerProperties() : new KubernetesDeployerProperties(), schedulerProperties, expectedVars, isDeprecated);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testGlobalAndCustomEnvironmentVariables(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();

		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.setEnvironmentVariables(new String[] {"MYVAR1=MYVAL1", "MYVAR2=MYVAL2"});

		String prefix = (isDeprecated) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".environmentVariables", "MYVAR3=MYVAL3,MYVAR4=MYVAL4");

		EnvVar[] expectedVars = new EnvVar[] {new EnvVar("MYVAR1", "MYVAL1", null),
				new EnvVar("MYVAR2", "MYVAL2", null), new EnvVar("MYVAR3", "MYVAL3", null),
				new EnvVar("MYVAR4", "MYVAL4", null)};

		testEnvironmentVariables(kubernetesDeployerProperties, schedulerProperties, expectedVars, isDeprecated);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testCustomEnvironmentVariablesOverrideGlobal(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.setEnvironmentVariables(new String[] {"MYVAR1=MYVAL1", "MYVAR2=MYVAL2"});

		String prefix = (isDeprecated) ? KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".environmentVariables", "MYVAR2=OVERRIDE");

		EnvVar[] expectedVars = new EnvVar[] {new EnvVar("MYVAR1", "MYVAL1", null),
				new EnvVar("MYVAR2", "OVERRIDE", null)};

		testEnvironmentVariables(kubernetesDeployerProperties, schedulerProperties, expectedVars, isDeprecated);
	}

	private void testEnvironmentVariables(KubernetesDeployerProperties kubernetesDeployerProperties,
			Map<String, String> schedulerProperties, EnvVar[] expectedVars, boolean isDeprecated) {
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, schedulerProperties, null, getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, schedulerProperties, getCommandLineArgs(), randomName(), testApplication());
		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		Container container = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec().getContainers().get(0);

		assertThat(container.getEnv()).as("Environment variables should not be empty").isNotEmpty();

		assertThat(container.getEnv()).contains(expectedVars);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testTaskServiceAccountNameOverride(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		String taskServiceAccountName = "mysa";
		String prefix = KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX;

		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(prefix + ".taskServiceAccountName", taskServiceAccountName);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, schedulerProperties,
				null, getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		String serviceAccountName = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec()
				.getServiceAccountName();
		assertThat(serviceAccountName).as("Unexpected service account name").isEqualTo(taskServiceAccountName);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testTaskServiceAccountNameDefault(boolean isDeprecated) {
		KubernetesDeployerProperties kubernetesDeployerProperties =
				new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = (isDeprecated) ? new ScheduleRequest(appDefinition, getSchedulerProperties(),
				getDeploymentProperties(), getCommandLineArgs(), randomName(), testApplication()) :
				new ScheduleRequest(appDefinition, getDeploymentProperties(),
						getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);
		CronJobSpec cronJobSpec = cronJob.getSpec();

		String serviceAccountName = cronJobSpec.getJobTemplate().getSpec().getTemplate().getSpec()
				.getServiceAccountName();
		assertThat(serviceAccountName).as("Unexpected service account name")
				.isEqualTo(KubernetesSchedulerProperties.DEFAULT_TASK_SERVICE_ACCOUNT_NAME);

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"Forbid", "Allow", "Replace"})
	public void testConcurrencyPolicy(String concurrencyPolicy) {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(concurrencyPolicy),
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getConcurrencyPolicy()).isEqualTo(concurrencyPolicy);


		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@Test
	public void testConcurrencyPolicyDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(),
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getConcurrencyPolicy()).isEqualTo("Allow");


		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@Test
	public void testConcurrencyPolicyFromServerProperties() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.getCron().setConcurrencyPolicy("Forbid");
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(),
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getConcurrencyPolicy()).isEqualTo("Forbid");


		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"100", "86400"})
	public void testTtlSecondsAfterFinished(String ttlSecondsAfterFinished) {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(KubernetesScheduler.KUBERNETES_DEPLOYER_CRON_TTL_SECONDS_AFTER_FINISHED, ttlSecondsAfterFinished);

		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, schedulerProperties,
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTtlSecondsAfterFinished())
				.isEqualTo(Integer.parseInt(ttlSecondsAfterFinished));

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"100 2", "one", "10'"})
	public void testInvalidTtlSecondsAfterFinished(String ttlSecondsAfterFinished) {
		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		Map<String, String> schedulerProperties = new HashMap<>(getSchedulerProperties());
		schedulerProperties.put(KubernetesScheduler.KUBERNETES_DEPLOYER_CRON_TTL_SECONDS_AFTER_FINISHED, ttlSecondsAfterFinished);

		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, schedulerProperties,
				getCommandLineArgs(), randomName(), testApplication());

		assertThatThrownBy(() -> {
			scheduler.schedule(scheduleRequest);
		}).isInstanceOf(NumberFormatException.class);
	}

	@Test
	public void testTtlSecondsAfterFinishedDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());

		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(),
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTtlSecondsAfterFinished())
				.isNull();

		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@Test
	public void testTtlSecondsAfterFinishedFromServerProperties() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");
		}
		kubernetesDeployerProperties.getCron().setTtlSecondsAfterFinished(86400);
		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace());

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		AppDefinition appDefinition = new AppDefinition(randomName(), getAppProperties());
		ScheduleRequest scheduleRequest = new ScheduleRequest(appDefinition, getSchedulerProperties(),
				getCommandLineArgs(), randomName(), testApplication());

		CronJob cronJob = kubernetesScheduler.createCronJob(scheduleRequest);

		assertThat(cronJob.getSpec().getJobTemplate().getSpec().getTtlSecondsAfterFinished()).isEqualTo(86400);


		kubernetesScheduler.unschedule(cronJob.getMetadata().getName());
	}

	@AfterAll
	public static void cleanup() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

		KubernetesClient kubernetesClient = new DefaultKubernetesClient()
				.inNamespace(kubernetesDeployerProperties.getNamespace() != null
						? kubernetesDeployerProperties.getNamespace() : "default");

		KubernetesScheduler kubernetesScheduler = new KubernetesScheduler(kubernetesClient,
				kubernetesDeployerProperties);

		List<ScheduleInfo> scheduleInfos = kubernetesScheduler.list();

		for (ScheduleInfo scheduleInfo : scheduleInfos) {
			kubernetesScheduler.unschedule(scheduleInfo.getScheduleName());
		}
		// Cleanup the schedules that aren't part of the list() - created from listScheduleWithExternalCronJobs test
		kubernetesScheduler.unschedule("job2");
		kubernetesScheduler.unschedule("job3");
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigurationProperties
	public static class Config {
		private KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

		@Bean
		public Scheduler scheduler(KubernetesClient kubernetesClient) {
			return new KubernetesScheduler(kubernetesClient, kubernetesDeployerProperties);
		}

		@Bean
		public KubernetesClient kubernetesClient() {
			if (kubernetesDeployerProperties.getNamespace() == null) {
				kubernetesDeployerProperties.setNamespace("default");
			}

			return KubernetesClientFactory.getKubernetesClient(kubernetesDeployerProperties);
		}
	}
}
