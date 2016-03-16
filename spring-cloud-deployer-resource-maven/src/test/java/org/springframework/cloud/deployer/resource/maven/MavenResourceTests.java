/*
 * Copyright 2016 the original author or authors.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * @author Venil Noronha
 * @author Janne Valkealahti
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

	@Test
	public void testExists() {
		MavenResource resource = new MavenResource.Builder()
				.setArtifactId("timestamp-task")
				.setGroupId("org.springframework.cloud.task.module")
				.setVersion("1.0.0.BUILD-SNAPSHOT")
				.setExtension("jar")
				.setClassifier("exec")
				.build();
		assertEquals(resource.exists(), true);
		resource = new MavenResource.Builder()
				.setArtifactId("notexist")
				.setGroupId("org.springframework.cloud.task.module")
				.setVersion("1.0.0.BUILD-SNAPSHOT")
				.setExtension("jar")
				.setClassifier("exec")
				.build();
		assertEquals(resource.exists(), false);
	}

	@Test
	public void testParseCoordinates() {
		MavenResource resource = MavenResource.parse("org.springframework.cloud.task.module:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename());
		resource = MavenResource.parse("org.springframework.cloud.task.module:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename());
	}

	@Test
	public void testMavenResourceFromURI() throws Exception {
		MavenResource resource = MavenResource.parseUri("http://repo.spring.io/libs-snapshot-local/org.springframework.cloud.task.module:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename());
		MavenResource.parseUri("maven:org.springframework.cloud.task.module:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename());
	}

	@Test
	public void testGetFileShouldDownloadIfNotCached() throws Exception {
		MavenResource resource = MavenResource.parseUri("maven:http://repo.spring.io/libs-snapshot-local/org.springframework.cloud.task.module:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		resource.getFile();

		// no remotes should not fail anymore
		resource = new MavenResource.Builder()
				.setArtifactId("timestamp-task")
				.setGroupId("org.springframework.cloud.task.module")
				.setVersion("1.0.0.BUILD-SNAPSHOT")
				.setExtension("jar")
				.setClassifier("exec")
				.build();
		resource.getFile();
	}

}
