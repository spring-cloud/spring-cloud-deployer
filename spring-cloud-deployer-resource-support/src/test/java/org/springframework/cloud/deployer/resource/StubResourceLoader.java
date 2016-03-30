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

package org.springframework.cloud.deployer.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * Simple {@link ResourceLoader} that returns the {@link Resource}
 * provided in the constructor. It also keeps track of the locations
 * of requested resources.
 *
 * @author Patrick Peralta
 */
public class StubResourceLoader implements ResourceLoader {

	private final Resource resource;

	private final List<String> requestedLocations = new ArrayList<>();


	/**
	 * Construct a {@code StubResourceLoader} that returns the provided
	 * resource.
	 *
	 * @param resource resource to return via {@link #getResource(String)}
	 */
	public StubResourceLoader(Resource resource) {
		this.resource = resource;
	}

	@Override
	public Resource getResource(String location) {
		this.requestedLocations.add(location);
		return this.resource;
	}

	@Override
	public ClassLoader getClassLoader() {
		return ClassUtils.getDefaultClassLoader();
	}

	public List<String> getRequestedLocations() {
		return Collections.unmodifiableList(this.requestedLocations);
	}

}
