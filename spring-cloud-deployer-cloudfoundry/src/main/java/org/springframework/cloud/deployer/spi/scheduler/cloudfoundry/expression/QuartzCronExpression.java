/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.deployer.spi.scheduler.cloudfoundry.expression;

import java.text.ParseException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Provides a parser and evaluator for unix-like cron expressions. Cron
 * expressions provide the ability to specify complex time combinations such as
 * &quot;At 8:00am every Monday through Friday&quot; or &quot;At 1:30am every
 * last Friday of the month&quot;.
 * <P>
 * Cron expressions are comprised of 6 required fields and one optional field
 * separated by white space.
 *
 * Based on the CronExpression from Quartz.
 *
 * @author Glenn Renfro
 */
public final class QuartzCronExpression {

	private static final int SECOND = 0;
	private static final int MINUTE = 1;
	private static final int HOUR = 2;
	private static final int DAY_OF_MONTH = 3;
	private static final int MONTH = 4;
	private static final int DAY_OF_WEEK = 5;
	private static final int YEAR = 6;
	private static final int ALL_SPEC_INT = 99; // '*'
	private static final int NO_SPEC_INT = 98; // '?'
	private static final Integer ALL_SPEC = ALL_SPEC_INT;
	private static final Integer NO_SPEC = NO_SPEC_INT;

	protected static final Map<String, Integer> monthMap = new HashMap<String, Integer>(20);
	protected static final Map<String, Integer> dayMap = new HashMap<String, Integer>(60);
	static {
		monthMap.put("JAN", 0);
		monthMap.put("FEB", 1);
		monthMap.put("MAR", 2);
		monthMap.put("APR", 3);
		monthMap.put("MAY", 4);
		monthMap.put("JUN", 5);
		monthMap.put("JUL", 6);
		monthMap.put("AUG", 7);
		monthMap.put("SEP", 8);
		monthMap.put("OCT", 9);
		monthMap.put("NOV", 10);
		monthMap.put("DEC", 11);

		dayMap.put("SUN", 1);
		dayMap.put("MON", 2);
		dayMap.put("TUE", 3);
		dayMap.put("WED", 4);
		dayMap.put("THU", 5);
		dayMap.put("FRI", 6);
		dayMap.put("SAT", 7);
	}

	private final String cronExpression;
	private TreeSet<Integer> seconds;
	private TreeSet<Integer> minutes;
	private TreeSet<Integer> hours;
	private TreeSet<Integer> daysOfMonth;
	private TreeSet<Integer> months;
	private TreeSet<Integer> daysOfWeek;
	private TreeSet<Integer> years;

	private int nthdayOfWeek = 0;
	private boolean lastdayOfMonth = false;
	private int lastdayOffset = 0;

	public static final int MAX_YEAR = Calendar.getInstance().get(Calendar.YEAR) + 100;

	/**
	 * Constructs a new {@link QuartzCronExpression} based on the specified
	 * parameter.
	 *
	 * @param cronExpression String representation of the cron expression the
	 *                       new object should represent
	 * @throws ParseException
	 *         if the string expression cannot be parsed into a valid
	 *         {@link QuartzCronExpression}
	 */
	public QuartzCronExpression(String cronExpression) throws ParseException {
		if (cronExpression == null) {
			throw new IllegalArgumentException("cronExpression cannot be null");
		}

		this.cronExpression = cronExpression.toUpperCase(Locale.US);

		buildExpression(this.cronExpression);
	}

	/**
	 * Returns the string representation of the {@link QuartzCronExpression}
	 *
	 * @return a string representation of the {@link QuartzCronExpression}
	 */
	@Override
	public String toString() {
		return cronExpression;
	}


	////////////////////////////////////////////////////////////////////////////
	//
	// Expression Parsing Functions
	//
	////////////////////////////////////////////////////////////////////////////

