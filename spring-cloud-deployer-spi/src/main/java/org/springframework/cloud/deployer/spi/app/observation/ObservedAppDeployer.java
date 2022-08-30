/*
 * Copyright 2018-2022 the original author or authors.
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

package org.springframework.cloud.deployer.spi.app.observation;

import java.time.Duration;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;

/**
 * Observed representation of an {@link AppDeployer}.
 *
 * @author Marcin Grzejszczak
 */
public class ObservedAppDeployer implements AppDeployer {

	private static final LogAccessor log = new LogAccessor(ObservedAppDeployer.class);

	private static final DefaultAppDeployerObservationConvention DEFAULT_CONVENTION = new DefaultAppDeployerObservationConvention();
	private final AppDeployer delegate;

	private final ObservationRegistry observationRegistry;

	@Nullable
	private final AppDeployerObservationConvention customConvention;

	private final Duration pollDelay;

	/**
	 * Creates a new instance of {@link ObservedAppDeployer}.
	 *
	 * @param observationRegistry observation registry
	 * @param customConvention custom convention
	 * @param delegate application deployer delegate
	 * @param pollDelay duration for polling for the status of the application being deployed
	 */
	public ObservedAppDeployer(ObservationRegistry observationRegistry, @Nullable AppDeployerObservationConvention customConvention, AppDeployer delegate, Duration pollDelay) {
		this.observationRegistry = observationRegistry;
		this.delegate = delegate;
		this.customConvention = customConvention;
		this.pollDelay = pollDelay;
	}

	/**
	 * Creates a new instance of {@link ObservedAppDeployer} with a default {@link AppDeployerObservationConvention} implementation.
	 *
	 * @param observationRegistry observation registry
	 * @param delegate application deployer delegate
	 * @param pollDelay duration for polling for the status of the application being deployed
	 */
	public ObservedAppDeployer(ObservationRegistry observationRegistry, AppDeployer delegate, Duration pollDelay) {
		this(observationRegistry, null, delegate, pollDelay);
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setDeploymentRequest(request);
		Observation observation = AppDeployerDocumentedObservation.DEPLOY_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry);
		return observation.scoped(() -> {
			observation.event(AppDeployerDocumentedObservation.Events.DEPLOYER_START);
			String id = this.delegate.deploy(request);
			context.setAppId(id);
			registerListener(observation, id);
			return id;
		});
	}

	private void registerListener(Observation observation, String id) {
		PreviousAndCurrentStatus previousAndCurrentStatus = new PreviousAndCurrentStatus(observation);
		// @formatter:off
			this.delegate.statusReactive(id)
					.map(previousAndCurrentStatus::updateCurrent)
					.repeatWhen(repeat -> repeat.flatMap(i -> Mono.delay(this.pollDelay)))
					.takeUntil(PreviousAndCurrentStatus::isFinished)
					.last()
					.doOnNext(PreviousAndCurrentStatus::eventOnObservation)
					.doOnError(observation::error)
					.doFinally(signalType -> observation.stop()).subscribe();
			// @formatter:on
	}

	@Override
	public void undeploy(String id) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setAppId(id);
		Observation observation = AppDeployerDocumentedObservation.UNDEPLOY_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry);
		observation.scoped(() -> {
			observation.event(AppDeployerDocumentedObservation.Events.DEPLOYER_START);
			this.delegate.undeploy(id);
			registerListener(observation, id);
			return id;
		});
	}

	@Override
	public AppStatus status(String id) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setAppId(id);
		Observation observation = AppDeployerDocumentedObservation.STATUS_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry);
		return observation.observe(() -> this.delegate.status(id));
	}

	@Override
	public Mono<AppStatus> statusReactive(String id) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setAppId(id);
		return Mono.defer(() -> Mono.just(AppDeployerDocumentedObservation.STATUS_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry)))
				.flatMap(observation -> this.delegate.statusReactive(id).doOnNext(appStatus -> observation.stop()));
	}

	@Override
	public Flux<AppStatus> statusesReactive(String... ids) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setAppId(String.join(",", ids));
		return Flux.defer(() -> Flux.just(AppDeployerDocumentedObservation.STATUS_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry)))
				.flatMap(observation -> this.delegate.statusesReactive(ids).doOnNext(appStatus -> observation.stop()));
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return this.delegate.environmentInfo();
	}

	@Override
	public String getLog(String id) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setAppId(id);
		Observation observation = AppDeployerDocumentedObservation.GET_LOG_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry);
		return observation.observe(() -> this.delegate.getLog(id));
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		AppDeployerContext context = new AppDeployerContext(environmentInfo());
		context.setScaleRequest(appScaleRequest);
		Observation observation = AppDeployerDocumentedObservation.SCALE_OBSERVATION.start(this.customConvention, DEFAULT_CONVENTION, context, this.observationRegistry);
		observation.observe(() -> this.delegate.scale(appScaleRequest));
	}

	private static final class PreviousAndCurrentStatus {

		private final Observation observation;

		private AppStatus current;

		private AppStatus previous;

		private PreviousAndCurrentStatus(Observation observation) {
			this.observation = observation;
			if (log.isDebugEnabled()) {
				log.debug("Current observation is [" + observation + "]");
			}
		}

		private PreviousAndCurrentStatus updateCurrent(AppStatus current) {
			if (log.isTraceEnabled()) {
				log.trace("State before change: current [" + this.current + "], previous [" + this.previous + "]");
			}
			this.previous = this.current;
			this.current = current;
			if (log.isTraceEnabled()) {
				log.trace("State after change: current [" + this.current + "], previous [" + this.previous + "]");
			}
			if (statusChanged()) {
				eventOnObservation();
			}
			else if (log.isTraceEnabled()) {
				log.trace("State has not changed, will not annotate the observation");
			}
			return this;
		}

		private void eventOnObservation() {
			String name = this.current.getState().name();
			if (log.isDebugEnabled()) {
				log.debug("Will annotate its state with [" + name + "]");
			}
			this.observation.event(AppDeployerDocumentedObservation.Events.DEPLOYER_STATUS_CHANGE.format(name));
		}

		private boolean statusChanged() {
			if (this.previous == null && this.current != null) {
				if (log.isDebugEnabled()) {
					log.debug("Previous is null, current is not null");
				}
				return true;
			}
			else if (this.current == null) {
				throw new IllegalStateException("Current state can't be null");
			}
			DeploymentState currentState = this.current.getState();
			DeploymentState previousState = this.previous.getState();
			return currentState != previousState;
		}

		private boolean isFinished() {
			boolean finished = !(this.current.getState() == DeploymentState.deploying || this.current.getState() == DeploymentState.partial);
			if (log.isTraceEnabled()) {
				log.trace("Status is finished [" + finished + "]");
			}
			return finished;
		}

	}

}
