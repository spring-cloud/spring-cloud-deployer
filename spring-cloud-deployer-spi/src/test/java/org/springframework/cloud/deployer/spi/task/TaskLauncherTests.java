/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.cloud.deployer.spi.task;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link TaskLauncher}
 */
public class TaskLauncherTests {

	@Test
	public void testTaskLauncherDefaultMethods() {

		TaskLauncher taskLauncher = new TaskLauncher() {
			@Override
			public String launch(AppDeploymentRequest request) {
				return null;
			}

			@Override
			public void cancel(String id) {

			}

			@Override
			public TaskStatus status(String id) {
				return null;
			}

			@Override
			public void cleanup(String id) {

			}

			@Override
			public void destroy(String appName) {

			}

			@Override
			public RuntimeEnvironmentInfo environmentInfo() {
				return null;
			}
		};
		try {
			taskLauncher.getLog("test");
			fail();
		}
		catch (UnsupportedOperationException e) {
			assertEquals(e.getMessage(), "'getLog' is not implemented.");
		}
		try {
			taskLauncher.getRunningTaskExecutionCount();
			fail();
		}
		catch (UnsupportedOperationException e) {
			assertEquals(e.getMessage(), "'getRunningTaskExecutionCount' is not implemented.");
		}
		try {
			taskLauncher.getMaximumConcurrentTasks();
			fail();
		}
		catch (UnsupportedOperationException e) {
			assertEquals(e.getMessage(), "'getMaximumConcurrentTasks' is not implemented.");
		}
	}
}
