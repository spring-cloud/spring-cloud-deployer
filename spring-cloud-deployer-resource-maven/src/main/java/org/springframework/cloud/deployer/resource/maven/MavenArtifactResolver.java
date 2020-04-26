/*
 * Copyright 2015-2020 the original author or authors.
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

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.wagon.WagonConfigurator;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Resolves a {@link MavenResource} using <a href="https://www.eclipse.org/aether/>aether</a> to
 * locate the artifact (uber jar) in a local Maven repository, downloading the latest update from a
 * remote repository if necessary.
 *
 * @author David Turanski
 * @author Mark Fisher
 * @author Marius Bogoevici
 * @author Ilayaperumal Gopinathan
 * @author Donovan Muller
 */
class MavenArtifactResolver {

	private static final Logger log = LoggerFactory.getLogger(MavenArtifactResolver.class);

	private static final String DEFAULT_CONTENT_TYPE = "default";

	private final RepositorySystem repositorySystem;

	private final MavenProperties properties;

	private final List<RemoteRepository> remoteRepositories = new LinkedList<>();

	private final Authentication proxyAuthentication;

	/**
	 * Create an instance using the provided properties.
	 *
	 * @param properties the properties for the maven repositories, proxies, and authentication
	 */
	public MavenArtifactResolver(final MavenProperties properties) {
		Assert.notNull(properties, "MavenProperties must not be null");
		Assert.notNull(properties.getLocalRepository(), "Local repository path cannot be null");
		if (log.isDebugEnabled()) {
			log.debug("Local repository: " + properties.getLocalRepository());
			log.debug("Remote repositories: " +
					StringUtils.collectionToCommaDelimitedString(properties.getRemoteRepositories().keySet()));
		}
		this.properties = properties;
		if (isProxyEnabled() && proxyHasCredentials()) {
			final String username = this.properties.getProxy().getAuth().getUsername();
			final String password = this.properties.getProxy().getAuth().getPassword();
			this.proxyAuthentication = newAuthentication(username, password);
		}
		else {
			this.proxyAuthentication = null;
		}
		File localRepository = new File(this.properties.getLocalRepository());
		if (!localRepository.exists()) {
			boolean created = localRepository.mkdirs();
			// May have been created by another thread after above check. Double check.
			Assert.isTrue(created || localRepository.exists(),
					"Unable to create directory for local repository: " + localRepository);
		}
		for (Map.Entry<String, MavenProperties.RemoteRepository> entry : this.properties.getRemoteRepositories()
				.entrySet()) {
			MavenProperties.RemoteRepository remoteRepository = entry.getValue();
			RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(
					entry.getKey(), DEFAULT_CONTENT_TYPE, remoteRepository.getUrl());
			// Update policies when set.
			if (remoteRepository.getPolicy() != null) {
				remoteRepositoryBuilder.setPolicy(new RepositoryPolicy(remoteRepository.getPolicy().isEnabled(),
						remoteRepository.getPolicy().getUpdatePolicy(),
						remoteRepository.getPolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getReleasePolicy() != null) {
				remoteRepositoryBuilder
						.setReleasePolicy(new RepositoryPolicy(remoteRepository.getReleasePolicy().isEnabled(),
								remoteRepository.getReleasePolicy().getUpdatePolicy(),
								remoteRepository.getReleasePolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getSnapshotPolicy() != null) {
				remoteRepositoryBuilder
						.setSnapshotPolicy(new RepositoryPolicy(remoteRepository.getSnapshotPolicy().isEnabled(),
								remoteRepository.getSnapshotPolicy().getUpdatePolicy(),
								remoteRepository.getSnapshotPolicy().getChecksumPolicy()));
			}
			if (remoteRepositoryHasCredentials(remoteRepository)) {
				final String username = remoteRepository.getAuth().getUsername();
				final String password = remoteRepository.getAuth().getPassword();
				remoteRepositoryBuilder.setAuthentication(newAuthentication(username, password));
			}

			RemoteRepository repo = remoteRepositoryBuilder.build();
			Proxy proxy = null;
			if (isProxyEnabled()) {
				MavenProperties.Proxy proxyProperties = this.properties.getProxy();
				if (this.proxyAuthentication != null) {
					proxy = new Proxy(
							proxyProperties.getProtocol(),
							proxyProperties.getHost(),
							proxyProperties.getPort(),
							this.proxyAuthentication);
				}
				else {
					// if proxy does not require authentication
					proxy = new Proxy(
							proxyProperties.getProtocol(),
							proxyProperties.getHost(),
							proxyProperties.getPort());
				}

				DefaultProxySelector proxySelector = new DefaultProxySelector();
				proxySelector.add(proxy, this.properties.getProxy().getNonProxyHosts());

				proxy = proxySelector.getProxy(repo);
			}

			remoteRepositoryBuilder = new RemoteRepository.Builder(repo);
			remoteRepositoryBuilder.setProxy(proxy);
			this.remoteRepositories.add(remoteRepositoryBuilder.build());
		}
		this.repositorySystem = newRepositorySystem();
	}

	/**
	 * Check if the proxy settings are provided.
	 *
	 * @return boolean true if the proxy settings are provided.
	 */
	private boolean isProxyEnabled() {
		return (this.properties.getProxy() != null &&
				this.properties.getProxy().getHost() != null &&
				this.properties.getProxy().getPort() > 0);
	}

	/**
	 * Check if the proxy setting has username/password set.
	 *
	 * @return boolean true if both the username/password are set
	 */
	private boolean proxyHasCredentials() {
		return (this.properties.getProxy() != null &&
				this.properties.getProxy().getAuth() != null &&
				this.properties.getProxy().getAuth().getUsername() != null &&
				this.properties.getProxy().getAuth().getPassword() != null);
	}

	/**
	 * Check if the {@link MavenProperties.RemoteRepository} setting has username/password set.
	 *
	 * @return boolean true if both the username/password are set
	 */
	private boolean remoteRepositoryHasCredentials(MavenProperties.RemoteRepository remoteRepository) {
		return remoteRepository != null &&
				remoteRepository.getAuth() != null &&
				remoteRepository.getAuth().getUsername() != null &&
				remoteRepository.getAuth().getPassword() != null;
	}

	/**
	 * Create an {@link Authentication} given a username/password
	 *
	 * @param username
	 * @param password
	 * @return a configured {@link Authentication}
	 */
	private Authentication newAuthentication(final String username, final String password) {
		return new Authentication() {

			@Override
			public void fill(AuthenticationContext context, String key, Map<String, String> data) {
				context.put(AuthenticationContext.USERNAME, username);
				context.put(AuthenticationContext.PASSWORD, password);
			}

			@Override
			public void digest(AuthenticationDigest digest) {
				digest.update(AuthenticationContext.USERNAME, username,
						AuthenticationContext.PASSWORD, password);
			}
		};
	}

	/*
	 * Create a session to manage remote and local synchronization.
	 */
	private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, String localRepoPath) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = new LocalRepository(localRepoPath);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
		session.setOffline(this.properties.isOffline());
		session.setUpdatePolicy(this.properties.getUpdatePolicy());
		session.setChecksumPolicy(this.properties.getChecksumPolicy());
		if (this.properties.isEnableRepositoryListener()) {
			session.setRepositoryListener(new LoggingRepositoryListener());
		}
		if (this.properties.getConnectTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, this.properties.getConnectTimeout());
		}
		if (this.properties.getRequestTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, this.properties.getRequestTimeout());
		}
		if (isProxyEnabled()) {
			DefaultProxySelector proxySelector = new DefaultProxySelector();
			Proxy proxy = new Proxy(this.properties.getProxy().getProtocol(),
					this.properties.getProxy().getHost(),
					this.properties.getProxy().getPort(),
					this.proxyAuthentication);
			proxySelector.add(proxy, this.properties.getProxy().getNonProxyHosts());
			session.setProxySelector(proxySelector);
		}
		// wagon configs
		for (Entry<String, MavenProperties.RemoteRepository> entry : this.properties.getRemoteRepositories().entrySet()) {
			session.setConfigProperty("aether.connector.wagon.config." + entry.getKey(), entry.getValue().getWagon());
		}
		return session;
	}

