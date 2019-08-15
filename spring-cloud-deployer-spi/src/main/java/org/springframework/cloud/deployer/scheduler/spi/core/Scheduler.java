/*
 * Copyright 2018-2019 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.springframework.cloud.deployer.scheduler.spi.core;

import java.util.List;

import org.springframework.core.io.Resource;

/**
 * A {@code Scheduler} is a component that provides a way to register the execution of a
 * {@link ScheduleRequest} with an underlying scheduler system (Quartz, etc).
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 *
 */
public interface Scheduler {

	/**
	 * Registers the {@link ScheduleRequest} to be executed based on the
	 * cron expression provided.  If an error occurs during schedule creation
	 * then a {@link CreateScheduleException} should be thrown.
	 *
	 * @param scheduleRequest A request representing a sched-uable
	 * artifact({@link org.springframework.cloud.deployer.spi.core.AppDefinition},
	 * the {@link Resource}), schedule properties, and deployment properties.
	 */
	void schedule(ScheduleRequest scheduleRequest);

	/**
	 *  Deletes a schedule that has been created.  If an error occurs during
	 *  un-scheduling then a {@link UnScheduleException} should be thrown.
	 *
	 * @param scheduleName the name of the schedule to be removed.
	 */
	void unschedule(String scheduleName);

	/**
	 * List all of the Schedules associated with the provided AppDefinition.
	 * If an error occurs during list generation then a {@link SchedulerException}
	 * should be thrown.
	 *
	 * @param taskDefinitionName to retrieve {@link ScheduleInfo}s for a specified taskDefinitionName.
	 * @return A List of {@link ScheduleInfo}s configured for the provided taskDefinitionName.
	 */
	List<ScheduleInfo> list(String taskDefinitionName) ;

	/**
	 * List all of the {@link ScheduleInfo}s registered with the system.
	 * If an error occurs during list generation then a {@link SchedulerException}
	 * should be thrown.
	 *
	 * @return A List of {@link ScheduleInfo}s for the given system.
	 */
	List<ScheduleInfo> list();
}

