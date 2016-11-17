package org.springframework.cloud.deployer.spi.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * Allows parsing and display of common byte sizes, using units such a MB and GB.
 *
 * @author Eric Bottard
 */
public class ByteSize {

	private final long amount;

	private final Unit unit;

	private static final Pattern PARSE_REGEX = Pattern.compile("(?<amount>\\d+)(?<unit>\\p{Alpha}*)");

	public ByteSize(long amount, Unit unit) {
		if (amount < 0) {
			throw new IllegalArgumentException("Size cannot be negative");
		}
		if (unit == null) {
			throw new IllegalArgumentException("Unit cannot be null");
		}
		this.amount = amount;
		this.unit = unit;
	}

	/**
	 * Attempt to parse a text representation of a bytes size, ignoring case and using everyday abbreviations in place
	 * of the correct ones (ie KB is considered as 1024 bytes instead of 1000 bytes). Lack of unit means the amount is
	 * interpreted as direct number of bytes.
	 */
	public static ByteSize parse(String text) {
		return parse(text, true, true);
	}

	/**
	 * Attempt to parse a text representation of a bytes size, with full control over parsing.
	 * @param text the text to parse, must not be null
	 * @param ignoreCase whether to ignore case when dealing with units
	 * @param decimalInPlaceOfBinary if true, units like 'MB' are used to denote power of two instead of the more correct 'MiB'
	 */
	public static ByteSize parse(String text, boolean ignoreCase, boolean decimalInPlaceOfBinary) {
		Assert.notNull(text, "text to parse must not be null");
		Matcher matcher = PARSE_REGEX.matcher(text);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(String.format("Could not parse '%s' to a byte size: not a number", text));
		}
		long amount = Long.parseLong(matcher.group("amount"));
		String suffix = matcher.group("unit");
		Optional<Unit> optinalUnit = Arrays.stream(Unit.values())
			.filter(u -> Arrays.stream(u.suffixes).anyMatch(suffixMatchingPredicate(suffix, ignoreCase)))
			.findFirst();

		Unit unit = optinalUnit.orElseThrow(() -> new IllegalArgumentException(String.format("Could not parse '%s' to a byte size. A valid unit must be specified among [%s].",
				text, Arrays.stream(Unit.values()).flatMap(u -> Arrays.stream(u.suffixes)).collect(Collectors.joining(", ")))));
		if (decimalInPlaceOfBinary) {
			unit = unit.convertFromCommonNotation();
		}
		return new ByteSize(amount, unit);
	}

	private static Predicate<String> suffixMatchingPredicate(String suffix, boolean ignoreCase) {
		if (ignoreCase) {
			return s -> s.equalsIgnoreCase(suffix);
		}
		else {
			return s -> s.equals(suffix);
		}
	}

	/**
	 * Return the integral amount this quantity represents, when expressed in the target unit.
	 */
	public long sizeIn(Unit target) {
		return amount * unit.multiplier / target.multiplier;
	}

	/**
	 * Return a text representation of this quantity expressed in the target unit, uning integral numbers only and no
	 * unit suffix
	 */
	public String format(Unit targetUnit) {
		return format(new DecimalFormat("#####################"), targetUnit, false);
	}

	/**
	 * Return a text representation of this quantity expressed in the target unit, with full control over the number format.
	 * @param nf a number format to use (may allow decimal digits to appear)
	 * @param targetUnit the unit in which to express this quantity
	 * @param omitUnit whether to omit the target unit suffix in the text representation
	 */
	public String format(NumberFormat nf, Unit targetUnit, boolean omitUnit) {
		return nf.format((double) amount * unit.multiplier / targetUnit.multiplier) + (omitUnit ? "" : targetUnit.suffix());
	}

	@Override
	public String toString() {
		return amount + unit.suffix();
	}

	enum Unit {
		one(1L, "B", ""),
		// These should always be kept in pairs of 1000 and 1024 multipliers
		// for the current implementation of convertFromCommonNotation() to work
		kilo(1000L, "kB", "k"),
		mega(1000L * 1000L, "MB", "M"),
		giga(1000L * 1000L * 1000L, "GB", "G"),
		tera(1000L * 1000L * 1000L * 1000L, "TB"),
		peta(1000L * 1000L * 1000L * 1000L * 1000L, "PB"),
		kibi(1024L, "KiB"),
		mebi(1024L * 1024L, "MiB"),
		gebi(1024L * 1024L * 1024L, "GiB"),
		tebi(1024L * 1024L * 1024L * 1024L, "TiB"),
		pebi(1024L * 1024L * 1024L * 1024L * 1024L, "PiB");

		private long multiplier;

		private String[] suffixes;

		Unit(long multiplier, String... suffixes) {
			this.multiplier = multiplier;
			this.suffixes = suffixes;
		}

		String suffix() {
			return suffixes[0];
		}

		Unit convertFromCommonNotation() {
			if (kilo.ordinal() <= this.ordinal() && this.ordinal() < kibi.ordinal()) {
				return Unit.values()[this.ordinal() + kibi.ordinal() - kilo.ordinal()];
			}
			else {
				return this;
			}
		}
	}
}
