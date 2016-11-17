package org.springframework.cloud.deployer.spi.util;

import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

/**
 * Unit tests for {@link ByteSizeUtils}.
 *
 * @author Eric Bottard
 */
public class ByteSizeUtilsTests {

	@Test
	public void testParse() {
		assertThat(ByteSizeUtils.parseToMebibytes("1"), CoreMatchers.is(1L));
		assertThat(ByteSizeUtils.parseToMebibytes("2m"), CoreMatchers.is(2L));
		assertThat(ByteSizeUtils.parseToMebibytes("20M"), CoreMatchers.is(20L));
		assertThat(ByteSizeUtils.parseToMebibytes("1000g"), CoreMatchers.is(1024_000L));
		assertThat(ByteSizeUtils.parseToMebibytes("1G"), CoreMatchers.is(1024L));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNotANumber() {
		ByteSizeUtils.parseToMebibytes("wat?124");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUnsupportedUnit() {
		ByteSizeUtils.parseToMebibytes("1PB");
	}

}
