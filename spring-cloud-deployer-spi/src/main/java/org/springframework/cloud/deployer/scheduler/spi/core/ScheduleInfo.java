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

import java.util.Map;

/**
 * A {@code Schedule} represents the association between the task definition and the
 * times it is to be executed. The application, in this case, is represented by
 * the taskDefinitionName. The schedulerProperties, contain the information to calculate
 * when the next time the application should run (cron expression, etc)".
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
public class ScheduleInfo {
	/**
	 * The name to be associated with the Schedule instance.
	 */
	private String scheduleName;

	/**
	 * The task definition name associated with Schedule instance.
	 */
	private String taskDefinitionName;

	/**
	 * Schedule specific information returned from the Scheduler implementation
	 */
	private Map<String, String> scheduleProperties;


	public String getScheduleName() {
		return scheduleName;
	}

	public void setScheduleName(String scheduleName) {
		this.scheduleName = scheduleName;
	}

	public String getTaskDefinitionName() {
		return taskDefinitionName;
	}

	public void setTaskDefinitionName(String taskDefinitionName) {
		this.taskDefinitionName = taskDefinitionName;
	}

	public Map<String, String> getScheduleProperties() {
		return scheduleProperties;
	}

	public void setScheduleProperties(Map<String, String> scheduleProperties) {
		this.scheduleProperties = scheduleProperties;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ScheduleInfo)) return false;

		ScheduleInfo that = (ScheduleInfo) o;

		if (scheduleName != null ? !scheduleName.equals(that.scheduleName) : that.scheduleName != null)
			return false;

		return true;
	}

	@Override
	public String toString() {
		return "ScheduleInfo{" +
				"scheduleName='" + scheduleName + '\'' +
				", taskDefinitionName='" + taskDefinitionName + '\'' +
				", scheduleProperties=" + scheduleProperties +
				'}';
	}
}
