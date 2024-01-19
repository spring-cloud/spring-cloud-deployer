/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.deployer.resource.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Utility class for populating a {@link UriRegistry} via a
 * {@link Properties} file. One or more URI strings indicating the
 * location of property files is supplied via the constructor,
 * and the files themselves are loaded via the {@link Resource}
 * provided by {@link #resourceLoader}.
 *
 * @author Patrick Peralta
 * @author Ilayaperumal Gopinathan
 */
public class UriRegistryPopulator implements ResourceLoaderAware {

	private static final Logger logger = LoggerFactory.getLogger(UriRegistryPopulator.class);

	private volatile ResourceLoader resourceLoader;


	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Populate the provided registry with the contents of
	 * the property files indicated by {@code resourceUris}.
	 *
	 * @param overwrite    if {@code true}, overwrites any pre-existing registrations with the same key
	 * @param registry     the registry to populate
	 * @param resourceUris string(s) indicating the URIs to load properties from
	 * @return the registered URI values in the map with the keys being the property names
	 */
	public Map<String, URI> populateRegistry(boolean overwrite, UriRegistry registry, String... resourceUris) {
		Assert.notEmpty(resourceUris, "resourceUri must not be empty");
		Map<String, URI> registered = new HashMap<>();
		for (String resourceUri : resourceUris) {
			Resource resource = this.resourceLoader.getResource(resourceUri);
			Properties properties = new Properties();
			try (InputStream is = resource.getInputStream()) {
				properties.load(is);
				for (String key : properties.stringPropertyNames()) {
					try {
						URI uri = new URI(properties.getProperty(key));
						boolean validUri = true;
						if (uri == null || StringUtils.isEmpty(uri)) {
							logger.warn(String.format("Error when registering '%s': URI is required", key));
							validUri = false;
						}
						if (validUri && !StringUtils.hasText(uri.getScheme())) {
							logger.warn(String.format("Error when registering '%s' with URI %s: URI scheme must be specified", key, uri));
							validUri = false;
						}
						if (validUri && !StringUtils.hasText(uri.getSchemeSpecificPart())) {
							logger.warn(String.format("Error when registering '%s' with URI %s: URI scheme-specific part must be specified", key, uri));
							validUri = false;
						}
						if (!overwrite) {
							try {
								if (registry.find(key) != null) {
									// already exists; move on
									continue;
								}
							}
							catch (IllegalArgumentException e) {
								// this key does not exist; will add
							}
						}
						if (validUri) {
							registry.register(key, uri);
							registered.put(key, uri);
						}
					}
					catch (URISyntaxException e) {
						throw new IllegalArgumentException(String.format("'%s' for '%s' is not a properly formed URI",
								properties.getProperty(key), key), e);
					}
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return registered;
	}

}
