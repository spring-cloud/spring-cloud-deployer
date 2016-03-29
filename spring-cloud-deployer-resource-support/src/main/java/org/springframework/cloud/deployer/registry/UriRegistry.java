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

import java.net.URI;
import java.util.Map;

/**
 * @author Patrick Peralta
 */
public interface UriRegistry {

	/**
	 * Return a {@link URI} for a string key.
	 *
	 * @param key the key for the URI
	 * @return the {@code URI} for the given key
	 * @throws IllegalAccessException if no URI is registered with the key
	 */
	URI find(String key);

	/**
	 * Return all registered {@code URI}s.
	 *
	 * @return map of keys to {@code URI}s.
	 */
	Map<String, URI> findAll();

	/**
	 * Register a {@link URI} with a string key.
	 *
	 * @param key the key for the URI
	 * @param uri the {@code URI} to associate with the key
	 */
	void register(String key, URI uri);

	/**
	 * Remove the registration for a string key.
	 *
	 * @param key the key for the {@code URI} to unregister
	 */
	void unregister(String key);

}
