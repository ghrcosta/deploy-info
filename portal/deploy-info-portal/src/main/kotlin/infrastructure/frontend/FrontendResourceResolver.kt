package infrastructure.frontend

import org.springframework.core.io.Resource
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Resolves the frontend files embedded in the jar (`BOOT-INF/classes/static`, Spring Boot's default
 * classpath static location): existing files are served as they are, and any unknown
 * extension-less path outside the backend API prefixes — a deep link or a page refresh on a
 * frontend route, which matches no file on disk — is served the app shell (`index.html`), so
 * Angular's client-side router can take over.
 */
class FrontendResourceResolver(private val apiPrefixes: List<String>) : PathResourceResolver() {

    override fun getResource(resourcePath: String, location: Resource): Resource? =
        resolve(location, resourcePath)

    /** The resolution logic, public so it can be unit-tested (the overridden method is protected). */
    fun resolve(location: Resource, resourcePath: String): Resource? {
        location.createRelative(resourcePath)
            .takeIf { it.exists() && it.isReadable }
            ?.let { return it }
        if (!isFrontendRoute(resourcePath)) {
            return null
        }
        return location.createRelative(INDEX_FILE).takeIf { it.exists() && it.isReadable }
    }

    /**
     * A frontend route is any extension-less path outside the backend API prefixes. Paths with an
     * extension are asset requests, which must 404 when missing instead of returning HTML.
     */
    private fun isFrontendRoute(resourcePath: String): Boolean =
        !resourcePath.contains(".") && apiPrefixes.none { prefix ->
            resourcePath == prefix || resourcePath.startsWith("$prefix/")
        }

    private companion object {
        const val INDEX_FILE = "index.html"
    }
}
