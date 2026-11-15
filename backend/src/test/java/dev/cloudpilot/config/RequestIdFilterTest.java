package dev.cloudpilot.config;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Runs the filter and returns the request id the logs saw while the request was handled. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seenByLogs = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenByLogs.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return seenByLogs.get();
    }

    @Test
    void generatesAnIdAndReturnsIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String id = run(new MockHttpServletRequest("GET", "/api/v1/ping"), response);

        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void keepsAWellFormedIdFromTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader(RequestIdFilter.HEADER, "frontend-1234-abcd");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(run(request, response)).isEqualTo("frontend-1234-abcd");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("frontend-1234-abcd");
    }

    @Test
    void replacesAnIdThatCouldPolluteTheLogs() throws Exception {
        // A caller-supplied id goes into every log line, so it must not be able to forge new lines.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader(RequestIdFilter.HEADER, "abc\nERROR fake log line");

        String id = run(request, new MockHttpServletResponse());

        assertThat(id).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsTheIdAfterTheRequestSoThreadsDoNotLeakIt() throws Exception {
        run(new MockHttpServletRequest("GET", "/api/v1/ping"), new MockHttpServletResponse());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
