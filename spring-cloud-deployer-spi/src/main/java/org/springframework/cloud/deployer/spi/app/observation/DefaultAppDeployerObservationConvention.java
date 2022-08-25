/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.app.observation;

import java.util.Map;

import io.micrometer.common.KeyValues;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

public class DefaultAppDeployerObservationConvention implements AppDeployerObservationConvention {

	// TODO: Check what is high and what is low cardinality
	@Override
	public KeyValues getLowCardinalityKeyValues(AppDeployerContext context) {
		KeyValues keyValues = KeyValues.empty();
		if (context.getAppId() != null) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.APP_ID.withValue(context.getAppId()));
		}
		if (context.getAppScaleRequest() != null) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.ScaleTags.DEPLOYER_SCALE_DEPLOYMENT_ID.withValue(context.getAppScaleRequest().getDeploymentId()),
					AppDeployerDocumentedObservation.ScaleTags.DEPLOYER_SCALE_COUNT.withValue(String.valueOf(context.getAppScaleRequest().getCount())));
		}
		if (context.getRuntimeEnvironmentInfo() != null) {
			keyValues = keyValues.and(platformSpecificKeyValues(context.getRuntimeEnvironmentInfo(), context.getRequest()));
		}
		return keyValues;
	}


	private KeyValues platformSpecificKeyValues(RuntimeEnvironmentInfo environmentInfo, @Nullable AppDeploymentRequest request) {
		KeyValues keyValues = KeyValues.empty();
		Map<String, String> platformSpecificInfo = environmentInfo.getPlatformSpecificInfo();
		if (request != null) {
			String platformName = request.getDeploymentProperties().get("spring.cloud.deployer.platformName");
			if (StringUtils.hasText(platformName)) {
				keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.PLATFORM_NAME.withValue(platformName));
			}
			String appName = request.getDeploymentProperties().get("spring.cloud.deployer.appName");
			if (StringUtils.hasText(appName)) {
				keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.APP_NAME.withValue(appName));
			}
			String group = request.getDeploymentProperties().get("spring.cloud.deployer.group");
			if (StringUtils.hasText(group)) {
				keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.APP_GROUP.withValue(group));
			}
		}
		keyValues = keyValues.and(addCfTags(platformSpecificInfo));
		return keyValues.and(addK8sTags(platformSpecificInfo));
	}

	private KeyValues addCfTags(Map<String, String> platformSpecificInfo) {
		KeyValues keyValues = KeyValues.empty();
		if (platformSpecificInfo.containsKey("API Endpoint")) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.CF_URL.withValue(platformSpecificInfo.get("API Endpoint")));
		}
		if (platformSpecificInfo.containsKey("Organization")) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.CF_ORG.withValue(platformSpecificInfo.get("Organization")));
		}
		if (platformSpecificInfo.containsKey("Space")) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.CF_SPACE.withValue(platformSpecificInfo.get("Space")));
		}
		return keyValues;
	}

	private KeyValues addK8sTags(Map<String, String> platformSpecificInfo) {
		KeyValues keyValues = KeyValues.empty();
		if (platformSpecificInfo.containsKey("master-url")) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.K8S_URL.withValue(platformSpecificInfo.get("master-url")));
		}
		if (platformSpecificInfo.containsKey("namespace")) {
			keyValues = keyValues.and(AppDeployerDocumentedObservation.Tags.K8S_NAMESPACE.withValue(platformSpecificInfo.get("namespace")));
		}
		return keyValues;
	}

}
