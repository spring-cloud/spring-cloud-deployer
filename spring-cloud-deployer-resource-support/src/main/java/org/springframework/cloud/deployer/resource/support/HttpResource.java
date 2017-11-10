/*
 * Copyright 2017 the original author or authors.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * A {@link Resource} implementation for resolving HTTP resource.
 *
 * @author Ilayaperumal Gopinathan
 */
public class HttpResource extends UrlResource {

	public HttpResource(String location) throws MalformedURLException {
		super(location);
	}

	@Override
	public String getDescription() {
		try {
			return "Http Resource [" + this.getURL().getPath() + "]";
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public File getFile() throws IOException {
		String fileName = ShaUtils.sha1(this.getURL().getPath());
		File tempFile = new File(Files.createTempDirectory("spring-cloud-deployer").toFile(), fileName);
		FileCopyUtils.copy(this.getInputStream(), new FileOutputStream(tempFile));
		tempFile.deleteOnExit();
		return tempFile;
	}

}
