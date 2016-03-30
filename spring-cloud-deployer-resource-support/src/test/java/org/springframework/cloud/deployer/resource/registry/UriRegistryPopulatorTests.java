package org.springframework.cloud.deployer.resource.registry;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;

import org.springframework.cloud.deployer.resource.StubResourceLoader;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

/**
 * @author Patrick Peralta
 */
public class UriRegistryPopulatorTests {

	private final Properties uris;

	public UriRegistryPopulatorTests() {
		this.uris = new Properties();
		this.uris.setProperty("source.file", "maven://org.springframework.cloud.stream.module:file-source:jar:exec:1.0.0");
		this.uris.setProperty("source.ftp", "maven://org.springframework.cloud.stream.module:ftp-source:jar:exec:1.0.0");
		this.uris.setProperty("source.jdbc", "maven://org.springframework.cloud.stream.module:jdbc-source:jar:exec:1.0.0");
	}

	@Test
	public void populateRegistry() throws Exception {
		String localUri = "local://local";
		UriRegistryPopulator populator = new UriRegistryPopulator(new String[] { localUri });
		StubResourceLoader resourceLoader = new StubResourceLoader(new PropertiesResource(uris));
		populator.setResourceLoader(resourceLoader);

		UriRegistry registry = new InMemoryUriRegistry();
		populator.populateRegistry(registry);
		assertTrue(resourceLoader.getRequestedLocations().contains(localUri));
		assertThat(resourceLoader.getRequestedLocations().size(), is(1));
		assertThat(registry.findAll().size(), is(this.uris.size()));
		for (String key : this.uris.stringPropertyNames()) {
			assertThat(registry.find(key).toString(), is(this.uris.getProperty(key)));
		}

		boolean thrown = false;
		try {
			registry.find("not present");
		}
		catch (IllegalArgumentException e) {
			thrown = true;
		}
		finally {
			assertTrue(thrown);
		}
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