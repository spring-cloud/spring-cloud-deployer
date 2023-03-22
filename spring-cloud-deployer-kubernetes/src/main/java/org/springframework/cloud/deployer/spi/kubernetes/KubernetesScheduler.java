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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.StatusCause;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerException;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Kubernetes implementation of the {@link Scheduler} SPI.
 *
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
public class KubernetesScheduler extends AbstractKubernetesDeployer implements Scheduler {
	protected static final String SPRING_CRONJOB_ID_KEY = "spring-cronjob-id";

	private static final String SCHEDULE_EXPRESSION_FIELD_NAME = "spec.schedule";

	static final String KUBERNETES_DEPLOYER_CRON_CONCURRENCY_POLICY = KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".cron.concurrencyPolicy";

	static final String KUBERNETES_DEPLOYER_CRON_TTL_SECONDS_AFTER_FINISHED = KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".cron.ttlSecondsAfterFinished";

	public KubernetesScheduler(KubernetesClient client,
			KubernetesDeployerProperties properties) {
		Assert.notNull(client, "KubernetesClient must not be null");
		Assert.notNull(properties, "KubernetesSchedulerProperties must not be null");

		this.client = client;
		this.properties = properties;
		this.containerFactory = new DefaultContainerFactory(properties);
		this.deploymentPropertiesResolver = new DeploymentPropertiesResolver(
				(properties instanceof KubernetesSchedulerProperties) ?
						KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX :
						KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX, properties);
	}

	@Override
	public void schedule(ScheduleRequest scheduleRequest) {

		validateScheduleName(scheduleRequest);
		try {
			createCronJob(scheduleRequest);
		}
		catch (KubernetesClientException e) {
			String invalidCronExceptionMessage = getExceptionMessageForField(e, SCHEDULE_EXPRESSION_FIELD_NAME);

			if (StringUtils.hasText(invalidCronExceptionMessage)) {
				throw new CreateScheduleException(invalidCronExceptionMessage, e);
			}

			throw new CreateScheduleException("Failed to create schedule " + scheduleRequest.getScheduleName(), e);
		}
	}

