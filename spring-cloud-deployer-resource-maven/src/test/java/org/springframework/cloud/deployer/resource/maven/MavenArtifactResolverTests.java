/*
 * Copyright 2016-2023 the original author or authors.
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

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @author Eric Chen
 * @author Chris Bono
 */
class MavenArtifactResolverTests {

    @Test
    void resolveFailsOnProxyWithUnknownHostException() {
        String location = "maven://foo:bar:1.0.1";
        MavenResourceLoader loader = new MavenResourceLoader(new MavenProperties());
        Resource resource = loader.getResource(location);
        assertThat(MavenResource.class).isEqualTo(resource.getClass());
        MavenResource mavenResource = (MavenResource) resource;
        MavenArtifactResolver resolver = new MavenArtifactResolver(mavenPropertiesWithProxyRepo());
        assertThatThrownBy(() -> resolver.resolve(mavenResource))
                .rootCause()
                .isInstanceOf(UnknownHostException.class)
                .hasMessageStartingWith("proxy.example.com:");
    }

    private MavenProperties mavenPropertiesWithProxyRepo() {
        MavenProperties mavenProperties = new MavenProperties();
        mavenProperties.setLocalRepository("~/.m2");
        mavenProperties.setIncludeDefaultRemoteRepos(false);
        MavenProperties.RemoteRepository remoteRepo2 = new MavenProperties.RemoteRepository();
        remoteRepo2.setUrl("http://myrepo.com:99999");
        mavenProperties.getRemoteRepositories().put("repo2", remoteRepo2);
        MavenProperties.Proxy proxy = new MavenProperties.Proxy();
        proxy.setHost("proxy.example.com");
        proxy.setPort(8080);
        proxy.setNonProxyHosts("apache*|*.springframework.org|127.0.0.1|localhost");
        mavenProperties.setProxy(proxy);
        return mavenProperties;
    }

    @Test
    void resolveFailsOnNoProxyWithIllegalArgumentException() {
        String location = "maven://foo:bar:1.0.1";
        MavenResourceLoader loader = new MavenResourceLoader(new MavenProperties());
        Resource resource = loader.getResource(location);
        assertThat(MavenResource.class).isEqualTo(resource.getClass());
        MavenResource mavenResource = (MavenResource) resource;
        MavenArtifactResolver resolver = new MavenArtifactResolver(mavenPropertiesWithNoProxyRepo());
        assertThatThrownBy(() -> resolver.resolve(mavenResource))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("port out of range:99999");
    }

    private MavenProperties mavenPropertiesWithNoProxyRepo() {
        MavenProperties mavenProperties = new MavenProperties();
        mavenProperties.setLocalRepository("~/.m2");
        mavenProperties.setIncludeDefaultRemoteRepos(false);
        MavenProperties.RemoteRepository remoteRepo1 = new MavenProperties.RemoteRepository();
        remoteRepo1.setUrl("http://localhost:99999");
        mavenProperties.getRemoteRepositories().put("repo1", remoteRepo1);
        MavenProperties.Proxy proxy = new MavenProperties.Proxy();
        proxy.setHost("http://proxy.example.com");
        proxy.setPort(8080);
        proxy.setNonProxyHosts("apache*|*.springframework.org|127.0.0.1|localhost");
        mavenProperties.setProxy(proxy);
        return mavenProperties;
    }

    @Test
    void resolveFailsWithErrorMessageIncludingRemoteRepos() {
        String location = "maven://one:car:1.0.1";
        MavenResourceLoader loader = new MavenResourceLoader(new MavenProperties());
        Resource resource = loader.getResource(location);
        assertThat(MavenResource.class).isEqualTo(resource.getClass());
        MavenResource mavenResource = (MavenResource) resource;
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setIncludeDefaultRemoteRepos(false);
        MavenProperties.RemoteRepository remoteRepo = new MavenProperties.RemoteRepository();
        remoteRepo.setUrl("http://myrepo.com:99999");
        mavenProps.getRemoteRepositories().put("repo2", remoteRepo);

        MavenArtifactResolver resolver = new MavenArtifactResolver(mavenProps);
        assertThatThrownBy(() -> resolver.resolve(mavenResource))
                .hasMessage("Failed to resolve one:car:jar:1.0.1 using remote repo(s): [repo2 (http://myrepo.com:99999)]");
    }

