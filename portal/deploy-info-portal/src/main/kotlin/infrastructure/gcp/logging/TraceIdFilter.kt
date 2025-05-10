package infrastructure.gcp.logging

import com.google.cloud.spring.logging.TraceIdLoggingWebMvcInterceptor
import com.google.cloud.spring.logging.extractors.TraceIdExtractor
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * GCP groups log messages by their trace ID, and the GCP Spring implementation retrieves the trace ID using
 * [TraceIdLoggingWebMvcInterceptor]. Since interceptors have lower priority than filters, by default filters' log
 * messages end up without trace ID, making it more difficult to debug any filter-related issues. To prevent that, this
 * class forces the interceptor to be executed first by converting it into a filter too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter(extractor: TraceIdExtractor): Filter {
    private val traceIdInterceptor = TraceIdLoggingWebMvcInterceptor(extractor)

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        request as HttpServletRequest
        response as HttpServletResponse

        traceIdInterceptor.preHandle(request, response, Any())
        chain.doFilter(request, response)
        traceIdInterceptor.afterCompletion(request, response, Any(), null)
    }
}