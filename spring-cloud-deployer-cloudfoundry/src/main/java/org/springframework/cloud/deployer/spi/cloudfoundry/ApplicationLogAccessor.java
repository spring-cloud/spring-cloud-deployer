/*
 * Copyright 2023 the original author or authors.
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


import org.cloudfoundry.logcache.v1.Envelope;
import org.cloudfoundry.logcache.v1.Log;
import org.cloudfoundry.logcache.v1.LogCacheClient;
import org.cloudfoundry.logcache.v1.ReadRequest;
import org.cloudfoundry.logcache.v1.ReadResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Provide api access to retrieve logs for applications.
 *
 * @author Glenn Renfro
 * @author Chris Bono
 *
 * @since 2.9.3
 */
public class ApplicationLogAccessor {

    private final LogCacheClient logCacheClient;

    public ApplicationLogAccessor(LogCacheClient logCacheClient) {
        Assert.notNull(logCacheClient, "logCacheClient must not be null");
        this.logCacheClient = logCacheClient;
    }

    /**
     * Retrieve logs for specified deployment id.
     * @param deploymentId the deployment id of the application.
     * @param apiTimeout specify duration of the timeout for the api.
     * @return String containing the log information or empty string if no entries are available.
     */
    public String getLog(String deploymentId, Duration apiTimeout) {
        Assert.hasText(deploymentId, "id must have text and not null");
        Assert.notNull(apiTimeout, "apiTimeout must not be null");
        StringBuilder stringBuilder = new StringBuilder();
        List<Log> logs = this.logCacheClient
                .read(ReadRequest.builder().sourceId(deploymentId).build())
                .flatMapMany(this::responseToEnvelope)
                .collectList()
                .block(apiTimeout);
        // if no log exists the result set is null.
        if(logs == null) {
            return "";
        }
        Base64.Decoder decoder = Base64.getDecoder();
        logs.forEach((log) -> {
            stringBuilder.append(new String(decoder.decode(log.getPayload())));
            stringBuilder.append(System.lineSeparator());
        });
        return stringBuilder.toString();
    }

    private Flux<Log> responseToEnvelope(ReadResponse response) {
        return Flux.fromIterable(response.getEnvelopes().getBatch())
                .map(Envelope::getLog)
                .filter(Objects::nonNull);
    }
}
