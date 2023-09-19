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
package org.springframework.cloud.deployer.spi.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for dealing with parseable byte sizes, such as memory and disk limits.
 *
 * @author Eric Bottard
 */
public class ByteSizeUtils {

	private ByteSizeUtils() {

	}

	private static final Pattern SIZE_PATTERN = Pattern.compile("(?<amount>\\d+)(?<unit>(m|g)?)", Pattern.CASE_INSENSITIVE);

	/**
	 * Return the number of mebibytes (1024*1024) denoted by the given text, where an optional case-insensitive unit of
	 * 'm' or 'g' can be used to mean mebi- or gebi- bytes, respectively. Lack of unit assumes mebibytes.
	 * @param text The text to parse
	 * @return mebibytes
	 */
	public  static long parseToMebibytes(String text) {
		Matcher matcher = SIZE_PATTERN.matcher(text);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(String.format("Could not parse '%s' as a byte size." +
				" Expected a number with optional 'm' or 'g' suffix", text));
		}
		long size = Long.parseLong(matcher.group("amount"));
		if (matcher.group("unit").equalsIgnoreCase("g")) {
			size *= 1024L;
		}
		return size;
	}
}
