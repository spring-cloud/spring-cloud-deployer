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
