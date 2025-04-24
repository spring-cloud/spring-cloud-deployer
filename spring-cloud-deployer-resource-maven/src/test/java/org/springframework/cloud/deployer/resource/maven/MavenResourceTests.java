/*
 * Copyright 2016-2021 the original author or authors.
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

package org.springframework.cloud.deployer.resource.maven;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.jupiter.api.Test;

import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	public void mavenResourceFilename() throws IOException {
		MavenResource resource = new MavenResource.Builder()
				.artifactId("timestamp-task")
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		assertNotNull(resource.getFilename(), "getFilename() returned null");
		assertEquals("timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename(), "getFilename() doesn't match the expected filename");
		assertEquals("maven://org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT",
				resource.getURI().toString(),
				"getURI doesn't match the expected URI");
	}

	@Test
	public void resourceExists() {
		MavenProperties mavenProperties = new MavenProperties();
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource
				.parse("org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT", mavenProperties);
		assertEquals(resource.exists(), true);
	}

	@Test
	public void resourceDoesNotExist() {
		MavenProperties mavenProperties = new MavenProperties();
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource
				.parse("org.springframework.cloud.task.app:doesnotexist:jar:1.0.0.BUILD-SNAPSHOT", mavenProperties);
		assertEquals(resource.exists(), false);
	}

	@Test
	public void coordinatesParsed() {
		MavenResource resource = MavenResource
				.parse("org.springframework.cloud.task.app:timestamp-task:jar:exec:1.0.0.BUILD-SNAPSHOT");
		assertEquals("timestamp-task-1.0.0.BUILD-SNAPSHOT-exec.jar", resource.getFilename(), "getFilename() doesn't match the expected filename");
		resource = MavenResource.parse("org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT");
		assertEquals("timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename(), "getFilename() doesn't match the expected filename");
	}

	@Test
	public void mavenResourceRetrievedFromNonDefaultRemoteRepository() throws Exception {
		String coordinates = "org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT";
		MavenProperties properties = new MavenProperties();
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test1";
		new File(tempLocalRepo).deleteOnExit();
		properties.setLocalRepository(tempLocalRepo);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot"));
		properties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse(coordinates, properties);
		assertEquals("timestamp-task-1.0.0.BUILD-SNAPSHOT.jar", resource.getFilename(), "getFilename() doesn't match the expected filename");
	}

	@Test
	public void localResolutionFailsIfNotCached() {
		assertThrows(IllegalStateException.class, () -> {
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
		});
	}

	@Test
	public void localResolutionSucceedsIfCached() throws Exception {
		String coordinates = "org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT";
		MavenProperties properties1 = new MavenProperties();
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test3";
		new File(tempLocalRepo).deleteOnExit();
		properties1.setLocalRepository(tempLocalRepo);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot"));
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

	@Test
	public void testGetVersions() throws Exception {
		String coordinates = "org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT";
		MavenProperties properties = new MavenProperties();
		String tempLocalRepo = System.getProperty("java.io.tmpdir") + File.separator + ".m2-test3";
		new File(tempLocalRepo).deleteOnExit();
		properties.setLocalRepository(tempLocalRepo);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("https://repo.spring.io/libs-snapshot"));
		properties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource.parse(coordinates, properties);
		Assert.isTrue(!resource.getVersions("org.springframework.cloud.task.app:timestamp-task:jar:[0,)").isEmpty(), "Versions shouldn't be empty");
	}

	@Test
	public void checkRepositoryPolicies() {
		MavenProperties mavenProperties = new MavenProperties();
		mavenProperties.setChecksumPolicy("always");
		mavenProperties.setUpdatePolicy("fail");
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		MavenProperties.RemoteRepository remoteRepo1 = new MavenProperties.RemoteRepository(
				"https://repo.spring.io/libs-snapshot");
		MavenProperties.RepositoryPolicy snapshotPolicy = new MavenProperties.RepositoryPolicy();
		snapshotPolicy.setEnabled(true);
		snapshotPolicy.setUpdatePolicy("always");
		snapshotPolicy.setChecksumPolicy("warn");
		remoteRepo1.setSnapshotPolicy(snapshotPolicy);
		MavenProperties.RepositoryPolicy releasePolicy = new MavenProperties.RepositoryPolicy();
		releasePolicy.setEnabled(true);
		releasePolicy.setUpdatePolicy("interval");
		releasePolicy.setChecksumPolicy("ignore");
		remoteRepo1.setReleasePolicy(releasePolicy);
		remoteRepositoryMap.put("repo1", remoteRepo1);
		MavenProperties.RemoteRepository remoteRepo2 = new MavenProperties.RemoteRepository(
				"https://repo.spring.io/libs-milestone");
		MavenProperties.RepositoryPolicy policy = new MavenProperties.RepositoryPolicy();
		policy.setEnabled(true);
		policy.setUpdatePolicy("daily");
		policy.setChecksumPolicy("fail");
		remoteRepo2.setPolicy(policy);
		remoteRepositoryMap.put("repo2", remoteRepo2);
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenArtifactResolver artifactResolver = new MavenArtifactResolver(mavenProperties);
		Field remoteRepositories = ReflectionUtils.findField(MavenArtifactResolver.class, "remoteRepositories");
		ReflectionUtils.makeAccessible(remoteRepositories);
		List<RemoteRepository> remoteRepositoryList = (List<RemoteRepository>) ReflectionUtils
				.getField(remoteRepositories, artifactResolver);
		Field repositorySystem = ReflectionUtils.findField(MavenArtifactResolver.class, "repositorySystem");
		ReflectionUtils.makeAccessible(repositorySystem);
		RepositorySystem repositorySystem1 = (RepositorySystem) ReflectionUtils.getField(repositorySystem, artifactResolver);
		Method repositorySystemSessionMethod = ReflectionUtils.findMethod(MavenArtifactResolver.class, "newRepositorySystemSession", RepositorySystem.class, String.class);
		ReflectionUtils.makeAccessible(repositorySystemSessionMethod);
		RepositorySystemSession repositorySystemSession = (RepositorySystemSession)
				ReflectionUtils.invokeMethod(repositorySystemSessionMethod, artifactResolver, repositorySystem1, "file://local");
		assertEquals("always", repositorySystemSession.getChecksumPolicy());
		assertEquals("fail", repositorySystemSession.getUpdatePolicy());
		for (RemoteRepository remoteRepository : remoteRepositoryList) {
			assertEquals(2, remoteRepositoryList.size());
			assertEquals(true, remoteRepositoryList.get(0).getId().equals("repo1")
					|| remoteRepositoryList.get(0).getId().equals("repo2"));
			assertEquals(true, remoteRepositoryList.get(1).getId().equals("repo2")
					|| remoteRepositoryList.get(1).getId().equals("repo1"));
			if (remoteRepository.getId().equals("repo1")) {
				RepositoryPolicy snapshotPolicy1 = remoteRepository.getPolicy(true);
				assertEquals(true, snapshotPolicy1.isEnabled());
				assertEquals("always", snapshotPolicy1.getUpdatePolicy());
				assertEquals("warn", snapshotPolicy1.getChecksumPolicy());
				RepositoryPolicy releasePolicy1 = remoteRepository.getPolicy(false);
				assertEquals(true, releasePolicy1.isEnabled());
				assertEquals("interval", releasePolicy1.getUpdatePolicy());
				assertEquals("ignore", releasePolicy1.getChecksumPolicy());
			}
			else if (remoteRepository.getId().equals("repo2")) {
				RepositoryPolicy snapshotPolicy2 = remoteRepository.getPolicy(true);
				assertEquals(true, snapshotPolicy2.isEnabled());
				assertEquals("daily", snapshotPolicy2.getUpdatePolicy());
				assertEquals("fail", snapshotPolicy2.getChecksumPolicy());
				RepositoryPolicy releasePolicy2 = remoteRepository.getPolicy(false);
				assertEquals(true, releasePolicy2.isEnabled());
				assertEquals("daily", releasePolicy2.getUpdatePolicy());
				assertEquals("fail", releasePolicy2.getChecksumPolicy());
			}
		}
		MavenResource resource = MavenResource
				.parse("org.springframework.cloud.task.app:timestamp-task:jar:1.0.0.BUILD-SNAPSHOT", mavenProperties);
		assertEquals(resource.exists(), true);
	}

}
