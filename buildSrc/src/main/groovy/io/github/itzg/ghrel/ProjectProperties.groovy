package io.github.itzg.ghrel

import org.gradle.api.provider.Property

abstract class ProjectProperties {
    abstract Property<String> getLicense()

    abstract Property<String> getHomepage()
}