	/*
	 * Aether's components implement {@link org.eclipse.aether.spi.locator.Service} to ease manual wiring.
	 * Using the prepopulated {@link DefaultServiceLocator}, we need to register the repository connector
	 * and transporter factories
	 */
	private RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);

		if (properties.isUseWagon()) {
			locator.addService(WagonProvider.class, StaticWagonProvider.class);
			locator.addService(WagonConfigurator.class, StaticWagonConfigurator.class);
			locator.addService(TransporterFactory.class, WagonTransporterFactory.class);
		} else {
			locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
		}

		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				throw new RuntimeException(exception);
			}
		});
		return locator.getService(RepositorySystem.class);
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether performs the normal
	 * Maven resolution process ensuring that the latest update is cached to the local repository.
	 * In addition, if the {@link MavenProperties#resolvePom} flag is <code>true</code>,
	 * the POM is also resolved and cached.
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link FileSystemResource} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	Resource resolve(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		validateCoordinates(resource);
		RepositorySystemSession session = newRepositorySystemSession(this.repositorySystem,
				this.properties.getLocalRepository());
		ArtifactResult resolvedArtifact;
		try {
			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(toPomArtifact(resource),
						this.remoteRepositories,
						JavaScopes.RUNTIME));
			}
			artifactRequests.add(new ArtifactRequest(toJarArtifact(resource),
					this.remoteRepositories,
					JavaScopes.RUNTIME));

			List<ArtifactResult> results = this.repositorySystem.resolveArtifacts(session, artifactRequests);
			resolvedArtifact = results.get(results.size() - 1);
		}
		catch (ArtifactResolutionException e) {

			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
		return toResource(resolvedArtifact);
	}

	private void validateCoordinates(MavenResource resource) {
		Assert.hasText(resource.getGroupId(), "groupId must not be blank.");
		Assert.hasText(resource.getArtifactId(), "artifactId must not be blank.");
		Assert.hasText(resource.getExtension(), "extension must not be blank.");
		Assert.hasText(resource.getVersion(), "version must not be blank.");
	}

	public FileSystemResource toResource(ArtifactResult resolvedArtifact) {
		return new FileSystemResource(resolvedArtifact.getArtifact().getFile());
	}

	private Artifact toJarArtifact(MavenResource resource) {
		return toArtifact(resource, resource.getExtension());
	}

	private Artifact toPomArtifact(MavenResource resource) {
		return toArtifact(resource, "pom");
	}

	private Artifact toArtifact(MavenResource resource, String extension) {
		return new DefaultArtifact(resource.getGroupId(),
				resource.getArtifactId(),
				resource.getClassifier() != null ? resource.getClassifier() : "",
				extension,
				resource.getVersion());
	}
}
