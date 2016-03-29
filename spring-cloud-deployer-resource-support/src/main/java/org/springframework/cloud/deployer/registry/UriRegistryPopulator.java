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

package org.springframework.cloud.deployer.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * @author Patrick Peralta
 */
public class UriRegistryPopulator implements ResourceLoaderAware {
	private static final Logger logger = LoggerFactory.getLogger(UriRegistryPopulator.class);

	private volatile ResourceLoader resourceLoader;

	private final String[] resourceUri;

	
	public UriRegistryPopulator(String[] resourceUri) {
		Assert.noNullElements(resourceUri);
		this.resourceUri = resourceUri;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public void populateRegistry(UriRegistry registry) {
		for (String uri : this.resourceUri) {
			Resource resource = this.resourceLoader.getResource(uri);
			Properties properties = new Properties();
			try(InputStream is = resource.getInputStream()) {
				properties.load(is);
				for (String key : properties.stringPropertyNames()) {
					try {
						registry.register(key, new URI(properties.getProperty(key)));
					}
					catch (URISyntaxException e) {
						logger.warn(String.format("'%s' for '%s' is not a properly formed URI",
								properties.getProperty(key), key), e);
					}
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

	}

}