	/**
	 * Merge the Deployment properties into Scheduler properties.
	 * This way, the CronJob's scheduler properties are updated with the deployer properties if set any.
	 * @param scheduleRequest the {@link ScheduleRequest}
	 * @return the merged schedule properties
	 */
	static Map<String, String> mergeSchedulerProperties(ScheduleRequest scheduleRequest) {
		Map<String, String> deploymentProperties = new HashMap<>();
		Map<String, String> schedulerProperties = scheduleRequest.getSchedulerProperties();
		if(scheduleRequest.getDeploymentProperties() != null) {
			deploymentProperties.putAll(scheduleRequest.getDeploymentProperties());
		}
		if (schedulerProperties != null) {
			for (Map.Entry<String, String> schedulerProperty : schedulerProperties.entrySet()) {
				String schedulerPropertyKey = schedulerProperty.getKey();
				if (StringUtils.hasText(schedulerPropertyKey) && schedulerPropertyKey.startsWith(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX)) {
					String deployerPropertyKey = KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX +
							schedulerPropertyKey.substring(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX.length());
					deploymentProperties.put(deployerPropertyKey, schedulerProperty.getValue());
				}
				else if(StringUtils.hasText(schedulerPropertyKey) && schedulerPropertyKey.startsWith(SchedulerPropertyKeys.PREFIX)) {
					if (!deploymentProperties.containsKey(schedulerPropertyKey)) {
						deploymentProperties.put(schedulerPropertyKey, schedulerProperty.getValue());
					}
				}
			}
		}
		if(!deploymentProperties.containsKey(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".restartPolicy")) {
			deploymentProperties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".restartPolicy", RestartPolicy.Never.name());
		}
		if(deploymentProperties.containsKey("spring.cloud.deployer.cron.expression")) {
			deploymentProperties.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".cron.expression", deploymentProperties.get("spring.cloud.deployer.cron.expression"));
		}
		Map<String, String> updatedDeploymentProperties = new HashMap<>();
		Map<String, String> updatedSchedulerProperties = new HashMap<>();
		for (Map.Entry<String, String> schedulerProperty : deploymentProperties.entrySet()) {
			String schedulerPropertyKey = schedulerProperty.getKey();
			if (StringUtils.hasText(schedulerPropertyKey) && schedulerPropertyKey.startsWith(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX)) {
				String deployerPropertyKey = KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX +
						schedulerPropertyKey.substring(KubernetesSchedulerProperties.KUBERNETES_SCHEDULER_PROPERTIES_PREFIX.length());
				updatedSchedulerProperties.put(deployerPropertyKey, schedulerProperty.getValue());
			}
			else {
				updatedDeploymentProperties.put(schedulerProperty.getKey(), schedulerProperty.getValue());
			}
		}
		deploymentProperties.clear();
		deploymentProperties.putAll(updatedDeploymentProperties);
		deploymentProperties.putAll(updatedSchedulerProperties);
		return deploymentProperties;
	}

	public void validateScheduleName(ScheduleRequest request) {
		if(request.getScheduleName() == null) {
			throw new CreateScheduleException("The name for the schedule request is null", null);
		}
		if(request.getScheduleName().length() > 52) {
			throw new CreateScheduleException(String.format("because Schedule Name: '%s' has too many characters.  Schedule name length must be 52 characters or less", request.getScheduleName()), null);
		}
		if(!Pattern.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", request.getScheduleName())) {
			throw new CreateScheduleException("Invalid Format for Schedule Name. Schedule name can only contain lowercase letters, numbers 0-9 and hyphens.", null);
		}

	}

	@Override
	public void unschedule(String scheduleName) {
		boolean unscheduled = this.client.batch().cronjobs().withName(scheduleName).delete();

		if (!unscheduled) {
			throw new SchedulerException("Failed to unschedule schedule " + scheduleName + " does not exist.");
		}
	}

	@Override
	public List<ScheduleInfo> list(String taskDefinitionName) {
		return list()
				.stream()
				.filter(scheduleInfo -> taskDefinitionName.equals(scheduleInfo.getTaskDefinitionName()))
				.collect(Collectors.toList());
	}

	@Override
	public List<ScheduleInfo> list() {
		CronJobList cronJobList = this.client.batch().cronjobs().list();

		List<CronJob> cronJobs = cronJobList.getItems();
		List<ScheduleInfo> scheduleInfos = new ArrayList<>();

		for (CronJob cronJob : cronJobs) {
			if (cronJob.getMetadata() != null && cronJob.getMetadata().getLabels() != null &&
					StringUtils.hasText(cronJob.getMetadata().getLabels().get(SPRING_CRONJOB_ID_KEY))) {
				Map<String, String> properties = new HashMap<>();
				properties.put(SchedulerPropertyKeys.CRON_EXPRESSION, cronJob.getSpec().getSchedule());

				ScheduleInfo scheduleInfo = new ScheduleInfo();
				scheduleInfo.setScheduleName(cronJob.getMetadata().getName());
				scheduleInfo.setTaskDefinitionName(cronJob.getMetadata().getLabels().get(SPRING_CRONJOB_ID_KEY));
				scheduleInfo.setScheduleProperties(properties);

				scheduleInfos.add(scheduleInfo);
			}
		}

		return scheduleInfos;
	}

	protected CronJob createCronJob(ScheduleRequest scheduleRequest) {
		Map<String, String> labels = new HashMap<>();
		labels.put(SPRING_CRONJOB_ID_KEY, scheduleRequest.getDefinition().getName());

		Map<String, String> schedulerProperties = mergeSchedulerProperties(scheduleRequest);

		String schedule = schedulerProperties.get("spring.cloud.deployer.kubernetes.cron.expression") != null ?
				schedulerProperties.get("spring.cloud.deployer.kubernetes.cron.expression") :
				schedulerProperties.get(SchedulerPropertyKeys.CRON_EXPRESSION);
		Assert.hasText(schedule, "The property spring.cloud.deployer.cron.expression must be defined");

		String concurrencyPolicy = schedulerProperties.get(KUBERNETES_DEPLOYER_CRON_CONCURRENCY_POLICY);
		// check default server properties
		if (!StringUtils.hasText(concurrencyPolicy)) {
			concurrencyPolicy = this.properties.getCron().getConcurrencyPolicy();
		}
		if(concurrencyPolicy==null) {
			concurrencyPolicy = "Allow";
		}

		final Integer ttlSecondsAfterFinished;
		String ttlSecondsAfterFinishedString = schedulerProperties.get(KUBERNETES_DEPLOYER_CRON_TTL_SECONDS_AFTER_FINISHED);
		if (StringUtils.hasText(ttlSecondsAfterFinishedString)) {
			ttlSecondsAfterFinished = Integer.parseInt(ttlSecondsAfterFinishedString);
		}
		else {
			ttlSecondsAfterFinished = this.properties.getCron().getTtlSecondsAfterFinished();
		}

		PodSpec podSpec = createPodSpec(new ScheduleRequest(scheduleRequest.getDefinition(),schedulerProperties, scheduleRequest.getCommandlineArguments(), scheduleRequest.getScheduleName(),scheduleRequest.getResource()));
		String taskServiceAccountName = this.deploymentPropertiesResolver.getTaskServiceAccountName(schedulerProperties);
		taskServiceAccountName = taskServiceAccountName != null ? taskServiceAccountName : KubernetesDeployerProperties.DEFAULT_TASK_SERVICE_ACCOUNT_NAME;
		if (StringUtils.hasText(taskServiceAccountName)) {
			podSpec.setServiceAccountName(taskServiceAccountName);
		}
		Map<String, String> annotations = this.deploymentPropertiesResolver.getPodAnnotations(schedulerProperties);
		labels.putAll(this.deploymentPropertiesResolver.getDeploymentLabels(schedulerProperties));

		CronJob cronJob = new CronJobBuilder().withNewMetadata().withName(scheduleRequest.getScheduleName())
				.withLabels(labels).withAnnotations(this.deploymentPropertiesResolver.getJobAnnotations(schedulerProperties)).endMetadata()
				.withNewSpec().withSchedule(schedule).withConcurrencyPolicy(concurrencyPolicy).withNewJobTemplate()
				.withNewSpec().withTtlSecondsAfterFinished(ttlSecondsAfterFinished)
				.withNewTemplate().withNewMetadata().addToAnnotations(annotations).addToLabels(labels)
				.endMetadata().withSpec(podSpec).endTemplate().endSpec()
				.endJobTemplate().endSpec().build();

		setImagePullSecret(scheduleRequest, cronJob);

		return this.client.batch().cronjobs().create(cronJob);
	}

	protected String getExceptionMessageForField(KubernetesClientException clientException,
			String fieldName) {
		if (clientException.getStatus() == null || clientException.getStatus().getDetails() == null) {
			return null;
		}
		List<StatusCause> statusCauses = clientException.getStatus().getDetails().getCauses();

		if (!CollectionUtils.isEmpty(statusCauses)) {
			for (StatusCause statusCause : statusCauses) {
				if (fieldName.equals(statusCause.getField())) {
					return clientException.getStatus().getMessage();
				}
			}
		}

		return null;
	}

	private void setImagePullSecret(ScheduleRequest scheduleRequest, CronJob cronJob) {

		String imagePullSecret = this.deploymentPropertiesResolver.getImagePullSecret(scheduleRequest.getDeploymentProperties());

		if (StringUtils.hasText(imagePullSecret)) {
			LocalObjectReference localObjectReference = new LocalObjectReference();
			localObjectReference.setName(imagePullSecret);

			cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getImagePullSecrets()
					.add(localObjectReference);
		}
	}
}