	protected void buildExpression(String expression) throws ParseException {

		try {

			if (seconds == null) {
				seconds = new TreeSet<>();
			}
			if (minutes == null) {
				minutes = new TreeSet<>();
			}
			if (hours == null) {
				hours = new TreeSet<>();
			}
			if (daysOfMonth == null) {
				daysOfMonth = new TreeSet<>();
			}
			if (months == null) {
				months = new TreeSet<>();
			}
			if (daysOfWeek == null) {
				daysOfWeek = new TreeSet<>();
			}
			if (years == null) {
				years = new TreeSet<>();
			}

			int exprOn = SECOND;

			StringTokenizer exprsTok = new StringTokenizer(expression, " \t",
					false);

			while (exprsTok.hasMoreTokens() && exprOn <= YEAR) {
				String expr = exprsTok.nextToken().trim();

				// throw an exception if L is used with other days of the month
				if(exprOn == DAY_OF_MONTH && expr.indexOf('L') != -1 && expr.length() > 1 && expr.contains(",")) {
					throw new ParseException("Support for specifying 'L' and 'LW' with other days of the month is not implemented", -1);
				}
				// throw an exception if L is used with other days of the week
				if(exprOn == DAY_OF_WEEK && expr.indexOf('L') != -1 && expr.length() > 1  && expr.contains(",")) {
					throw new ParseException("Support for specifying 'L' with other days of the week is not implemented", -1);
				}
				if(exprOn == DAY_OF_WEEK && expr.indexOf('#') != -1 && expr.indexOf('#', expr.indexOf('#') +1) != -1) {
					throw new ParseException("Support for specifying multiple \"nth\" days is not implemented.", -1);
				}

				StringTokenizer vTok = new StringTokenizer(expr, ",");
				while (vTok.hasMoreTokens()) {
					String v = vTok.nextToken();
					storeExpressionVals(0, v, exprOn);
				}

				exprOn++;
			}

			if (exprOn <= DAY_OF_WEEK) {
				throw new ParseException("Unexpected end of expression.",
						expression.length());
			}

			if (exprOn <= YEAR) {
				storeExpressionVals(0, "*", YEAR);
			}

			TreeSet<Integer> dow = getSet(DAY_OF_WEEK);
			TreeSet<Integer> dom = getSet(DAY_OF_MONTH);

			// Copying the logic from the UnsupportedOperationException below
			boolean dayOfMSpec = !dom.contains(NO_SPEC);
			boolean dayOfWSpec = !dow.contains(NO_SPEC);

			if (!dayOfMSpec || dayOfWSpec) {
				if (!dayOfWSpec || dayOfMSpec) {
					throw new ParseException(
							"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.", 0);
				}
			}
		} catch (ParseException pe) {
			throw pe;
		} catch (Exception e) {
			throw new ParseException("Illegal cron expression format ("
					+ e.toString() + ")", 0);
		}
	}

	private void checkIncrementRange(int incr, int type, int idxPos) throws ParseException {
		if (incr > 59 && (type == SECOND || type == MINUTE)) {
			throw new ParseException("Increment > 60 : " + incr, idxPos);
		} else if (incr > 23 && (type == HOUR)) {
			throw new ParseException("Increment > 24 : " + incr, idxPos);
		} else if (incr > 31 && (type == DAY_OF_MONTH)) {
			throw new ParseException("Increment > 31 : " + incr, idxPos);
		} else if (incr > 7 && (type == DAY_OF_WEEK)) {
			throw new ParseException("Increment > 7 : " + incr, idxPos);
		} else if (incr > 12 && (type == MONTH)) {
			throw new ParseException("Increment > 12 : " + incr, idxPos);
		}
	}

