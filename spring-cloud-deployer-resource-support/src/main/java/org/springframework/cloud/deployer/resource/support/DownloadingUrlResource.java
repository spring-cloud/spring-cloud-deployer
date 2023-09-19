/*
 * Copyright 2017 the original author or authors.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileCopyUtils;

/**
 * A {@link Resource} implementation that will download a {@link UrlResource} to a temp file when
 * the {@link DownloadingUrlResource#getFile()} is invoked.
 *
 * @author Ilayaperumal Gopinathan
 * @author Mark Pollack
 */
public class DownloadingUrlResource extends UrlResource {

	private static final Logger logger = LoggerFactory.getLogger(DownloadingUrlResource.class);

	private File file;

	/**
	 * Create a new {@code DownloadingUrlResource} based on the given URI object.
	 * @param uri a URI
	 * @throws MalformedURLException if the given URL path is not valid
	 */
	public DownloadingUrlResource(String uri) throws MalformedURLException {
		super(uri);
	}


	/**
	 * Downloads the file from the HTTP location to a temporary file.
	 * The temporary file uses the directory prefix "spring-cloud-deployer" and the filename is
	 * the SHA1 hash of the URL.  The file will only be downloaded on the first invocation
	 * of this method.
	 * @return The downloaded file.
	 * @throws IOException if there are errors downloading or writing the temporary file.
	 */
	@Override
	public synchronized File getFile() throws IOException {
		if (file == null) {
			// Create a well formatted filename, no dashes, slashes, etc from the URL
			String simpleName = null;
			try {
				Path path = Paths.get(getURL().toURI().getPath());
				simpleName = path.getFileName().toString().replaceAll("[^\\p{IsAlphabetic}^\\p{IsDigit}]", "");
			} catch (URISyntaxException e) {
				logger.info("Could not create simple name from last part of URL", e.getMessage());
			}
			String fileName = ShaUtils.sha1(getURL().toString());
			if (simpleName != null) {
				try {
					this.file = new File(Files.createTempDirectory("spring-cloud-deployer").toFile(),
							fileName + "-" + simpleName);
				} catch (IOException e) {
					logger.info("Could not create simple temp file name using last part of URL");
				}
			}
			if (file == null) {
				this.file = new File(Files.createTempDirectory("spring-cloud-deployer").toFile(), fileName);
			}
			// Get the input stream for the URLResource
			logger.info("Downloading [" + getURL().toString() + "] to " + this.file.getAbsolutePath());
			FileCopyUtils.copy(this.getInputStream(), new FileOutputStream(file));
		}
		return file;
	}

	@Override
	public synchronized String getDescription() {
		StringBuffer sb = new StringBuffer();
		sb.append("URL [" + getURL() + "]");
		if (file != null) {
			sb.append(", file [" + this.file.getAbsolutePath() + "]");
		}
		return sb.toString();
	}
}
