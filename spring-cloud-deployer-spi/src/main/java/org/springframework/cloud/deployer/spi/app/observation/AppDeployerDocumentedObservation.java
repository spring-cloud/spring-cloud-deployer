/*
 * Copyright 2013-2022 the original author or authors.
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
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.docs.DocumentedObservation;

enum AppDeployerDocumentedObservation implements DocumentedObservation {

	/**
	 * Observation created upon deploying of an application.
	 */
	DEPLOY_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.deploy";
		}

		@Override
		public String getContextualName() {
			return "deploy";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return PlatformKeyName.values();
		}

		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Observation created upon undeploying of an application.
	 */
	UNDEPLOY_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.undeploy";
		}

		@Override
		public String getContextualName() {
			return "undeploy";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return PlatformKeyName.values();
		}

		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Observation created upon asking for a status of a deployed application.
	 */
	STATUS_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.status";
		}

		@Override
		public String getContextualName() {
			return "status";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return PlatformKeyName.values();
		}

		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Observation created upon asking for statuses of deployed applications.
	 */
	STATUSES_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.statuses";
		}

		@Override
		public String getContextualName() {
			return "statuses";
		}
		
		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return PlatformKeyName.values();
		}


		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Observation created upon asking for logs of deployed applications.
	 */
	GET_LOG_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.getLog";
		}

		@Override
		public String getContextualName() {
			return "getLog";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return PlatformKeyName.values();
		}

		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Observation created upon asking for logs of deployed applications.
	 */
	SCALE_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultAppDeployerObservationConvention.class;
		}

		@Override
		public String getName() {
			return "spring.cloud.deployer.scale";
		}

		@Override
		public String getContextualName() {
			return "scale";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return KeyName.merge(PlatformKeyName.values(), ScaleKeyName.values());
		}

		@Override
		public Event[] getEvents() {
			return Events.values();
		}
	};

	enum PlatformKeyName implements KeyName {

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

	enum ScaleKeyName implements KeyName {

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

	enum Events implements Event {

		/**
		 * When a deployer start action takes place (e.g. deployment, scale).
		 */
		DEPLOYER_START {
			@Override
			public String getName() {
				return "start";
			}
		},

		/**
		 * When a deployer status changes action takes place.
		 */
		DEPLOYER_STATUS_CHANGE {
			@Override
			public String getName() {
				return "status-change";
			}

			@Override
			public String getContextualName() {
				return "%s";
			}
		}

	}
}
