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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

import org.springframework.cloud.deployer.resource.StubResourceLoader;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Patrick Peralta
 * @author Ilayaperumal Gopinathan
 */
public class UriRegistryPopulatorTests {

	private final Properties uris;

	public UriRegistryPopulatorTests() {
		this.uris = new Properties();
		this.uris.setProperty("foo.1", "maven://group1:foo:jar:classifier1:1.0.1");
		this.uris.setProperty("foo-2", "maven://group2:foo:2.1.7");
		this.uris.setProperty("bar", "file:///bar-1.2.3.jar");
	}

	@Test
	public void populateRegistry() throws Exception {
		String localUri = "local://local";
		UriRegistryPopulator populator = new UriRegistryPopulator();
		StubResourceLoader resourceLoader = new StubResourceLoader(new PropertiesResource(uris));
		populator.setResourceLoader(resourceLoader);

		UriRegistry registry = new InMemoryUriRegistry();
		populator.populateRegistry(true, registry, localUri);
		assertThat(resourceLoader.getRequestedLocations().contains(localUri)).isTrue();
		assertThat(resourceLoader.getRequestedLocations().size()).isEqualTo(1);
		assertThat(registry.findAll().size()).isEqualTo(this.uris.size());
		for (String key : this.uris.stringPropertyNames()) {
			assertThat(registry.find(key).toString()).isEqualTo(this.uris.getProperty(key));
		}

		boolean thrown = false;
		try {
			registry.find("not present");
		}
		catch (IllegalArgumentException e) {
			thrown = true;
		}
		finally {
			assertThat(thrown).isTrue();
		}
	}

	@Test
	public void populateRegistryWithOverwrites() throws Exception {
		String localUri = "local://local";
		UriRegistryPopulator populator = new UriRegistryPopulator();
		PropertiesResource propertiesResource = new PropertiesResource(uris);
		StubResourceLoader resourceLoader = new StubResourceLoader(propertiesResource);
		populator.setResourceLoader(resourceLoader);
		UriRegistry registry = new InMemoryUriRegistry();
		Map<String, URI> registered = populator.populateRegistry(true, registry, localUri);
		assertThat(registered.size() == 3).isTrue();
		// Perform overwrites on the existing keys
		Map<String, URI> registeredWithNoOverwrites = populator.populateRegistry(false, registry, localUri);
		assertThat(registeredWithNoOverwrites.size() == 0).isTrue();
		propertiesResource.addNewProperty("another", "maven://somegroup:someartifact:jar:exec:1.0.0");
		Map<String, URI> newlyRegisteredWithNoOverwrites = populator.populateRegistry(false, registry, localUri);
		assertThat(newlyRegisteredWithNoOverwrites.size() == 1).isTrue();
		propertiesResource.addNewProperty("yet-another", "file:///tmp/yet-another.jar");
		Map<String, URI> newlyRegisteredWithOverwrites = populator.populateRegistry(true, registry, localUri);
		assertThat(newlyRegisteredWithOverwrites.size() == 5).isTrue();
	}

	@Test
	public void populateRegistryInvalidUri() throws Exception {
		String localUri = "local://local";
		Properties props = new Properties();
		props.setProperty("test", "file:///bar-1.2.3.jar");
		UriRegistryPopulator populator = new UriRegistryPopulator();
		StubResourceLoader resourceLoader = new StubResourceLoader(new PropertiesResource(props));
		populator.setResourceLoader(resourceLoader);
		UriRegistry registry = new InMemoryUriRegistry();
		populator.populateRegistry(true, registry, localUri);
		assertThat(resourceLoader.getRequestedLocations().contains(localUri)).isTrue();
		assertThat(resourceLoader.getRequestedLocations().size()).isEqualTo(1);
		assertThat(registry.findAll().size()).isEqualTo(1);
		assertThat(registry.find("test").toString()).isEqualTo("file:///bar-1.2.3.jar");
		populator.populateRegistry(true, registry, localUri);
		props.setProperty("test", "invalid");
		populator.populateRegistry(true, registry, localUri);
		assertThat(registry.find("test").toString()).isEqualTo("file:///bar-1.2.3.jar");
	}


	/**
	 * {@link Resource} implementation that returns an {@link InputStream}
	 * fed by a {@link Properties} object.
	 */
	static class PropertiesResource extends AbstractResource {

		private final Properties properties;

		public PropertiesResource(Properties properties) {
			this.properties = properties;
		}

		public Properties addNewProperty(String key, String value) {
			if (this.properties != null) {
				this.properties.put(key, value);
			}
			return this.properties;
		}

		@Override
		public String getDescription() {
			return null;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.properties.store(out, "URIs");
			return new ByteArrayInputStream(out.toByteArray());
		}
	}

}
