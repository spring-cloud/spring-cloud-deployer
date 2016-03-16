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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link Resource} implementation for resolving an artifact via maven coordinates.
 * <p>
 * The {@code MavenResource} class contains <a href="https://maven.apache.org/pom.html#Maven_Coordinates">
 * Maven coordinates</a> for a jar file containing an app/library, or a Bill of Materials pom.
 * <p>
 * To create a new instance, either use {@link Builder} to set the individual fields:
 * <pre>
 * new MavenResource.Builder()
 *     .setGroupId("org.springframework.sample")
 *     .setArtifactId("some-app")
 *     .setExtension("jar") //optional
 *     .setClassifier("exec") //optional
 *     .setVersion("2.0.0")
 *     .build()
 * </pre>
 * ...or use {@link #parse(String)} to parse the coordinates as a colon delimited string:
 * <code>&lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;</code>
 * <pre>
 * MavenResource.parse("org.springframework.sample:some-app:2.0.0");
 * MavenResource.parse("org.springframework.sample:some-app:jar:exec:2.0.0");
 * MavenResource.parseUri("http://my.repo.io/repo/path/org.springframework.sample:some-app:jar:exec:2.0.0");
 * MavenResource.parseUri("maven:http://my.repo.io/repo/path/org.springframework.sample:some-app:jar:exec:2.0.0");
 * MavenResource.parseUri("maven:org.springframework.sample:some-app:jar:exec:2.0.0");
 * </pre>
 * </p>
 * @author David Turanski
 * @author Mark Fisher
 * @author Patrick Peralta
 * @author Venil Noronha
 * @author Janne Valkealahti
 */
public class MavenResource extends AbstractResource {

	private static final File LOCAL_REPO = new File(System.getProperty("user.home")
			+ File.separator + ".m2" + File.separator + "repository");

	/**
	 * Reserved URI prefix for resource loader.
	 */
	public final static String URI_PREFIX = "maven:";

	/**
	 * The default extension for the artifact.
	 */
	final static String DEFAULT_EXTENSION = "jar";

	/**
	 * String representing an empty classifier.
	 */
	final static String EMPTY_CLASSIFIER = "";

	/**
	 * Group ID for artifact; generally this includes the name of the
	 * organization that generated the artifact.
	 */
	private final String groupId;

	/**
	 * Artifact ID; generally this includes the name of the app or library.
	 */
	private final String artifactId;

	/**
	 * Extension of the artifact.
	 */
	private final String extension;

	/**
	 * Classifier of the artifact.
	 */
	private final String classifier;

	/**
	 * Version of the artifact.
	 */
	private final String version;

	/**
	 * Artifact resolver for this resource.
	 */
	private final MavenArtifactResolver resolver;

	/**
	 * Construct a {@code MavenResource} object.
	 *
	 * @param groupId group ID for artifact
	 * @param artifactId artifact ID
	 * @param extension the file extension
	 * @param classifier artifact classifier - can be null
	 * @param version artifact version
	 * @param repositories the remote repositories
	 */
	private MavenResource(String groupId, String artifactId, String extension, String classifier, String version, Map<String, String> repositories) {
		Assert.hasText(groupId, "'groupId' cannot be blank");
		Assert.hasText(artifactId, "'artifactId' cannot be blank");
		Assert.hasText(extension, "'extension' cannot be blank");
		Assert.hasText(version, "'version' cannot be blank");
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.extension = extension;
		this.classifier = classifier == null ? EMPTY_CLASSIFIER : classifier;
		this.version = version;
		this.resolver = new MavenArtifactResolver(LOCAL_REPO, repositories);
	}

	/**
	 * @see #groupId
	 */
	public String getGroupId() {
		return groupId;
	}

	/**
	 * @see #artifactId
	 */
	public String getArtifactId() {
		return artifactId;
	}

	/**
	 * @see #extension
	 */
	public String getExtension() {
		return extension;
	}

	/**
	 * @see #version
	 */
	public String getClassifier() {
		return classifier;
	}

	/**
	 * @see #version
	 */
	public String getVersion() {
		return version;
	}

	@Override
	public String getDescription() {
		return this.toString();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return resolver.resolve(this).getInputStream();
	}

	@Override
	public File getFile() throws IOException {
		return resolver.resolve(this).getFile();
	}

	@Override
	public boolean exists() {
		// Resource.exists() doesn't throw so
		// lets catch and return false if it does
		try {
			return super.exists();
		} catch (Exception e) {
		}
		return false;
	}

	@Override
	public String getFilename() {
		return StringUtils.hasLength(classifier) ?
				String.format("%s-%s-%s.%s", artifactId, version, classifier, extension) :
				String.format("%s-%s.%s", artifactId, version, extension);
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MavenResource)) {
			return false;
		}
		MavenResource that = (MavenResource) o;
		return this.groupId.equals(that.groupId) &&
				this.artifactId.equals(that.artifactId) &&
				this.extension.equals(that.extension) &&
				this.classifier.equals(that.classifier) &&
				this.version.equals(that.version);
	}

	@Override
	public int hashCode() {
		int result = groupId.hashCode();
		result = 31 * result + artifactId.hashCode();
		result = 31 * result + extension.hashCode();
		if (StringUtils.hasLength(classifier)) {
			result = 31 * result + classifier.hashCode();
		}
		result = 31 * result + version.hashCode();
		return result;
	}

	/**
	 * Returns the coordinates encoded as
	 * &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;,
	 * conforming to the <a href="http://www.eclipse.org/aether">Aether</a> convention.
	 */
	@Override
	public String toString() {
		return StringUtils.hasLength(classifier) ?
				String.format("%s:%s:%s:%s:%s", groupId, artifactId, extension, classifier, version) :
				String.format("%s:%s:%s:%s", groupId, artifactId, extension, version);
	}

	/**
	 * Parse coordinates given as a colon delimited string.
	 *
	 * @param coordinates coordinates encoded as <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>,
	 * conforming to the <a href="http://www.eclipse.org/aether">Aether</a> convention.
	 * @return the maven resource
	 */
	public static MavenResource parse(String coordinates) {
		return parse(coordinates, null);
	}

	/**
	 * Parse coordinates given as a colon delimited string.
	 *
	 * @param coordinates coordinates encoded as <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>,
	 * conforming to the <a href="http://www.eclipse.org/aether">Aether</a> convention.
	 * @param repositories the remote repositories
	 * @return the maven resource
	 */
	public static MavenResource parse(String coordinates, Map<String, String> remoteRepositories) {
		Assert.hasText(coordinates);
		Pattern p = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
		Matcher m = p.matcher(coordinates);
		Assert.isTrue(m.matches(), "Bad artifact coordinates " + coordinates
				+ ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
		String groupId = m.group(1);
		String artifactId = m.group(2);
		String extension = StringUtils.hasLength(m.group(4)) ? m.group(4) : DEFAULT_EXTENSION;
		String classifier = StringUtils.hasLength(m.group(6)) ? m.group(6) : EMPTY_CLASSIFIER;
		String version = m.group(7);
		return new MavenResource(groupId, artifactId, extension, classifier, version, remoteRepositories);
	}

	/**
	 * Parses resource using a given uri pointing into maven repo.
	 *
	 * @param uri the uri
	 * @return the maven resource
	 * @throws URISyntaxException the URI syntax exception
	 */
	public static MavenResource parseUri(String uri) throws URISyntaxException {
		Assert.hasText(uri, "Uri must be set");
		uri = uri.startsWith(URI_PREFIX) ? uri.substring(URI_PREFIX.length()) : uri;
		return parseUri(new URI(uri));
	}

	/**
	 * Parses resource using a given uri pointing into maven repo.
	 *
	 * @param uri the uri
	 * @return the maven resource
	 * @throws URISyntaxException the URI syntax exception
	 */
	public static MavenResource parseUri(URI uri) {
		Assert.notNull(uri, "Uri must be set");
		Map<String, String> remoteRepositories = new HashMap<String, String>();
		String artifact = null;
		if (StringUtils.hasText(uri.getPath())) {
			String[] segments = uri.getPath().split("/");
			Assert.isTrue(segments.length > 2, "Malformed uri, " + uri);
			// build actual repo url and its id from paths
			StringBuilder idBuilder = new StringBuilder();
			StringBuilder urlBuilder = new StringBuilder();
			urlBuilder.append(uri.getScheme());
			urlBuilder.append("://");
			urlBuilder.append(uri.getHost());
			urlBuilder.append("/");
			for (int i = 0; i < segments.length - 1; i++) {
				if (StringUtils.hasText(segments[i])) {
					idBuilder.append(segments[i]);
					urlBuilder.append(segments[i]);
					if (i < segments.length) {
						idBuilder.append("/");
						urlBuilder.append("/");
					}
				}
			}
			artifact = segments[segments.length-1];
			remoteRepositories.put(idBuilder.toString(), urlBuilder.toString());
		} else {
			// if we don't have actual path part, assume
			// whole uri is a artifact coordinates
			artifact = uri.toString();
		}
		return parse(artifact, remoteRepositories);
	}

	public static class Builder {

		private String groupId;

		private String artifactId;

		private String extension = DEFAULT_EXTENSION;

		private String classifier = EMPTY_CLASSIFIER;

		private String version;

		private Map<String, String> repositories = new HashMap<String, String>();

		public Builder setGroupId(String groupId) {
			this.groupId = groupId;
			return this;
		}

		public Builder setArtifactId(String artifactId) {
			this.artifactId = artifactId;
			return this;
		}

		public Builder setExtension(String extension) {
			this.extension = extension;
			return this;
		}

		public Builder setClassifier(String classifier) {
			this.classifier = classifier;
			return this;
		}

		public Builder setVersion(String version) {
			this.version = version;
			return this;
		}

		public Builder addRepository(String id, String url) {
			this.repositories.put(id, url);
			return this;
		}

		public MavenResource build() {
			return new MavenResource(groupId, artifactId, extension, classifier, version, repositories);
		}
	}
}