	protected int checkNext(int pos, String s, int val, int type)
			throws ParseException {

		int end = -1;
		int i = pos;

		if (i >= s.length()) {
			addToSet(val, end, -1, type);
			return i;
		}

		char c = s.charAt(pos);

		if (c == 'L') {
			if (type == DAY_OF_WEEK) {
				if(val < 1 || val > 7)
					throw new ParseException("Day-of-Week values must be between 1 and 7", -1);
			} else {
				throw new ParseException("'L' option is not valid here. (pos=" + i + ")", i);
			}
			TreeSet<Integer> set = getSet(type);
			set.add(val);
			i++;
			return i;
		}

		if (c == 'W') {
			if (type != DAY_OF_MONTH) {
				throw new ParseException("'W' option is not valid here. (pos=" + i + ")", i);
			}
			if(val > 31)
				throw new ParseException("The 'W' option does not make sense with values larger than 31 (max number of days in a month)", i);
			TreeSet<Integer> set = getSet(type);
			set.add(val);
			i++;
			return i;
		}

		if (c == '#') {
			if (type != DAY_OF_WEEK) {
				throw new ParseException("'#' option is not valid here. (pos=" + i + ")", i);
			}
			i++;
			try {
				nthdayOfWeek = Integer.parseInt(s.substring(i));
				if (nthdayOfWeek < 1 || nthdayOfWeek > 5) {
					throw new Exception();
				}
			} catch (Exception e) {
				throw new ParseException(
						"A numeric value between 1 and 5 must follow the '#' option",
						i);
			}

			TreeSet<Integer> set = getSet(type);
			set.add(val);
			i++;
			return i;
		}

		if (c == '-') {
			i++;
			c = s.charAt(i);
			int v = Integer.parseInt(String.valueOf(c));
			end = v;
			i++;
			if (i >= s.length()) {
				addToSet(val, end, 1, type);
				return i;
			}
			c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				ValueSet vs = getValue(v, s, i);
				end = vs.value;
				i = vs.pos;
			}
			if (i < s.length() && ((c = s.charAt(i)) == '/')) {
				i++;
				c = s.charAt(i);
				int v2 = Integer.parseInt(String.valueOf(c));
				i++;
				if (i >= s.length()) {
					addToSet(val, end, v2, type);
					return i;
				}
				c = s.charAt(i);
				if (c >= '0' && c <= '9') {
					ValueSet vs = getValue(v2, s, i);
					int v3 = vs.value;
					addToSet(val, end, v3, type);
					i = vs.pos;
					return i;
				} else {
					addToSet(val, end, v2, type);
					return i;
				}
			} else {
				addToSet(val, end, 1, type);
				return i;
			}
		}

