package net.rose.customPlugin;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

class CustomPluginLoader implements PluginLoader {

    @Override
    public void classloader(final PluginClasspathBuilder builder) {
        // Add dynamically loaded libraries here
    }
}
