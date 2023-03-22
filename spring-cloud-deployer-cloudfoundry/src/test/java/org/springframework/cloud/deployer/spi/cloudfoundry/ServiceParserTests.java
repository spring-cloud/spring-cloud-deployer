/*
 * Copyright 2019-2021 the original author or authors.
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

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** @author David Turanski */
public class ServiceParserTests {

  @Test
  public void plainService() {
    assertThat(ServiceParser.getServiceParameters("test-service").isPresent()).isFalse();
    assertThat(ServiceParser.getServiceInstanceName("test-service")).isEqualTo("test-service");
  }

  @Test
  public void plainServiceWithSpecialCharacters() {
    assertThat(ServiceParser.getServiceParameters("test.service.$$").isPresent()).isFalse();
  }

  @Test
  public void serviceWithParameters() {
    String serviceSpec = "test-service foo:bar";
    assertThat(ServiceParser.getServiceParameters(serviceSpec).get())
        .isEqualTo(Collections.singletonMap("foo", "bar"));
  }

  @Test
  public void getServiceInstanceName() {
    String serviceSpec = "test-service foo:bar";
    assertThat(ServiceParser.getServiceInstanceName(serviceSpec)).isEqualTo("test-service");
  }

  @Test
  public void serviceWithSpacesParameters() {
    assertThat(ServiceParser.getServiceParameters("test-service foo : bar").get())
        .isEqualTo(Collections.singletonMap("foo", "bar"));
  }

  @Test
  public void serviceWithEqualsInParameters() {
    assertThat(ServiceParser.getServiceParameters("test-service foo=bar").get())
        .isEqualTo(Collections.singletonMap("foo", "bar"));
  }

  @Test
  public void realWorldExample() {

    Map<String,String> params = ServiceParser.getServiceParameters(
        "nfs share:1.2.3.4/export, uid:65534, gid:65534, mount:/var/scdf")
        .get();

    assertThat(params).containsOnly(
        entry("share","1.2.3.4/export"),
        entry("uid","65534"),
        entry("gid","65534"),
        entry("mount","/var/scdf")
    );
  }

  @Test
  public void anotherRealWorldExample() {
    Map<String,String> params = ServiceParser.getServiceParameters(
        "nfs share=1.2.3.4/export, uid=65534, gid=65534, mount=/var/scdf")
        .get();

    assertThat(params).containsOnly(
        entry("share","1.2.3.4/export"),
        entry("uid","65534"),
        entry("gid","65534"),
        entry("mount","/var/scdf")
    );
  }

  @Test
  public void serviceWithInvalidParameters() {
		assertThatThrownBy(() -> {
			ServiceParser.getServiceParameters("test-service foo bar");
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(
				"invalid service specification: test-service foo bar");
  }

  @Test
  public void splitServiceProperties() {

     assertThat(ServiceParser.splitServiceProperties(
        "'nfs share:10.194.2.6/export,uid:65534,gid:65534,mount:/var/scdf',mysql,'foo bar:baz'"))
         .containsExactlyInAnyOrder(
             "nfs share:10.194.2.6/export,uid:65534,gid:65534,mount:/var/scdf",
             "mysql",
             "foo bar:baz");

    assertThat(ServiceParser.splitServiceProperties("mysql,rabbit,redis"))
        .containsExactlyInAnyOrder(
            "mysql",
            "rabbit",
            "redis");

    assertThat(ServiceParser.splitServiceProperties(
        "redis,  'nfs share:10.194.2.6/export,uid:65534,gid:65534,mount:/var/scdf', mysql ,  'foo bar:baz'"))
        .containsExactlyInAnyOrder(
            "redis",
            "nfs share:10.194.2.6/export,uid:65534,gid:65534,mount:/var/scdf",
            "mysql",
            "foo bar:baz");
  }

}