		if (c == '/') {
			if ((i + 1) >= s.length() || s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t') {
				throw new ParseException("'/' must be followed by an integer.", i);
			}

			i++;
			c = s.charAt(i);
			int v2 = Integer.parseInt(String.valueOf(c));
			i++;
			if (i >= s.length()) {
				checkIncrementRange(v2, type, i);
				addToSet(val, end, v2, type);
				return i;
			}
			c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				ValueSet vs = getValue(v2, s, i);
				int v3 = vs.value;
				checkIncrementRange(v3, type, i);
				addToSet(val, end, v3, type);
				i = vs.pos;
				return i;
			} else {
				throw new ParseException("Unexpected character '" + c + "' after '/'", i);
			}
		}

		addToSet(val, end, 0, type);
		i++;
		return i;
	}


	protected int skipWhiteSpace(int i, String s) {
		for (; i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'); i++) {
			;
		}

		return i;
	}

	protected int findNextWhiteSpace(int i, String s) {
		for (; i < s.length() && (s.charAt(i) != ' ' || s.charAt(i) != '\t'); i++) {
			;
		}

		return i;
	}

	protected void addToSet(int val, int end, int incr, int type)
			throws ParseException {

		TreeSet<Integer> set = getSet(type);

		if (type == SECOND || type == MINUTE) {
			if ((val < 0 || val > 59 || end > 59) && (val != ALL_SPEC_INT)) {
				throw new ParseException(
						"Minute and Second values must be between 0 and 59",
						-1);
			}
		} else if (type == HOUR) {
			if ((val < 0 || val > 23 || end > 23) && (val != ALL_SPEC_INT)) {
				throw new ParseException(
						"Hour values must be between 0 and 23", -1);
			}
		} else if (type == DAY_OF_MONTH) {
			if ((val < 1 || val > 31 || end > 31) && (val != ALL_SPEC_INT)
					&& (val != NO_SPEC_INT)) {
				throw new ParseException(
						"Day of month values must be between 1 and 31", -1);
			}
		} else if (type == MONTH) {
			if ((val < 1 || val > 12 || end > 12) && (val != ALL_SPEC_INT)) {
				throw new ParseException(
						"Month values must be between 1 and 12", -1);
			}
		} else if (type == DAY_OF_WEEK) {
			if ((val == 0 || val > 7 || end > 7) && (val != ALL_SPEC_INT)
					&& (val != NO_SPEC_INT)) {
				throw new ParseException(
						"Day-of-Week values must be between 1 and 7", -1);
			}
		}

		if ((incr == 0 || incr == -1) && val != ALL_SPEC_INT) {
			if (val != -1) {
				set.add(val);
			} else {
				set.add(NO_SPEC);
			}

			return;
		}

		int startAt = val;
		int stopAt = end;

		if (val == ALL_SPEC_INT && incr <= 0) {
			incr = 1;
			set.add(ALL_SPEC); // put in a marker, but also fill values
		}

		if (type == SECOND || type == MINUTE) {
			if (stopAt == -1) {
				stopAt = 59;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 0;
			}
		} else if (type == HOUR) {
			if (stopAt == -1) {
				stopAt = 23;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 0;
			}
		} else if (type == DAY_OF_MONTH) {
			if (stopAt == -1) {
				stopAt = 31;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 1;
			}
		} else if (type == MONTH) {
			if (stopAt == -1) {
				stopAt = 12;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 1;
			}
		} else if (type == DAY_OF_WEEK) {
			if (stopAt == -1) {
				stopAt = 7;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 1;
			}
		} else if (type == YEAR) {
			if (stopAt == -1) {
				stopAt = MAX_YEAR;
			}
			if (startAt == -1 || startAt == ALL_SPEC_INT) {
				startAt = 1970;
			}
		}

		// if the end of the range is before the start, then we need to overflow into
		// the next day, month etc. This is done by adding the maximum amount for that
		// type, and using modulus max to determine the value being added.
		int max = -1;
		if (stopAt < startAt) {
			switch (type) {
				case SECOND:
					max = 60;
					break;
				case MINUTE:
					max = 60;
					break;
				case HOUR:
					max = 24;
					break;
				case MONTH:
					max = 12;
					break;
				case DAY_OF_WEEK:
					max = 7;
					break;
				case DAY_OF_MONTH:
					max = 31;
					break;
				case YEAR:
					throw new IllegalArgumentException("Start year must be less than stop year");
				default:
					throw new IllegalArgumentException("Unexpected type encountered");
			}
			stopAt += max;
		}

		for (int i = startAt; i <= stopAt; i += incr) {
			if (max == -1) {
				// ie: there's no max to overflow over
				set.add(i);
			} else {
				// take the modulus to get the real value
				int i2 = i % max;

				// 1-indexed ranges should not include 0, and should include their max
				if (i2 == 0 && (type == MONTH || type == DAY_OF_WEEK || type == DAY_OF_MONTH) ) {
					i2 = max;
				}

				set.add(i2);
			}
		}
	}

	TreeSet<Integer> getSet(int type) {
		switch (type) {
			case SECOND:
				return seconds;
			case MINUTE:
				return minutes;
			case HOUR:
				return hours;
			case DAY_OF_MONTH:
				return daysOfMonth;
			case MONTH:
				return months;
			case DAY_OF_WEEK:
				return daysOfWeek;
			case YEAR:
				return years;
			default:
				return null;
		}
	}

	protected ValueSet getValue(int v, String s, int i) {
		char c = s.charAt(i);
		StringBuilder s1 = new StringBuilder(String.valueOf(v));
		while (c >= '0' && c <= '9') {
			s1.append(c);
			i++;
			if (i >= s.length()) {
				break;
			}
			c = s.charAt(i);
		}
		ValueSet val = new ValueSet();

		val.pos = (i < s.length()) ? i : i + 1;
		val.value = Integer.parseInt(s1.toString());
		return val;
	}

	protected int getNumericValue(String s, int i) {
		int endOfVal = findNextWhiteSpace(i, s);
		String val = s.substring(i, endOfVal);
		return Integer.parseInt(val);
	}

	protected int getMonthNumber(String s) {
		Integer integer = monthMap.get(s);

		if (integer == null) {
			return -1;
		}

		return integer;
	}

	protected int getDayOfWeekNumber(String s) {
		Integer integer = dayMap.get(s);

		if (integer == null) {
			return -1;
		}

		return integer;
	}


	protected int storeExpressionVals(int pos, String s, int type)
			throws ParseException {

		int incr = 0;
		int i = skipWhiteSpace(pos, s);
		if (i >= s.length()) {
			return i;
		}
		char c = s.charAt(i);
		if ((c >= 'A') && (c <= 'Z') && (!s.equals("L")) && (!s.equals("LW")) && (!s.matches("^L-[0-9]*[W]?"))) {
			String sub = s.substring(i, i + 3);
			int sval = -1;
			int eval = -1;
			if (type == MONTH) {
				sval = getMonthNumber(sub) + 1;
				if (sval <= 0) {
					throw new ParseException("Invalid Month value: '" + sub + "'", i);
				}
				if (s.length() > i + 3) {
					c = s.charAt(i + 3);
					if (c == '-') {
						i += 4;
						sub = s.substring(i, i + 3);
						eval = getMonthNumber(sub) + 1;
						if (eval <= 0) {
							throw new ParseException("Invalid Month value: '" + sub + "'", i);
						}
					}
				}
			} else if (type == DAY_OF_WEEK) {
				sval = getDayOfWeekNumber(sub);
				if (sval < 0) {
					throw new ParseException("Invalid Day-of-Week value: '"
							+ sub + "'", i);
				}
				if (s.length() > i + 3) {
					c = s.charAt(i + 3);
					if (c == '-') {
						i += 4;
						sub = s.substring(i, i + 3);
						eval = getDayOfWeekNumber(sub);
						if (eval < 0) {
							throw new ParseException(
									"Invalid Day-of-Week value: '" + sub
											+ "'", i);
						}
					} else if (c == '#') {
						try {
							i += 4;
							nthdayOfWeek = Integer.parseInt(s.substring(i));
							if (nthdayOfWeek < 1 || nthdayOfWeek > 5) {
								throw new Exception();
							}
						} catch (Exception e) {
							throw new ParseException(
									"A numeric value between 1 and 5 must follow the '#' option",
									i);
						}
					} else if (c == 'L') {
						i++;
					}
				}

			} else {
				throw new ParseException(
						"Illegal characters for this position: '" + sub + "'",
						i);
			}
			if (eval != -1) {
				incr = 1;
			}
			addToSet(sval, eval, incr, type);
			return (i + 3);
		}

		if (c == '?') {
			i++;
			if ((i + 1) < s.length()
					&& (s.charAt(i) != ' ' && s.charAt(i + 1) != '\t')) {
				throw new ParseException("Illegal character after '?': "
						+ s.charAt(i), i);
			}
			if (type != DAY_OF_WEEK && type != DAY_OF_MONTH) {
				throw new ParseException(
						"'?' can only be specified for Day-of-Month or Day-of-Week.",
						i);
			}
			if (type == DAY_OF_WEEK && !lastdayOfMonth) {
				int val = daysOfMonth.last();
				if (val == NO_SPEC_INT) {
					throw new ParseException(
							"'?' can only be specified for Day-of-Month -OR- Day-of-Week.",
							i);
				}
			}

			addToSet(NO_SPEC_INT, -1, 0, type);
			return i;
		}

		if (c == '*' || c == '/') {
			if (c == '*' && (i + 1) >= s.length()) {
				addToSet(ALL_SPEC_INT, -1, incr, type);
				return i + 1;
			} else if (c == '/'
					&& ((i + 1) >= s.length() || s.charAt(i + 1) == ' ' || s
					.charAt(i + 1) == '\t')) {
				throw new ParseException("'/' must be followed by an integer.", i);
			} else if (c == '*') {
				i++;
			}
			c = s.charAt(i);
			if (c == '/') { // is an increment specified?
				i++;
				if (i >= s.length()) {
					throw new ParseException("Unexpected end of string.", i);
				}

				incr = getNumericValue(s, i);

				i++;
				if (incr > 10) {
					i++;
				}
				checkIncrementRange(incr, type, i);
			} else {
				incr = 1;
			}

			addToSet(ALL_SPEC_INT, -1, incr, type);
			return i;
		} else if (c == 'L') {
			i++;
			if (type == DAY_OF_MONTH) {
				lastdayOfMonth = true;
			}
			if (type == DAY_OF_WEEK) {
				addToSet(7, 7, 0, type);
			}
			if(type == DAY_OF_MONTH && s.length() > i) {
				c = s.charAt(i);
				if(c == '-') {
					ValueSet vs = getValue(0, s, i+1);
					lastdayOffset = vs.value;
					if(lastdayOffset > 30)
						throw new ParseException("Offset from last day must be <= 30", i+1);
					i = vs.pos;
				}
				if(s.length() > i) {
					c = s.charAt(i);
					if(c == 'W') {
						i++;
					}
				}
			}
			return i;
		} else if (c >= '0' && c <= '9') {
			int val = Integer.parseInt(String.valueOf(c));
			i++;
			if (i >= s.length()) {
				addToSet(val, -1, -1, type);
			} else {
				c = s.charAt(i);
				if (c >= '0' && c <= '9') {
					ValueSet vs = getValue(val, s, i);
					val = vs.value;
					i = vs.pos;
				}
				i = checkNext(i, s, val, type);
				return i;
			}
		} else {
			throw new ParseException("Unexpected character: " + c, i);
		}

		return i;
	}

	public static class ValueSet {
		public int value;

		public int pos;
	}
}



