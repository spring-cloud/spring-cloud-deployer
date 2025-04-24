/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.deployer.resource.support;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cloud.deployer.resource.StubResourceLoader;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link DelegatingResourceLoader}.
 *
 * @author Patrick Peralta
 * @author Janne Valkealahti
 * @author Ilayaperumal Gopinathan
 */
public class DelegatingResourceLoaderTests {

	@TempDir
	public File folder;

	@Test
	public void test() {
		NullResource one = new NullResource("one");
		NullResource two = new NullResource("two");
		NullResource three = new NullResource("three");

		assertNotEquals(one, two);
		assertNotEquals(two, three);

		Map<String, ResourceLoader> map = new HashMap<>();
		map.put("one", new StubResourceLoader(one));
		map.put("two", new StubResourceLoader(two));
		map.put("three", new StubResourceLoader(three));

		DelegatingResourceLoader resourceLoader = new DelegatingResourceLoader(map);
		assertEquals(one, resourceLoader.getResource("one://one"));
		assertEquals(two, resourceLoader.getResource("two://two"));
		assertEquals(three, resourceLoader.getResource("three://three"));
	}

	static class NullResource extends AbstractResource {

		final String description;

		public NullResource(String description) {
			this.description = description;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public File getFile() throws IOException {
			return null;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return null;
		}
	}

}
