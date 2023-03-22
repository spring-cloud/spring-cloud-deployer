/*
 * Copyright 2016-2022 the original author or authors.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.hashids.Hashids;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.kubernetes.support.PropertyParserUtils;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * A task launcher that targets Kubernetes.
 *
 * @author Thomas Risberg
 * @author David Turanski
 * @author Leonardo Diniz
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 * @author Glenn Renfro
 */
public class KubernetesTaskLauncher extends AbstractKubernetesDeployer implements TaskLauncher {
	private KubernetesTaskLauncherProperties taskLauncherProperties;

	@Autowired
	public KubernetesTaskLauncher(KubernetesDeployerProperties properties,
			KubernetesClient client) {
		this(properties, new KubernetesTaskLauncherProperties(), client, new DefaultContainerFactory(properties));
	}

	@Autowired
	public KubernetesTaskLauncher(KubernetesDeployerProperties properties,
			KubernetesClient client, ContainerFactory containerFactory) {
		this(properties, new KubernetesTaskLauncherProperties(), client, containerFactory);
	}

	@Autowired
	public KubernetesTaskLauncher(KubernetesDeployerProperties deployerProperties,
			KubernetesTaskLauncherProperties taskLauncherProperties, KubernetesClient client) {
		this(deployerProperties, taskLauncherProperties, client, new DefaultContainerFactory(deployerProperties));
	}

	@Autowired
	public KubernetesTaskLauncher(KubernetesDeployerProperties kubernetesDeployerProperties,
			KubernetesTaskLauncherProperties taskLauncherProperties,
	                             KubernetesClient client, ContainerFactory containerFactory) {
		this.properties = kubernetesDeployerProperties;
		this.taskLauncherProperties = taskLauncherProperties;
		this.client = client;
		this.containerFactory = containerFactory;
		this.deploymentPropertiesResolver = new DeploymentPropertiesResolver(
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX, properties);
	}

