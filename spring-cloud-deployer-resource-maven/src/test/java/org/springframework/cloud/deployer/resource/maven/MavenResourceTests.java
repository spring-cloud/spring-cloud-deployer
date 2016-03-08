/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.resource.maven;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * 
 * @author Venil Noronha
 */
public class MavenResourceTests {

	@Test
	public void testMavenResourceFilename() {
		MavenResource resource = new MavenResource.Builder()
				.setArtifactId("timestamp-task")
				.setGroupId("org.springframework.cloud.task.module")
				.setVersion("1.0.0.BUILD-SNAPSHOT")
				.setExtension("jar")
				.setClassifier("exec")
				.build();
		assertNotNull("getFilename() returned null", resource.getFilename());
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename());
	}
	
}
