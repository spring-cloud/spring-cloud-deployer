/*
 * Copyright 2017 the original author or authors.
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

import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.deployer.spi.app.DeploymentState;

/**
 * @author David Turanski
 **/
public class PredicateRunningPhaseDeploymentStateResolver implements RunningPhaseDeploymentStateResolver {
	private static Log logger = LogFactory.getLog(PredicateRunningPhaseDeploymentStateResolver.class);
	private final ContainerStatusCondition[] conditions;
	private final DeploymentState resolvedState;
	protected final KubernetesDeployerProperties properties;

	PredicateRunningPhaseDeploymentStateResolver(
		KubernetesDeployerProperties properties,
		DeploymentState resolvedState,
		ContainerStatusCondition... conditions) {
		this.conditions = conditions;
		this.resolvedState = resolvedState;
		this.properties = properties;
	}

	public DeploymentState resolve(ContainerStatus containerStatus) {

		Stream<Predicate<ContainerStatus>> conditionsStream = Stream.of(conditions);
		Boolean allConditionsMet = conditionsStream.reduce((x, y) -> x.and(y)).get().test(containerStatus);

		if (allConditionsMet) {
			logger.debug("deployment state is " + resolvedState.name());
			return this.resolvedState;
		}
		else {
			Stream<ContainerStatusCondition> report = Stream.of(conditions);
			report.filter(c -> {
				boolean result= false;
				try {
					result = c.test(containerStatus);
				}
				catch (NullPointerException e) {

				}
				return !result;

			}).forEach(c -> logger.debug(c + " is not satisfied"));

		}
		return null;
	}

	static abstract class ContainerStatusCondition implements Predicate<ContainerStatus> {
		private final String description;

		ContainerStatusCondition(String description) {
			this.description = description;
		}
		public String toString() {
			String className = this.getClass().getName();

			return className.substring(className.lastIndexOf(".") + 1) + ":" + description;
		}
	}

	static class ContainerReady extends PredicateRunningPhaseDeploymentStateResolver {
		ContainerReady(KubernetesDeployerProperties properties) {
			super(properties, DeploymentState.deployed, new ContainerStatusCondition("container ready") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getReady();
				}
			});
		}
	}

	static class ContainerCrashed extends PredicateRunningPhaseDeploymentStateResolver {
		ContainerCrashed(KubernetesDeployerProperties properties) {
			super(properties,
				DeploymentState.failed,
				new ContainerStatusCondition("restart count > maxTerminatedErrorRestarts") {
					@Override
					public boolean test(ContainerStatus containerStatus) {
						return containerStatus.getRestartCount() > properties.getMaxTerminatedErrorRestarts();
					}
				}, new ContainerStatusCondition("exit code in (1, 137, 143)") {
					@Override
					public boolean test(ContainerStatus containerStatus) {
						// if we are being killed repeatedly due to OOM or using too much CPU, or abnormal termination.
						return
							containerStatus.getLastState() != null &&
								containerStatus.getLastState().getTerminated() != null &&
								(containerStatus.getLastState().getTerminated().getExitCode() == 137 ||
									containerStatus.getLastState().getTerminated().getExitCode() == 143 ||
									containerStatus.getLastState().getTerminated().getExitCode() == 1);
					}
				});
		}
	}

	// if we are being restarted repeatedly due to the same error, consider the app crashed
	static class RestartsDueToTheSameError extends PredicateRunningPhaseDeploymentStateResolver {

		RestartsDueToTheSameError(KubernetesDeployerProperties properties) {
			super(properties, DeploymentState.failed, new ContainerStatusCondition("restart count > "
				+ "maxTerminatedErrorRestarts") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getRestartCount() > properties.getMaxTerminatedErrorRestarts();
				}
			}, new ContainerStatusCondition("last state termination reason == 'Error' and termination reason == "
				+ "'Error'") {
				public boolean test(ContainerStatus containerStatus) {
					return
						containerStatus.getLastState() != null && containerStatus.getState() != null &&
							containerStatus.getLastState().getTerminated() != null &&
							containerStatus.getLastState().getTerminated().getReason() != null &&
							containerStatus.getLastState().getTerminated().getReason().contains("Error") &&
							containerStatus.getState().getTerminated() != null &&
							containerStatus.getState().getTerminated().getReason() != null &&
							containerStatus.getState().getTerminated().getReason().contains("Error");
				}
			}, new ContainerStatusCondition("last state exit code == exit code") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getLastState().getTerminated().getExitCode().equals(
						containerStatus.getState().getTerminated().getExitCode());
				}
			});
		}
	}

	static class CrashLoopBackOffRestarts extends PredicateRunningPhaseDeploymentStateResolver {
		CrashLoopBackOffRestarts(KubernetesDeployerProperties properties) {
			super(properties, DeploymentState.failed, new ContainerStatusCondition("restart count > "
				+ "CrashLoopBackOffRestarts") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getRestartCount() > properties.getMaxCrashLoopBackOffRestarts();
				}
			}, new ContainerStatusCondition("waiting in CrashLoopBackOff") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return
						containerStatus.getLastState() != null &&
							containerStatus.getState() != null &&
							containerStatus.getLastState().getTerminated() != null &&
							containerStatus.getState().getWaiting() != null &&
							containerStatus.getState().getWaiting().getReason() != null &&
							containerStatus.getState().getWaiting().getReason().contains("CrashLoopBackOff");
				}
			});
		}
	}

	static class ContainerTerminated extends PredicateRunningPhaseDeploymentStateResolver {

		ContainerTerminated(KubernetesDeployerProperties properties) {
			super(properties, DeploymentState.undeployed, new ContainerStatusCondition("restart count == 0") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getRestartCount() == 0;
				}
			}, new ContainerStatusCondition("state is terminated") {
				@Override
				public boolean test(ContainerStatus containerStatus) {
					return containerStatus.getState() != null &&
						containerStatus.getState().getTerminated() != null;
				}
			});
		}
	}
}

