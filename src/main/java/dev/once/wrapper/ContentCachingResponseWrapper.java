package dev.once.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ContentCachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private ServletOutputStream servletOutputStream;
    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @param response The response to be wrapped
     * @throws IllegalArgumentException if the response is null
     */
    public ContentCachingResponseWrapper(HttpServletResponse response) {
        super(response);
    }
    @Override
    public ServletOutputStream getOutputStream(){
        if (servletOutputStream == null){
            servletOutputStream = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {

                }

                @Override
                public void write(int b) throws IOException {
                    outputStream.write(b);
                    getResponse().getOutputStream().write(b);
                }
            };
        }
        return servletOutputStream;
    }
    public byte[] getContent(){
        return outputStream.toByteArray();
    }
}
