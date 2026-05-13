package dev.once.wrapper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCachingResponseWrapperTest {

    @Test
    void writeMirrorsToClientAndCapturesContent() throws IOException {
        MockHttpServletResponse origin = new MockHttpServletResponse();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(origin);

        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        wrapper.getOutputStream().write(payload);

        assertThat(wrapper.getContent()).isEqualTo(payload);
        assertThat(origin.getContentAsByteArray()).isEqualTo(payload);
    }
}
