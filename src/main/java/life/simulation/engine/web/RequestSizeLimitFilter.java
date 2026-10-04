package life.simulation.engine.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import life.simulation.engine.config.GameProperties;

/**
 * Rejects a body before the grid is parsed. {@code max-cells} runs only after Jackson
 * has already built the array.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final GameProperties properties;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(GameProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long max = properties.maxRequestBytes();
        long advertised = request.getContentLengthLong();
        if (advertised > max) {
            writeTooLarge(response, max);
            return;
        }
        filterChain.doFilter(new BoundedBodyRequest(request, max), response);
    }

    private void writeTooLarge(HttpServletResponse response, long max) throws IOException {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body exceeds " + max + " bytes");
        detail.setTitle("Request too large");
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), detail);
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {

        private final long max;

        private BoundedBodyRequest(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BoundedStream(super.getInputStream(), max);
        }
    }

    private static final class BoundedStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long max;
        private long total;

        private BoundedStream(ServletInputStream delegate, long max) {
            this.delegate = delegate;
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next >= 0) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int bytes) {
            total += bytes;
            if (total > max) {
                throw new RequestTooLargeException(max);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
