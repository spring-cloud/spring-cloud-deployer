/*
 * Copyright 2020 the original author or authors.
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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cloud.deployer.resource.maven.MavenProperties.Authentication;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.RemoteRepository;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.WagonHttpMethod;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.WagonHttpMethodProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Maven Wagon resolver.
 *
 * @author Janne Valkealahti
 * @author Corneil du Plessis
 */
public class WagonHttpTests {

	@RegisterExtension
	static MavenExtension server = new MavenExtension();

	@Test
	public void resourceDoesNotExistWagon(@TempDir Path tempDir) {
		MavenProperties mavenProperties = new MavenProperties();
		mavenProperties.setLocalRepository(tempDir.toAbsolutePath().toString());
		mavenProperties.setUseWagon(true);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("http://localhost:" + server.getPort() + "/public"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource
				.parse("org.example:doesnotexist:jar:1.0.0.RELEASE", mavenProperties);
		assertThat(resource.exists()).isFalse();
	}


	@Test
	public void resourceDoesExistWagon(@TempDir Path tempDir) {
		MavenProperties mavenProperties = new MavenProperties();
		mavenProperties.setLocalRepository(tempDir.toAbsolutePath().toString());
		mavenProperties.setUseWagon(true);
		Map<String, MavenProperties.RemoteRepository> remoteRepositoryMap = new HashMap<>();
		remoteRepositoryMap.put("default",
				new MavenProperties.RemoteRepository("http://localhost:" + server.getPort() + "/public"));
		mavenProperties.setRemoteRepositories(remoteRepositoryMap);
		MavenResource resource = MavenResource
				.parse("org.example:app:jar:1.0.0.RELEASE", mavenProperties);
		assertThat(resource.exists()).isTrue();
	}

	@Test
	public void resourceDoesExistWithAuth(@TempDir Path tempDir) {
		MavenProperties mavenProperties = new MavenProperties();
		mavenProperties.setLocalRepository(tempDir.toAbsolutePath().toString());
		mavenProperties.setUseWagon(true);
		Map<String, RemoteRepository> remoteRepositories = new HashMap<>();
		RemoteRepository remoteRepository = new RemoteRepository("http://localhost:" + server.getPort() + "/private");
		Authentication auth = new Authentication("user", "password");
		remoteRepository.setAuth(auth);
		remoteRepositories.put("default", remoteRepository);
		mavenProperties.setRemoteRepositories(remoteRepositories);
		MavenResource resource = MavenResource
				.parse("org.example:secured:jar:1.0.0.RELEASE", mavenProperties);
		assertThat(resource.exists()).isTrue();
	}

	@Test
	public void resourceDoesExistWithPreemptiveAuth(@TempDir Path tempDir) {
		MavenProperties mavenProperties = new MavenProperties();
		mavenProperties.setLocalRepository(tempDir.toAbsolutePath().toString());

		mavenProperties.setUseWagon(true);
		Map<String, RemoteRepository> remoteRepositories = new HashMap<>();
		RemoteRepository remoteRepository = new RemoteRepository("http://localhost:" + server.getPort() + "/preemptive");
		WagonHttpMethodProperties wagonHttpMethodProperties = new WagonHttpMethodProperties();
		wagonHttpMethodProperties.setUsePreemptive(true);

		Map<String, String> headers = new HashMap<>();
		headers.put("Foo", "Bar");
		Map<String, String> params = new HashMap<>();
		params.put("http.connection.stalecheck", "true");
		wagonHttpMethodProperties.setHeaders(headers);
		wagonHttpMethodProperties.setParams(params);

		remoteRepository.getWagon().getHttp().put(WagonHttpMethod.all, wagonHttpMethodProperties);
		Authentication auth = new Authentication("user", "password");
		remoteRepository.setAuth(auth);
		remoteRepositories.put("default", remoteRepository);
		mavenProperties.setRemoteRepositories(remoteRepositories);
		MavenResource resource = MavenResource
				.parse("org.example:preemptive:jar:1.0.0.RELEASE", mavenProperties);
		assertThat(resource.exists()).isTrue();
	}
}
