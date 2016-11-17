package org.springframework.cloud.deployer.spi.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.text.DecimalFormat;

import org.junit.Test;

/**
 * Unit tests for {@link ByteSize}
 */
public class ByteSizeTests {

	@Test
	public void testParsing() {
		assertThat(ByteSize.parse("1234").sizeIn(ByteSize.Unit.one), is(1234L));
		assertThat(ByteSize.parse("1234kB").sizeIn(ByteSize.Unit.one), is(1234L * 1024L));
		assertThat(ByteSize.parse("1234mb").sizeIn(ByteSize.Unit.kibi), is(1234L * 1024L));
		assertThat(ByteSize.parse("1234mb", true, false).sizeIn(ByteSize.Unit.one), is(1234L * 1000L * 1000L));
		assertThat(ByteSize.parse("1234GiB").sizeIn(ByteSize.Unit.one), is(1234L * 1024 * 1024 *1024));
	}

	@Test
	public void testFormat() {
		ByteSize size = new ByteSize(1_234_567_890, ByteSize.Unit.one);
		assertThat(size.format(ByteSize.Unit.one), is("1234567890B"));
		assertThat(size.format(ByteSize.Unit.kilo), is("1234568kB")); // rounding
		assertThat(size.format(ByteSize.Unit.peta), is("0PB"));

		assertThat(size.format(new DecimalFormat("#.######"), ByteSize.Unit.giga, false), is("1.234568GB"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUnparseableUnit() {
		ByteSize.parse("1234u");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUnparseableNumber() {
		ByteSize.parse("wat?1234");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParsingRespectingCase() {
		ByteSize.parse("1234mb", false, false);
	}

}
