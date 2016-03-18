/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.resource.maven;

import java.io.File;

/**
 * Configuration Properties for Maven.
 *
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Mark Fisher
 */
public class MavenProperties {

	/**
	 * Default file path to a locally available maven repository.
	 */
	private static String DEFAULT_LOCAL_REPO = System.getProperty("user.home") +
			File.separator + ".m2" + File.separator + "repository";

	/**
	 * File path to a locally available maven repository, where artifacts will be downloaded.
	 */
	private String localRepository = DEFAULT_LOCAL_REPO;

	/**
	 * Locations of remote maven repositories from which artifacts will be downloaded, if not available locally.
	 */
	private String[] remoteRepositories = new String[]{"https://repo.spring.io/libs-snapshot"};

	/**
	 * Whether the resolver should operate in offline mode.
	 */
	private boolean offline;

	/**
	 * Proxy configuration properties.
	 */
	private Proxy proxy;

	/**
	 * Maximum time to wait for a successful connection.
	 */
	private int connectTimeout;

	/**
	 * Maximum time of inactivity allowed when a request is being processed.
	 */
	private int requestTimeout;

	public void setRemoteRepositories(String[] remoteRepositories) {
		this.remoteRepositories = remoteRepositories;
	}

	public String[] getRemoteRepositories() {
		return remoteRepositories;
	}

	public void setLocalRepository(String localRepository) {
		this.localRepository = localRepository;
	}

	public String getLocalRepository() {
		return localRepository;
	}

	public boolean isOffline() {
		return offline;
	}

	public void setOffline(Boolean offline) {
		this.offline = offline;
	}

	public int getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(int requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Proxy getProxy() {
		return this.proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public static class Proxy {

		/**
		 * Protocol to use for proxy settings.
		 */
		private String protocol = "http";

		/**
		 * Host for the proxy.
		 */
		private String host;

		/**
		 * Port for the proxy.
		 */
		private int port;

		/**
		 * List of non proxy hosts.
		 */
		private String nonProxyHosts;

		private Authentication auth;

		public String getProtocol() {
			return this.protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}

		public String getHost() {
			return this.host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return this.port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getNonProxyHosts() {
			return this.nonProxyHosts;
		}

		public void setNonProxyHosts(String nonProxyHosts) {
			this.nonProxyHosts = nonProxyHosts;
		}

		public Authentication getAuth() {
			return this.auth;
		}

		public void setAuth(Authentication auth) {
			this.auth = auth;
		}

		public static class Authentication {

			/**
			 * Username for the proxy.
			 */
			private String username;

			/**
			 * Password for the proxy.
			 */
			private String password;

			public String getUsername() {
				return this.username;
			}

			public void setUsername(String username) {
				this.username = username;
			}

			public String getPassword() {
				return this.password;
			}

			public void setPassword(String password) {
				this.password = password;
			}

		}
	}
}
