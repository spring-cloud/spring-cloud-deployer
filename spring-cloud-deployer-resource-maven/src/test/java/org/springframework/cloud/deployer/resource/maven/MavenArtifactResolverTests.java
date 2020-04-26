package org.springframework.cloud.deployer.resource.maven;

import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Eric Chen
 */
class MavenArtifactResolverTests {

    @Test
    void testResolve_fail_onProxy_UnknownHostException() {
        String location = "maven://foo:bar:1.0.1";
        MavenResourceLoader loader = new MavenResourceLoader(new MavenProperties());
        Resource resource = loader.getResource(location);
        assertEquals(MavenResource.class, resource.getClass());
        MavenResource mavenResource = (MavenResource) resource;

        //HttpTransporterFactory transporterFactory = Mockito.mock(HttpTransporterFactory.class);
        MavenArtifactResolver resolver = new MavenArtifactResolver(mavenPropertiesWithProxyRepo());

        try {
            resolver.resolve(mavenResource);
            fail();
        } catch (Exception e) {
            UnknownHostException ex = (UnknownHostException)e.getCause().getCause().getCause();
            Assert.assertEquals(ex.getMessage(), "http://proxy.example.com: nodename nor servname provided, or not known");
        }
        //resolver.resolve(mvnresource);
    }

    @Test
    void testResolve_fail_onNoProxy_IllegalArgumentException() {
        String location = "maven://foo:bar:1.0.1";
        MavenResourceLoader loader = new MavenResourceLoader(new MavenProperties());
        Resource resource = loader.getResource(location);
        assertEquals(MavenResource.class, resource.getClass());
        MavenResource mavenResource = (MavenResource) resource;

        //HttpTransporterFactory transporterFactory = Mockito.mock(HttpTransporterFactory.class);
        MavenArtifactResolver resolver = new MavenArtifactResolver(mavenPropertiesWithNoProxyRepo());

        try {
            resolver.resolve(mavenResource);
            fail();
        } catch (Exception e) {
            IllegalArgumentException ex = (IllegalArgumentException)e.getCause().getCause().getCause();
            Assert.assertEquals(ex.getMessage(), "port out of range:99999");
        }
        //resolver.resolve(mvnresource);
    }

    private MavenProperties mavenPropertiesWithProxyRepo() {
        MavenProperties mavenProperties = new MavenProperties();
        mavenProperties.setLocalRepository("~/.m2");

        MavenProperties.RemoteRepository remoteRepo2 = new MavenProperties.RemoteRepository();
        remoteRepo2.setUrl("http://myrepo.com:99999");
        mavenProperties.getRemoteRepositories().put("repo2", remoteRepo2);

        MavenProperties.Proxy proxy = new MavenProperties.Proxy();
        proxy.setHost("http://proxy.example.com");
        proxy.setPort(8080);
        proxy.setNonProxyHosts("apache*|*.springframework.org|127.0.0.1|localhost");
        mavenProperties.setProxy(proxy);

        return mavenProperties;
    }

    private MavenProperties mavenPropertiesWithNoProxyRepo() {
        MavenProperties mavenProperties = new MavenProperties();
        mavenProperties.setLocalRepository("~/.m2");

        MavenProperties.RemoteRepository remoteRepo1 = new MavenProperties.RemoteRepository();
        remoteRepo1.setUrl("http://localhost:99999");
        mavenProperties.getRemoteRepositories().put("repo1", remoteRepo1);

        MavenProperties.Proxy proxy = new MavenProperties.Proxy();
        proxy.setHost("http://proxy.example.com");
        proxy.setPort(8080);
        proxy.setNonProxyHosts("apache*|*.springframework.org|127.0.0.1|localhost");
        mavenProperties.setProxy(proxy);

        return mavenProperties;
    }
}
