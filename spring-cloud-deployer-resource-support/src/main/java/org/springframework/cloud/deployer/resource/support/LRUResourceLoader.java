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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * A wrapper around a ResourceLoader that limits the space used on disk of resolved resources by
 * removing the least recently resolved entries.
 *
 * @author Eric Bottard
 */
public class LRUResourceLoader implements ResourceLoader {

	private static final Logger logger = LoggerFactory.getLogger(LRUResourceLoader.class);

	private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	private final ResourceLoader delegate;

	private final Map<File, Long> cache;

	/**
	 * Wrap an existing ResourceLoader, limiting total used space to {@literal maxSize} bytes.
	 */
	public LRUResourceLoader(ResourceLoader delegate, long maxSize) {
		this.delegate = delegate;
		cache = new LRUSizedCache(maxSize);
	}

	@Override
	public Resource getResource(String location) {
		Resource resource = delegate.getResource(location);
		try {
			File file = resource.getFile();
			cache.put(file, file.length());
			cache.get(file); // triggers removeEldestEntry()
			return resource;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	/**
	 * An extension of {@link LinkedHashMap} used in access order, that keeps track of cumulative used disk space
	 * and purges LRU entries on access until total used size is less than the configured max.
	 *
	 * @author Eric Bottard
	 */
	private static class LRUSizedCache extends LinkedHashMap<File, Long> {
		private final long maxSize;


		private LRUSizedCache(long maxSize) {
			super(5, 0.75f, true);
			this.maxSize = maxSize;
		}


		@Override
		protected boolean removeEldestEntry(Map.Entry<File, Long> eldest) {
			// Compute cumulative used disk space, in MRU order
			List<Map.Entry<File, Long>> lruLast = new ArrayList<>(this.entrySet());
			Collections.reverse(lruLast);
			LinkedHashMap<File, Long> totals = new LinkedHashMap<>();
			long total = 0L;
			for (Map.Entry<File, Long> kv : lruLast) {
				totals.put(kv.getKey(), total += kv.getValue());
			}

			for (Map.Entry<File, Long> kv : totals.entrySet()) {
				if (kv.getValue() > maxSize) {
					logger.debug("Purging {} from FileSystem, reclaiming {} bytes", kv.getKey(), this.get(kv.getKey()));
					kv.getKey().delete();
					this.remove(kv.getKey());
				}
			}
			return false; // Don't let LHM remove *the* eldest entry. We already did some cleanup
		}
	}

}