	@Override
	public String launch(AppDeploymentRequest request) {
		String appId = createDeploymentId(request);
		TaskStatus status = status(appId);

		if (!status.getState().equals(LaunchState.unknown)) {
			throw new IllegalStateException("Task " + appId + " already exists with a state of " + status);
		}

		if (this.maxConcurrentExecutionsReached()) {
			throw new IllegalStateException(
				String.format("Cannot launch task %s. The maximum concurrent task executions is at its limit [%d].",
					request.getDefinition().getName(), this.getMaximumConcurrentTasks())
			);
		}

		logPossibleDownloadResourceMessage(request.getResource());
		try {
			launch(appId, request);
			return appId;
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void cancel(String id) {
		logger.debug(String.format("Cancelling task: %s", id));
		//ToDo: what does cancel mean? Kubernetes doesn't have stop - just cleanup
		cleanup(id);
	}

	@Override
	public void cleanup(String id) {
		try {
			if (properties.isCreateJob()) {
				deleteJob(id);
			} else {
				deletePod(id);
			}
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void destroy(String appName) {
		for (String id : getIdsForTasks(Optional.of(appName), properties.isCreateJob())) {
			cleanup(id);
		}
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(TaskLauncher.class, this.getClass());
	}

	@Override
	public TaskStatus status(String id) {
		TaskStatus status = buildTaskStatus(id);
		logger.debug(String.format("Status for task: %s is %s", id, status));

		return status;
	}

	@Override
	public int getMaximumConcurrentTasks() {
		return this.properties.getMaximumConcurrentTasks();
	}

	@Override
	public int getRunningTaskExecutionCount() {
		List<String> taskIds = getIdsForTasks(Optional.empty(), false);
		AtomicInteger executionCount = new AtomicInteger();

		taskIds.forEach(id-> {
			if (buildPodStatus(id).getState() == LaunchState.running) {
				executionCount.incrementAndGet();
			}
		});

		return executionCount.get();
	}

	@Override
	public String getLog(String id) {
		if(properties.isCreateJob()){
			Job job = getJob(id);
			Map<String, String> selector = new HashMap<>();
			selector.put(SPRING_APP_KEY, id);
			selector.put("job-name", job.getMetadata().getName());
			PodList podList = client.pods().withLabels(selector).list();
			StringBuilder logAppender = new StringBuilder();
			for (Pod pod : podList.getItems()) {
				for (Container container : pod.getSpec().getContainers()) {
					logAppender.append(this.client.pods().withName(pod.getMetadata().getName())
							.inContainer(container.getName()).tailingLines(500).getLog());
				}
			}
			return logAppender.toString();
		} else {
			Map<String, String> selector = new HashMap<>();
			selector.put(SPRING_APP_KEY, id);
			PodList podList = client.pods().withLabels(selector).list();
			StringBuilder logAppender = new StringBuilder();
			for (Pod pod : podList.getItems()) {
				for (Container container : pod.getSpec().getContainers()) {
					logAppender.append(this.client.pods().withName(pod.getMetadata().getName())
							.inContainer(container.getName()).tailingLines(500).getLog());
				}
			}
			return logAppender.toString();
		}
	}

	private boolean maxConcurrentExecutionsReached() {
		return this.getRunningTaskExecutionCount() >= this.getMaximumConcurrentTasks();
	}

	protected String createDeploymentId(AppDeploymentRequest request) {
		String name = request.getDefinition().getName();
		Hashids hashids = new Hashids(name, 0, "abcdefghijklmnopqrstuvwxyz1234567890");
		String hashid = hashids.encode(System.currentTimeMillis());
		String deploymentId = name + "-" + hashid;
		// Kubernetes does not allow . in the name and does not allow uppercase in the name
		return deploymentId.replace('.', '-').toLowerCase();
	}


	private void launch(String appId, AppDeploymentRequest request) {
		Map<String, String> idMap = createIdMap(appId, request);
		Map<String, String> podLabelMap = new HashMap<>();
		podLabelMap.put("task-name", request.getDefinition().getName());
		podLabelMap.put(SPRING_MARKER_KEY, SPRING_MARKER_VALUE);

		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		Map<String, String> deploymentLabels = this.deploymentPropertiesResolver.getDeploymentLabels(deploymentProperties);
		if (!CollectionUtils.isEmpty(deploymentLabels)) {
			logger.debug(String.format("Adding deploymentLabels: %s", deploymentLabels));
		}
		PodSpec podSpec = createPodSpec(request);

		podSpec.setRestartPolicy(getRestartPolicy(request).name());
		if (this.properties.isCreateJob()) {
			logger.debug(String.format("Launching Job for task: %s", appId));
			ObjectMeta objectMeta = new ObjectMetaBuilder()
					.withLabels(podLabelMap)
					.addToLabels(idMap)
					.addToLabels(deploymentLabels)
					.withAnnotations(this.deploymentPropertiesResolver.getJobAnnotations(deploymentProperties))
					.addToAnnotations(this.deploymentPropertiesResolver.getPodAnnotations(deploymentProperties))
					.build();
			PodTemplateSpec podTemplateSpec = new PodTemplateSpec(objectMeta, podSpec);

			JobSpec jobSpec = new JobSpecBuilder()
					.withTemplate(podTemplateSpec)
					.withBackoffLimit(getBackoffLimit(request))
					.withTtlSecondsAfterFinished(getTtlSecondsAfterFinished(request))
					.build();

			this.client.batch().v1().jobs().create(
				new JobBuilder()
						.withNewMetadata()
						.withName(appId)
						.withLabels(Collections.singletonMap("task-name", podLabelMap.get("task-name")))
						.addToLabels(idMap)
						.withAnnotations(this.deploymentPropertiesResolver.getJobAnnotations(deploymentProperties))
						.endMetadata()
						.withSpec(jobSpec)
						.build()
			);
		}
		else {
			logger.debug(String.format("Launching Pod for task: %s", appId));

			this.client.pods().create(
					new PodBuilder()
							.withNewMetadata()
							.withName(appId)
							.withLabels(podLabelMap)
							.addToLabels(deploymentLabels)
							.withAnnotations(this.deploymentPropertiesResolver.getJobAnnotations(deploymentProperties))
							.addToAnnotations(this.deploymentPropertiesResolver.getPodAnnotations(deploymentProperties))
							.addToLabels(idMap)
							.endMetadata()
							.withSpec(podSpec)
							.build()
			);
		}
	}

	private List<String> getIdsForTasks(Optional<String> taskName, boolean isCreateJob) {
		List<String> ids = new ArrayList<>();
		try {
			KubernetesResourceList<?> resourceList = getTaskResources(taskName, isCreateJob);

			for (HasMetadata hasMetadata : resourceList.getItems()) {
				ids.add(hasMetadata.getMetadata().getName());
			}
		}
		catch (KubernetesClientException kce) {
			logger.warn(String.format("Failed to retrieve pods for task: %s", taskName), kce);
		}

		return ids;
	}

	private KubernetesResourceList<?>  getTaskResources(Optional<String> taskName, boolean isCreateJob) {
		KubernetesResourceList<?> resourceList;
		if (taskName.isPresent()) {
			if (isCreateJob) {
				resourceList = client.batch().jobs().withLabel("task-name", taskName.get()).list();
			}
			else {
				resourceList = client.pods().withLabel("task-name", taskName.get()).list();
			}
		} else {
			if (isCreateJob) {
				resourceList = client.batch().jobs().withLabel("task-name").list();
			}
			else {
				resourceList = client.pods().withLabel("task-name").list();
			}
		}
		return resourceList;
	}

	TaskStatus buildTaskStatus(String id) {

		if(properties.isCreateJob()){
			Job job = getJob(id);

			if (job == null) {
				return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
			}

			JobStatus jobStatus = job.getStatus();

			if (jobStatus == null) {
				return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
			}

			boolean failed = jobStatus.getFailed() != null && jobStatus.getFailed() > 0;
			boolean succeeded = jobStatus.getSucceeded() != null && jobStatus.getSucceeded() > 0;
			if (failed) {
				return new TaskStatus(id, LaunchState.failed, new HashMap<>());
			}
			if (succeeded) {
				return new TaskStatus(id, LaunchState.complete, new HashMap<>());
			}
			return new TaskStatus(id, LaunchState.launching, new HashMap<>());

		} else {
			return buildPodStatus(id);
		}
	}

	private TaskStatus buildPodStatus(String id) {
		Pod pod = getPodByName(id);
		if (pod == null) {
			return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
		}

		PodStatus podStatus = pod.getStatus();
		if (podStatus == null) {
			return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
		}

		String phase = podStatus.getPhase();

		switch (phase) {
		case "Pending":
			return new TaskStatus(id, LaunchState.launching, new HashMap<>());
		case "Failed":
			return new TaskStatus(id, LaunchState.failed, new HashMap<>());
		case "Succeeded":
			return new TaskStatus(id, LaunchState.complete, new HashMap<>());
		default:
			return new TaskStatus(id, LaunchState.running, new HashMap<>());
		}
	}


	private void deleteJob(String id) {
		FilterWatchListDeletable<Job, JobList> jobsToDelete = client.batch().jobs()
				.withLabel(SPRING_APP_KEY, id);

		if (jobsToDelete == null || ObjectUtils.isEmpty(jobsToDelete.list().getItems())) {
			logger.warn(String.format("Cannot delete job for task \"%s\" (reason: job does not exist)", id));
		}

		logger.debug(String.format("Deleting job for task: %s", id));
		boolean deleted = jobsToDelete.delete();
		logger.debug(String.format("Job was%s deleted for task: %s", id, (deleted ? "" : " not")));
	}

	private void deletePod(String id) {
		FilterWatchListDeletable<Pod, PodList> podsToDelete = client.pods()
				.withLabel(SPRING_APP_KEY, id);

		if (podsToDelete == null || ObjectUtils.isEmpty(podsToDelete.list().getItems())) {
			logger.warn(String.format("Cannot delete pod for task \"%s\" (reason: pod does not exist)", id));
		}

		logger.debug(String.format("Deleting pod for task: %s", id));
		boolean deleted = podsToDelete.delete();
		logger.debug(String.format("Pod was%s deleted for task: %s", id, (deleted ? "" : " not")));
	}

	private Job getJob(String jobName) {
		List<Job> jobs = client.batch().jobs().withLabel(SPRING_APP_KEY, jobName).list().getItems();

		for (Job job : jobs) {
			if (jobName.equals(job.getMetadata().getName())) {
				return job;
			}
		}

		return null;
	}

	private Pod getPodByName(String name) {
		PodResource podResource = client.pods().withName(name);
		return podResource == null? null: client.pods().withName(name).get();
	}

	/**
	 * Get the RestartPolicy setting for the deployment request.
	 *
	 * @param request The deployment request.
	 * @return Whether RestartPolicy is requested
	 */
	protected RestartPolicy getRestartPolicy(AppDeploymentRequest request) {
		String restartPolicyString =
				PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
						"spring.cloud.deployer.kubernetes.restartPolicy");
		RestartPolicy restartPolicy =  (!StringUtils.hasText(restartPolicyString)) ? this.taskLauncherProperties.getRestartPolicy() :
				RestartPolicy.valueOf(restartPolicyString);
		if (this.properties.isCreateJob()) {
			Assert.isTrue(!restartPolicy.equals(RestartPolicy.Always), "RestartPolicy should not be 'Always' when the JobSpec is used.");
		}
		return restartPolicy;
	}

	/**
	 * Get the BackoffLimit setting for the deployment request.
	 *
	 * @param request The deployment request.
	 * @return the backoffLimit
	 */
	protected Integer getBackoffLimit(AppDeploymentRequest request) {
		String backoffLimitString = PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
				"spring.cloud.deployer.kubernetes.backoffLimit");
		if (StringUtils.hasText(backoffLimitString)) {
			return Integer.valueOf(backoffLimitString);
		}
		else {
			return this.taskLauncherProperties.getBackoffLimit();
		}
	}

	/**
	 * Get the ttlSecondsAfterFinihsed setting for the deployment request.
	 *
	 * @param request The deployment request.
	 * @return the ttlSecondsAfterFinished
	 */
	protected Integer getTtlSecondsAfterFinished(AppDeploymentRequest request) {
		String ttlSecondsAfterFinished = PropertyParserUtils.getDeploymentPropertyValue(request.getDeploymentProperties(),
				"spring.cloud.deployer.kubernetes.ttlSecondsAfterFinished");
		if (StringUtils.hasText(ttlSecondsAfterFinished)) {
			return Integer.valueOf(ttlSecondsAfterFinished);
		}
		else {
			return this.taskLauncherProperties.getTtlSecondsAfterFinished();
		}
	}
}
