package org.springframework.cloud.deployer.resource.support;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

/**
 * Unit tests for {@link LRUResourceLoader}.
 *
 * @author Eric Bottard
 */
public class LRUResourceLoaderTests {

	@Test
	public void testFilesGetDeletedWhenLimitReached() throws Exception {
		LRUResourceLoader loader = new LRUResourceLoader(new TemporaryFileResourceLoader(), 1000);
		Resource resource1 = loader.getResource("foo:150");
		assertThat(resource1.getFile().exists(), is(true));

		Resource resource2 = loader.getResource("bar:150");
		assertThat(resource1.getFile().exists(), is(true));
		assertThat(resource2.getFile().exists(), is(true));

		Resource resource3 = loader.getResource("wizz:300");
		assertThat(resource1.getFile().exists(), is(true));
		assertThat(resource2.getFile().exists(), is(true));
		assertThat(resource3.getFile().exists(), is(true));

		Resource resource4 = loader.getResource("quxx:600");
		assertThat(resource1.getFile().exists(), is(false));
		assertThat(resource2.getFile().exists(), is(false));
		assertThat(resource3.getFile().exists(), is(true));
		assertThat(resource4.getFile().exists(), is(true));
	}

	@Test
	public void testLRUFilesGetDeletedWhenLimitReached() throws Exception {
		LRUResourceLoader loader = new LRUResourceLoader(new TemporaryFileResourceLoader(), 1000);
		Resource resource1 = loader.getResource("foo:150");
		assertThat(resource1.getFile().exists(), is(true));

		Resource resource2 = loader.getResource("bar:150");
		assertThat(resource1.getFile().exists(), is(true));
		assertThat(resource2.getFile().exists(), is(true));

		Resource resource3 = loader.getResource("wizz:300");
		assertThat(resource1.getFile().exists(), is(true));
		assertThat(resource2.getFile().exists(), is(true));
		assertThat(resource3.getFile().exists(), is(true));

		loader.getResource("foo:150"); // Force access on foo

		Resource resource4 = loader.getResource("quxx:600");
		assertThat(resource1.getFile().exists(), is(true));
		assertThat(resource2.getFile().exists(), is(false));
		assertThat(resource3.getFile().exists(), is(false));
		assertThat(resource4.getFile().exists(), is(true));
	}
}

/**
 * A resource loader used in the tests above. Resolves locations in the form {@literal subpath:size} by creating a
 * temporary file that is "size" bytes in size.
 *
 * <p>All files are scheduled for deletion at JVM shutdown.</p>
 *
 * @author Eric Bottard
 */
class TemporaryFileResourceLoader implements ResourceLoader {

	private Map<String, Resource> created = new HashMap<>();

	@Override
	public Resource getResource(String location) {
		Resource alreadyCreated = created.get(location);
		if (alreadyCreated != null) {
			return alreadyCreated;
		}

		int colon = location.indexOf(':');
		String path = location.substring(0, colon);
		int size = Integer.parseInt(location.substring(colon + 1));
		try {
			File file = File.createTempFile(path, ".tmp");
			file.deleteOnExit();
			ByteArrayInputStream bis = new ByteArrayInputStream(new byte[size]);
			FileCopyUtils.copy(bis, new FileOutputStream(file));
			FileSystemResource result = new FileSystemResource(file);
			created.put(location, result);
			return result;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return TemporaryFileResourceLoader.class.getClassLoader();
	}

}
