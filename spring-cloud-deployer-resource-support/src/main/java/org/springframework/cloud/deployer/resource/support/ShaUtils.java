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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Simple sha utils.
 *
 * @author Janne Valkealahti
 */
public abstract class ShaUtils {

	private static final char[] CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 * Creates a sha1 out from a given data.
	 *
	 * @param data the data
	 * @return the sha1 for data
	 */
	protected static String sha1(String data) {
		try {
			return new String(encodeHex(MessageDigest.getInstance("SHA-1").digest(data.getBytes("UTF-8"))));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Encode given data as lower case hex chars.
	 *
	 * @param data the data
	 * @return the endoced chars
	 */
	private static char[] encodeHex(final byte[] data) {
		final int len = data.length;
		final char[] out = new char[len << 1];
		for (int i = 0, j = 0; i < len; i++) {
			out[j++] = CHARS[(0xF0 & data[i]) >>> 4];
			out[j++] = CHARS[0x0F & data[i]];
		}
		return out;
	}
}
