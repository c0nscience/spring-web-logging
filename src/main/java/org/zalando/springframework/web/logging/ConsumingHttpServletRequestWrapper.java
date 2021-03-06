package org.zalando.springframework.web.logging;

/*
 * #%L
 * spring-web-logging
 * %%
 * Copyright (C) 2015 Zalando SE
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/***
 * Simple wrapper around a {@link ContentCachingRequestWrapper}, which consumes the request immediately and redirects
 * access to the cache.
 */
final class ConsumingHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumingHttpServletRequestWrapper.class);

    private static final int CONSUMING_CHUNK_SIZE = 1024;


    private InputStream cachedInputStream;
    private TeeServletInputStream servletInputStream;

    public ConsumingHttpServletRequestWrapper(final ContentCachingRequestWrapper request) {
        super(request);
        consume();
    }

    @Override
    public ContentCachingRequestWrapper getRequest() {
        return (ContentCachingRequestWrapper) super.getRequest();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (servletInputStream == null) {
            servletInputStream = new TeeServletInputStream();
        }
        return servletInputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
    }

    @Override
    public String getCharacterEncoding() {
        final String encoding = super.getCharacterEncoding();
        return (encoding != null ? encoding : WebUtils.DEFAULT_CHARACTER_ENCODING);
    }

    public byte[] getContentAsByteArray() {
        return getRequest().getContentAsByteArray();
    }

    private void consume() {
        ServletInputStream originalInputStream = null;
        try {
            originalInputStream = getRequest().getInputStream();
            consume(originalInputStream);
            this.cachedInputStream = new ByteArrayInputStream(getRequest().getContentAsByteArray());
        } catch (final IOException e) {
            LOG.error("Error consuming request", e);
        } finally {
            closeStream(originalInputStream);
        }
    }

    private void consume(final InputStream stream) throws IOException {
        final byte[] chunk = new byte[CONSUMING_CHUNK_SIZE];
        int consumedBytes;
        do {
            consumedBytes = stream.read(chunk, 0, CONSUMING_CHUNK_SIZE);
        } while (consumedBytes != -1);
    }

    private void closeStream(final ServletInputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (final IOException e) {
                LOG.error("Error closing request", e);
            }
        }
    }

    private class TeeServletInputStream extends ServletInputStream {

        @Override
        public int read() throws IOException {
            return cachedInputStream.read();
        }
    }
}
