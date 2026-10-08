package tests.infrastructure.security

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import infrastructure.DeployInfoPortalApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun `Preflight from the configured origin is allowed`() {
        val result = mvc.perform(
            options("/collector/bucket")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        ).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertEquals("http://localhost:4200", result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals("true", result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    fun `Preflight from an unlisted origin is rejected`() {
        val result = mvc.perform(
            options("/collector/bucket")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        ).andReturn()
        assertEquals(HttpStatus.FORBIDDEN.value(), result.response.status)
        assertNull(result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `Actual request carrying an unlisted origin is rejected`() {
        val result = mvc.perform(
            get("/collector/bucket").header(HttpHeaders.ORIGIN, "http://evil.example.com")
        ).andReturn()
        assertEquals(HttpStatus.FORBIDDEN.value(), result.response.status)
        assertEquals("Invalid CORS request", result.response.contentAsString)
    }
}

/**
 * Proves the origin is configurable: the test-profile default (`http://localhost:4200`) is
 * replaced by a property override.
 */
@SpringBootTest(
    classes = [DeployInfoPortalApplication::class],
    properties = ["deploy-info.cors.allowed-origins=https://staging.example.com"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationConfigurableOriginTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun `Preflight from the overridden configured origin is allowed`() {
        val result = mvc.perform(
            options("/collector/bucket")
                .header(HttpHeaders.ORIGIN, "https://staging.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        ).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertEquals("https://staging.example.com", result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `The default dev origin is no longer allowed`() {
        val result = mvc.perform(
            options("/collector/bucket")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        ).andReturn()
        assertEquals(HttpStatus.FORBIDDEN.value(), result.response.status)
        assertNull(result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }
}

/**
 * Proves the same-origin production story: with an empty origin list no CORS mapping is
 * registered at all, so a request carrying an `Origin` header (browsers send one even on
 * same-origin non-GET requests) reaches the handler instead of being rejected by CORS.
 */
@SpringBootTest(
    classes = [DeployInfoPortalApplication::class],
    properties = ["deploy-info.cors.allowed-origins="],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationDisabledTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun `Request with an origin header is not rejected by CORS`() {
        // POST to a GET-only endpoint: reaches the handler → 405 method-not-allowed; a CORS
        // rejection would be 403 "Invalid CORS request" before the handler is consulted.
        val result = mvc.perform(
            post("/collector/bucket").header(HttpHeaders.ORIGIN, "http://localhost:4200")
        ).andReturn()
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), result.response.status)
    }
}