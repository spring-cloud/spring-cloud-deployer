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

package org.springframework.cloud.deployer.resource.registry;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;

/**
 * In-memory (non persistent) {@link UriRegistry} implementation.
 *
 * @author Patrick Peralta
 */
public class InMemoryUriRegistry implements UriRegistry {

	private final Map<String, URI> map = new ConcurrentHashMap<>();

	@Override
	public URI find(String key) {
		Assert.hasLength(key, "key required");
		URI uri = this.map.get(key);
		if (uri == null) {
			throw new IllegalArgumentException("No URI found for " + key);
		}
		return uri;
	}

	@Override
	public Map<String, URI> findAll() {
		return Collections.unmodifiableMap(this.map);
	}

	@Override
	public void register(String key, URI uri) {
		this.map.put(key, uri);
	}

	@Override
	public void unregister(String key) {
		this.map.remove(key);
	}

}
