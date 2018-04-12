package org.springframework.cloud.deployer.resource.command;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link ResourceLoader} that loads {@link CommandResource}s from locations of the format
 * {@literal command:<commandpath>}
 *
 * @author Ali Shahbour
 */
public class CommandResourceLoader  implements ResourceLoader {

    private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    /**
     * Returns a {@link CommandResource} for the provided location.
     *
     * @param location the command location. May optionally be preceded by {@value CommandResource#URI_SCHEME}
     * followed by a colon, e.g. {@literal command:/bin/pwd}
     * @return the {@link CommandResource}
     */
    @Override
    public Resource getResource(String location) {
        Assert.hasText(location, "location is required");
        String commandPath = location.replaceFirst(CommandResource.URI_SCHEME + ":", "");
        return new CommandResource(commandPath);
    }

    /**
     * Returns the {@link ClassLoader} for this ResourceLoader.
     */
    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

}
