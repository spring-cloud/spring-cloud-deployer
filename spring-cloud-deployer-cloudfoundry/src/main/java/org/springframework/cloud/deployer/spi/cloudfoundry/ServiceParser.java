/*
 * Copyright 2019-2020 the original author or authors.
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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

/**
 * Parses service instances and parses binding parameters if provided. Accepts Strings like
 * 'myservice foo=bar, cat=bat' or 'service foo:bar, cat:bat', White space is required between the
 * service name and parameters, but optional between the key-value pairs.
 *
 * @author David Turanski
 */
abstract class ServiceParser {

  private static Pattern serviceWithParameters = Pattern.compile("([^\\s]+)\\s*?(.*)?");

  private static Pattern singleQuotedLiteral = Pattern.compile("'([^']*?)'");

  /**
   * @param serviceSpec the service instance followed by optional parameters.
   * @return an Option<Map> of parameters.
   */
  static Optional<Map<String, String>> getServiceParameters(String serviceSpec) {
    Matcher m = serviceWithParameters.matcher(serviceSpec);

    if (m.matches()) {
      return parseParameters(m.group(2), serviceSpec);
    }

    return Optional.ofNullable(null);
  }

  /**
   * Extract the service name.
   *
   * @param serviceSpec the service instance name followed by optional parameters
   * @return the service instance
   */
  static String getServiceInstanceName(String serviceSpec) {
    Matcher m = serviceWithParameters.matcher(serviceSpec);
    if (m.matches()) {
      return m.group(1);
    } else {
      throw new IllegalArgumentException("invalid service specification: " + serviceSpec);
    }
  }

  static List<String> splitServiceProperties(String serviceProperties) {
    List<String> serviceInstances = new ArrayList<>();

    if (StringUtils.hasText(serviceProperties)) {
      Matcher m = singleQuotedLiteral.matcher(serviceProperties);
      int index = 0;

      while (index >= 0 && m.find(index)) {
        String val = m.group().replaceAll("'", "");
        serviceInstances.add(val);
        serviceProperties = serviceProperties.replaceAll(m.group(), "");
        index = serviceProperties.indexOf("'");
        m = singleQuotedLiteral.matcher(serviceProperties);
      }

      Stream.of(serviceProperties.split(","))
          .filter(StringUtils::hasText)
          .map(String::trim)
          .forEach(s -> serviceInstances.add(s));
    }

    return serviceInstances;
  }

  private static Optional<Map<String, String>> parseParameters(
      String rawParametersString, String serviceSpec) {
    if (StringUtils.hasText(rawParametersString)) {
      Map<String, String> parameters = new LinkedHashMap<>();
      String[] entries = rawParametersString.split(",");
      Stream.of(entries)
          .forEach(
              s -> {
                String[] pair = s.split("[:|=]");
                if (pair.length != 2) {
                  throw new IllegalArgumentException(
                      "invalid service specification: " + serviceSpec);
                }
                parameters.put(pair[0].trim(), pair[1].trim());
              });
      return Optional.of(parameters);
    }
    return Optional.ofNullable(null);
  }
}
