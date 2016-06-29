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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for {@link MavenResource}
 *
 * @author Venil Noronha
 * @author Janne Valkealahti
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 */
public class MavenResourceTests {

	@Test
	public void mavenResourceFilename() {
		MavenResource resource = new MavenResource.Builder()
				.artifactId("timestamp-task")
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		assertNotNull("getFilename() returned null", resource.getFilename());
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename());
	}

	@Test
	public void resourceExists() {
		MavenProperties mavenProperties = new MavenProperties();
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default", new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot-local"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse("org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT", mavenProperties);
		assertEquals(resource.exists(), true);
	}

	@Test
	public void resourceDoesNotExist() {
		MavenProperties mavenProperties = new MavenProperties();
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default", new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot-local"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse("org.springframework.cloud.task.app:doesnotexist:jar:1.0.0.BUILD-SNAPSHOT", mavenProperties);
		assertEquals(resource.exists(), false);
	}

	@Test
	public void coordinatesParsed() {
		MavenResource resource = MavenResource.parse("org.springframework.cloud.task.app:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename());
		resource = MavenResource.parse("org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT");
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename());
	}

	@Test
	public void mavenResourceRetrievedFromNonDefaultRemoteRepository() throws Exception {
		String coordinates = "org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT";
		MavenProperties properties = new MavenProperties();
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test1";
		new File(tempLocalRepo).deleteOnExit();
		properties.setLocalRepository(tempLocalRepo);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default", new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot-local"));
		properties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse(coordinates, properties);
		assertEquals("getFilename() doesn't match the expected filename",
				"timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename());
	}

	@Test(expected = IllegalStateException.class)
	public void localResolutionFailsIfNotCached() throws Exception {
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test2";
		new File(tempLocalRepo).deleteOnExit();
		MavenProperties properties = new MavenProperties();
		properties.setLocalRepository(tempLocalRepo);
		properties.setOffline(true);
		MavenResource resource = new MavenResource.Builder(properties)
				.artifactId("timestamp-task")
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		resource.getFile();
	}

	@Test
	public void localResolutionSucceedsIfCached() throws Exception {
		String coordinates = "org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT";
		MavenProperties properties1 = new MavenProperties();
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test3";
		new File(tempLocalRepo).deleteOnExit();
		properties1.setLocalRepository(tempLocalRepo);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default", new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot-local"));
		properties1.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse(coordinates, properties1);
		resource.getFile();

		// no remotes; should not fail anymore
		MavenProperties properties2 = new MavenProperties();
		properties2.setLocalRepository(tempLocalRepo);
		properties2.setOffline(true);
		resource = new MavenResource.Builder(properties2)
				.artifactId("timestamp-task")
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		resource.getFile();
	}

}
