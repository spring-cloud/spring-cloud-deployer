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
import org.cloudfoundry.logcache.v1.EnvelopeBatch;
import org.cloudfoundry.logcache.v1.Log;
import org.cloudfoundry.logcache.v1.LogCacheClient;
import org.cloudfoundry.logcache.v1.ReadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationLogAccessorTests {

    @Mock
    private LogCacheClient logCacheClient;

    @Test
    void testDefaultCase() {
        String sampleData = "foo\nbar\nbaz\nboo";
        String exectedResult = "boo\nbaz\nbar\nfoo";
        when(logCacheClient.read(any())).thenReturn(getSampleResponse(sampleData));
        ApplicationLogAccessor applicationLogAccessor = new ApplicationLogAccessor(this.logCacheClient);
        assertThat(applicationLogAccessor.getLog("myDeploymentId", Duration.ofSeconds(5))).isEqualTo(exectedResult);
    }

    private Mono<ReadResponse> getSampleResponse(String sampleData) {
        Envelope envelope = Envelope.builder().log(getSampleLog(sampleData)).build();
        EnvelopeBatch envelopeBatch = EnvelopeBatch.builder().batch(envelope).build();
        return Mono.just(ReadResponse.builder().envelopes(envelopeBatch).build());
    }

    private Log getSampleLog(String sampleData) {
        return Log.builder().payload(Base64.getEncoder().encodeToString(sampleData.getBytes())).build();
    }
}
