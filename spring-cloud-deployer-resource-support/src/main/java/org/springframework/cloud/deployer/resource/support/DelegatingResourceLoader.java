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

package org.springframework.cloud.deployer.resource.support;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;

/**
 * A {@link ResourceLoader} implementation that delegates to other {@link ResourceLoader} instances
 * that are stored in a Map with their associated URI schemes as the keys.
 *
 * This implementation is also caching remote resources which are not directly accessible
 * as {@link File} into either a given cache directory or on default a temporary location
 * prefixed by "deployer-resource-cache".
 *
 * @author Mark Fisher
 * @author Janne Valkealahti
 */
public class DelegatingResourceLoader implements ResourceLoader {

	private static final Logger logger = LoggerFactory.getLogger(DelegatingResourceLoader.class);

	private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	private final Map<String, ResourceLoader> loaders;

	private final File cacheDirectory;

	private final static String DEFAULT_CACHE_PREFIX = "deployer-resource-cache";

	/**
	 * Instantiates a new delegating resource loader.
	 *
	 * @param loaders the loaders
	 */
	public DelegatingResourceLoader(Map<String, ResourceLoader> loaders) {
		this(loaders, null);
	}

	/**
	 * Instantiates a new delegating resource loader.
	 *
	 * @param loaders the loaders
	 * @param cacheDirectory the cache directory
	 */
	public DelegatingResourceLoader(Map<String, ResourceLoader> loaders, File cacheDirectory) {
		Assert.notEmpty(loaders, "at least one ResourceLoader is required");
		this.loaders = Collections.unmodifiableMap(loaders);
		this.cacheDirectory = initCacheDirectory(cacheDirectory);
	}

	@Override
	public Resource getResource(String location) {
		try {
			URI uri = new URI(location);
			String scheme = uri.getScheme();
			Assert.notNull(scheme, "a scheme (prefix) is required");
			ResourceLoader loader = this.loaders.get(scheme);
			Assert.notNull(loader, String.format("no ResourceLoader available for scheme: %s", scheme));

			Resource resource = loader.getResource(location);
			if (existsAsFile(resource)) {
				return resource;
			}
			else {
				String cacheName = scheme + "-" + ShaUtils.sha1(location) + "-" + resource.getFilename();
				File cachedResource = new File(cacheDirectory, cacheName);
				if (!cachedResource.exists()) {
					logger.info("Caching file {} as given location {}", cachedResource, location);
					FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(cachedResource));
				}
				else {
					logger.info("Reusing cached file {} as given location {}", cachedResource, location);
				}
				return new FileSystemResource(cachedResource);
			}
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

	/**
	 * Handles init operation of a local cache directory.
	 *
	 * @param cacheDirectory the cache directory
	 * @return the directory
	 */
	private File initCacheDirectory(File cacheDirectory) {
		try {
			if (cacheDirectory == null) {
				Path tempDirectory = Files.createTempDirectory(DEFAULT_CACHE_PREFIX);
				return tempDirectory.toFile();
			}
			else {
				cacheDirectory.mkdirs();
				return cacheDirectory;
			}
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Unable to create cache directory", e);
		}
	}

	/**
	 * Check if a resource exists as a file.
	 *
	 * @param resource the resource
	 * @return true, if resource can be accessed as file
	 */
	private static boolean existsAsFile(Resource resource) {
		try {
			resource.getFile();
			return true;
		}
		catch (IOException e) {
		}
		return false;
	}
}
