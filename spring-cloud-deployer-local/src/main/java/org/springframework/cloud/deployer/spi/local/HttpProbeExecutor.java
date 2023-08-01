/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.cloud.deployer.spi.local;

import java.net.URI;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties.HttpProbe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Simple probe executor using rest endpoints.
 *
 * @author Janne Valkealahti
 *
 */
public class HttpProbeExecutor {

	private static final Logger logger = LoggerFactory.getLogger(HttpProbeExecutor.class);
    private final RestTemplate restTemplate;
    private final URI uri;

    public HttpProbeExecutor(RestTemplate restTemplate, URI uri) {
        this.restTemplate = restTemplate;
        this.uri = uri;
    }

    public static HttpProbeExecutor from(URL baseUrl, HttpProbe httpProbe) {
        URI base = null;
        try {
            base = baseUrl.toURI();
        } catch (Exception e) {
        }
        if (httpProbe == null || httpProbe.getPath() == null || base == null) {
            return null;
        }
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(base.toString());
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        URI uri = uriBuilderFactory.builder().path("{path}").build(httpProbe.getPath());
        return new HttpProbeExecutor(new RestTemplate(), uri);
    }

    public boolean probe() {
        try {
            logger.info("Probing for {}", this.uri);
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            HttpStatus statusCode = response.getStatusCode();
            boolean ok = statusCode.is2xxSuccessful();
            if (!ok) {
                logger.info("Probe for {} returned {}:{}", this.uri, statusCode, response.getBody());
            }
            return ok;
        } catch (Exception e) {
            logger.trace("Probe error for {}", this.uri, e);
        }
        return false;
    }
}
