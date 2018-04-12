package org.springframework.cloud.deployer.resource.command;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * A {@link Resource} implementation for resolving simple Command execution.
 *
 * Note: {@link #getInputStream()} throws {@code UnsupportedOperationException}.
 *
 * @author Ali Shahbour
 */
public class CommandResource  extends AbstractResource {

    public static String URI_SCHEME = "command";

    private URI uri;

    /**
     * Create a new {@code CommandResource} from an command path.
     * @param commandPath the command path.
     */
    public CommandResource(String commandPath) {
        Assert.hasText(commandPath, "The command path is required");
        this.uri = URI.create(URI_SCHEME + ":" + commandPath);
    }

    /**
     * Create a new {@code CommandResource} from a URI
     * @param uri a URI
     */
    public CommandResource(URI uri) {
        Assert.notNull(uri, "A URI is required");
        Assert.isTrue("command".equals(uri.getScheme()), "A 'command' scheme is required");
        this.uri = uri;
    }

    @Override
    public String getDescription() {
        return "Command Resource [" + uri + "]";
    }

    /**
     * This implementation currently throws {@code UnsupportedOperationException}
     */
    @Override
    public InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException("getInputStream not supported");
    }

    @Override
    public URI getURI() throws IOException {
        return uri;
    }

    public String getCommand() {
        return "/" + uri.getSchemeSpecificPart();
    }
}
