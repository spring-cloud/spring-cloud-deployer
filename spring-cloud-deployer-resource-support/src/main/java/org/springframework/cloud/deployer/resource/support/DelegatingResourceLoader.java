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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link ResourceLoader} implementation that delegates to other {@link ResourceLoader} instances
 * that are stored in a Map with their associated URI schemes as the keys.
 *
 * @author Mark Fisher
 */
public class DelegatingResourceLoader implements ResourceLoader {

	private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	private final Map<String, ResourceLoader> loaders;

	public DelegatingResourceLoader(Map<String, ResourceLoader> loaders) {
		Assert.notEmpty(loaders, "at least one ResourceLoader is required");
		this.loaders = Collections.unmodifiableMap(loaders);
	}

	@Override
	public Resource getResource(String location) {
		try {
			URI uri = new URI(location);
			String scheme = uri.getScheme();
			Assert.notNull(scheme, "a scheme (prefix) is required");
			ResourceLoader loader = loaders.get(scheme);
			Assert.notNull(loader, String.format("no ResourcLoader available for scheme: %s", scheme));
			return loader.getResource(location);
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

}
