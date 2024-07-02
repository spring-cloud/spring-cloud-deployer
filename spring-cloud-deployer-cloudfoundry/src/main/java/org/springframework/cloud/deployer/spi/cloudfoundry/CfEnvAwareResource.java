/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.core.io.Resource;

/**
 * A {@link Resource} implementation that delegates to a resource and keeps the state of a CfEnv dependency
 * as an {@link Optional} which may be empty, true, or false.
 *
 * @author David Turanski
 * @since 2.4
 */
class CfEnvAwareResource implements Resource {
	private final Resource resource;

	private final boolean hasCfEnv;

	static CfEnvAwareResource of(Resource resource) {
		return new CfEnvAwareResource(resource);
	}
	private CfEnvAwareResource(Resource resource) {
		this.resource = resource;
		this.hasCfEnv = CfEnvResolver.hasCfEnv(this);
	}

	@Override
	public boolean exists() {
		return resource.exists();
	}

	@Override
	public URL getURL() throws IOException {
		return resource.getURL();
	}

	@Override
	public URI getURI() throws IOException {
		return resource.getURI();
	}

	@Override
	public File getFile() throws IOException {
		return resource.getFile();
	}

	@Override
	public long contentLength() throws IOException {
		return resource.contentLength();
	}

	@Override
	public long lastModified() throws IOException {
		return resource.lastModified();
	}

	@Override
	public Resource createRelative(String s) throws IOException {
		return resource.createRelative(s);
	}

	@Override
	public String getFilename() {
		return resource.getFilename();
	}

	@Override
	public String getDescription() {
		return resource.getDescription();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return resource.getInputStream();
	}

	boolean hasCfEnv() {
		return this.hasCfEnv;
	}

	/**
	 * Inspect the {@link CfEnvAwareResource} to determine if it contains a dependency on <i>io.pivotal.cfenv.core.CfEnv</i>.
	 * Cache the result in the resource.
	 */
	static class CfEnvResolver {

		private static Log logger = LogFactory.getLog(CfEnvResolver.class);

		private static final String CF_ENV = "io.pivotal.cfenv.core.CfEnv";

		static boolean hasCfEnv(CfEnvAwareResource app
		) {
			try {
				String scheme = app.getURI().getScheme().toLowerCase();
				if (scheme.equals("docker")) {
					return false;
				}
			}
			catch (IOException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}

			try {
				JarFileArchive archive = new JarFileArchive(app.getFile());
				List<URL> urls = new ArrayList<>();
				archive.getNestedArchives(entry -> entry.getName().endsWith(".jar"), null).forEachRemaining(a -> {
					try {
						urls.add(a.getUrl());
					}
					catch (MalformedURLException e) {
						logger.error("Unable to process nested archive " +  e.getMessage());
					}
				});
				URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), null);
				try {
					Class.forName(CF_ENV, false, classLoader);
					return true;
				}
				catch (UnsupportedClassVersionError err) {
					logger.debug(app.getFilename() + " contain " + CF_ENV);
					// class found but can't load it i.e. because it's newer class version
					return true;
				}
				catch (ClassNotFoundException e) {
					logger.debug(app.getFilename() + " does not contain " + CF_ENV);
					return false;
				}
			}
			catch (Exception e) {
				logger.warn("Unable to determine dependencies for " + app.getFilename());
			}
			return false;
		}
	}
}