    @Test
    void defaultReposAddedWhenNoOtherRemoteRepos() {
        MavenProperties mavenProps = new MavenProperties();
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("id", "url")
                .containsExactly(tuple("mavenCentral-default", "https://repo.maven.apache.org/maven2"),
                        tuple("springSnapshot-default", "https://repo.spring.io/snapshot"),
                        tuple("springMilestone-default", "https://repo.spring.io/milestone"));
    }

    @Test
    void defaultReposAddedInFrontOfOtherRemoteRepos() {
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setRemoteRepositories(Collections.singletonMap("myRepo", new MavenProperties.RemoteRepository("https://my.custom.repo/snapshot")));
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("id", "url")
                .containsExactly(tuple("mavenCentral-default", "https://repo.maven.apache.org/maven2"),
                        tuple("springSnapshot-default", "https://repo.spring.io/snapshot"),
                        tuple("springMilestone-default", "https://repo.spring.io/milestone"),
                        tuple("myRepo", "https://my.custom.repo/snapshot"));
    }

    @ParameterizedTest
    @MethodSource("defaultReposIdAndUrlProvider")
    void defaultRepoAddedWhenNotAlreadyConfigured(String defaultRepoId, String defaultRepoUrl) {
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setRemoteRepositories(Collections.singletonMap("myRepo", new MavenProperties.RemoteRepository(defaultRepoUrl + "/foo")));
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("id", "url")
                .hasSize(4)
                .contains(tuple("myRepo", defaultRepoUrl + "/foo"))
                .contains(tuple(defaultRepoId, defaultRepoUrl));
    }

    @ParameterizedTest
    @MethodSource("defaultReposIdAndUrlProvider")
    void defaultRepoNotAddedWhenAlreadyConfigured(String defaultRepoId, String defaultRepoUrl) {
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setRemoteRepositories(Collections.singletonMap("myRepo", new MavenProperties.RemoteRepository(defaultRepoUrl)));
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("id", "url")
                .hasSize(3)
                .contains(tuple("myRepo", defaultRepoUrl))
                .doesNotContain(tuple(defaultRepoId, defaultRepoUrl));
    }

    static Stream<Arguments> defaultReposIdAndUrlProvider() {
        return Stream.of(
                arguments("mavenCentral-default", "https://repo.maven.apache.org/maven2"),
                arguments("springSnapshot-default", "https://repo.spring.io/snapshot"),
                arguments("springMilestone-default", "https://repo.spring.io/milestone")
        );
    }

    @Test
    void defaultReposNotAddedWhenPropertyIsDisabledWithNoOtherRepos() {
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setIncludeDefaultRemoteRepos(false);
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories()).isEmpty();
    }

    @Test
    void defaultReposNotAddedWhenPropertyIsDisabledWithOtherRepo() {
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setIncludeDefaultRemoteRepos(false);
        mavenProps.setRemoteRepositories(Collections.singletonMap("myRepo",
                new MavenProperties.RemoteRepository("https://repo.snap.io/snapshot")));
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("url")
                .containsExactly("https://repo.snap.io/snapshot");
    }

    @Test
    void defaultReposAddedAndProxiedWhenProxyEnabled() {
        MavenProperties.Proxy proxy = new MavenProperties.Proxy();
        proxy.setHost("proxy.example.com");
        proxy.setPort(8080);
        MavenProperties mavenProps = new MavenProperties();
        mavenProps.setProxy(proxy);
        MavenArtifactResolver mavenResolver = new MavenArtifactResolver(mavenProps);
        assertThat(mavenResolver.remoteRepositories())
                .extracting("url", "proxy.type", "proxy.host", "proxy.port")
                .containsExactly(tuple("https://repo.maven.apache.org/maven2", "http", "proxy.example.com", 8080),
                        tuple("https://repo.spring.io/snapshot", "http", "proxy.example.com", 8080),
                        tuple("https://repo.spring.io/milestone", "http", "proxy.example.com", 8080));
    }

}
