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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.DocumentedObservation;

enum AppDeployerDocumentedObservation implements DocumentedObservation {

	/**
	 * Span created upon deploying of an application.
	 */
	DEPLOYER_DEPLOY_OBSERVATION {
		@Override
		public String getName() {
			return "spring.cloud.deployer.deploy";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return Tags.values();
		}
	},

	/**
	 * Span created upon undeploying of an application.
	 */
	DEPLOYER_UNDEPLOY_OBSERVATION {
		@Override
		public String getName() {
			return "spring.cloud.deployer.undeploy";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return Tags.values();
		}
//
//		@Override
//		public EventValue[] getEvents() {
//			return Events.values();
//		}
	},

	/**
	 * Span created upon asking for a status of a deployed application.
	 */
	DEPLOYER_STATUS_OBSERVATION {
		@Override
		public String getName() {
			return "spring.cloud.deployer.status";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return Tags.values();
		}
//
//		@Override
//		public EventValue[] getEvents() {
//			return Events.values();
//		}
	},

	/**
	 * Span created upon asking for statuses of deployed applications.
	 */
	DEPLOYER_STATUSES_OBSERVATION {
		@Override
		public String getName() {
			return "spring.cloud.deployer.statuses";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return Tags.values();
		}

//		@Override
//		public EventValue[] getEvents() {
//			return Events.values();
//		}
	},

	/**
	 * Span created upon asking for logs of deployed applications.
	 */
	DEPLOYER_GET_LOG_OBSERVATION {
		@Override
		public String getName() {
			return "spring.cloud.deployer.getLog";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return Tags.values();
		}

//		@Override
//		public EventValue[] getEvents() {
//			return Events.values();
//		}
	},

	/**
	 * Span created upon asking for logs of deployed applications.
	 */
	DEPLOYER_SCALE_SPAN {
		@Override
		public String getName() {
			return "spring.cloud.deployer.scale";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return KeyName.merge(Tags.values(), ScaleTags.values());
		}
//
//		@Override
//		public EventValue[] getEvents() {
//			return Events.values();
//		}
	};

	enum Tags implements KeyName {

		/**
		 * Name of the platform to which apps are being deployed.
		 */
		PLATFORM_NAME {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.name";
			}
		},

		/**
		 * ID of the deployed application.
		 */
		APP_ID {
			@Override
			public String asString() {
				return "spring.cloud.deployer.app.id";
			}
		},

		/**
		 * Name of the deployed application.
		 */
		APP_NAME {
			@Override
			public String asString() {
				return "spring.cloud.deployer.app.name";
			}
		},

		/**
		 * Group of the deployed application.
		 */
		APP_GROUP {
			@Override
			public String asString() {
				return "spring.cloud.deployer.app.group";
			}
		},

		/**
		 * CloudFoundry API URL.
		 */
		CF_URL {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.cf.url";
			}
		},

		/**
		 * CloudFoundry org.
		 */
		CF_ORG {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.cf.org";
			}
		},

		/**
		 * CloudFoundry space.
		 */
		CF_SPACE {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.cf.space";
			}
		},

		/**
		 * Kubernetes API URL.
		 */
		K8S_URL {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.k8s.url";
			}
		},

		/**
		 * Kubernetes namespace.
		 */
		K8S_NAMESPACE {
			@Override
			public String asString() {
				return "spring.cloud.deployer.platform.k8s.namespace";
			}
		},

	}

	enum ScaleTags implements KeyName {

		/**
		 * Scale command deployment id.
		 */
		DEPLOYER_SCALE_DEPLOYMENT_ID {
			@Override
			public String asString() {
				return "spring.cloud.deployer.scale.deploymentId";
			}
		},

		/**
		 * Scale count.
		 */
		DEPLOYER_SCALE_COUNT {
			@Override
			public String asString() {
				return "spring.cloud.deployer.scale.count";
			}
		}

	}

	// TODO: It can't be from here but from micrometer-observation
	enum Events implements EventValue {

		/**
		 * When deployer started deploying the application.
		 */
		DEPLOYER_START {
			@Override
			public String getValue() {
				return "spring.cloud.deployer.start";
			}
		},

		/**
		 * When deployer changes the state of the application.
		 */
		DEPLOYER_STATUS_CHANGE {
			@Override
			public String getValue() {
				return "%s";
			}
		}

	}

	interface EventValue {
		String getValue();
	}

}
