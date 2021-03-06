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

import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.list;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.zalando.springframework.web.logging.Obfuscator.none;

public final class LogDataBuilder {

    private final Obfuscator headerObfuscator;
    private final Obfuscator parameterObfuscator;
    private final Obfuscator bodyObfuscator;
    private final boolean includePayload;

    public LogDataBuilder() {
        this(true);
    }

    public LogDataBuilder(final boolean includePayload) {
        this(none(), none(), none(), includePayload);
    }

    public LogDataBuilder(final Obfuscator headerObfuscator, final Obfuscator parameterObfuscator,
            final Obfuscator bodyObfuscator) {
        this(headerObfuscator, parameterObfuscator, bodyObfuscator, true);
    }

    public LogDataBuilder(final Obfuscator headerObfuscator, final Obfuscator parameterObfuscator,
            final Obfuscator bodyObfuscator, final boolean includePayload) {
        this.headerObfuscator = headerObfuscator;
        this.parameterObfuscator = parameterObfuscator;
        this.bodyObfuscator = bodyObfuscator;
        this.includePayload = includePayload;
    }

    public RequestData buildRequest(final HttpServletRequest request) {
        final String remote = request.getRemoteAddr();
        final String method = request.getMethod();
        final String uri = request.getRequestURL().toString();

        final Map<String, List<String>> headers = list(request.getHeaderNames())
                .stream()
                .collect(toMap(identity(), h -> list(request.getHeaders(h))
                        .stream()
                        .map(v -> headerObfuscator.obfuscate(h, v))
                        .collect(toList())));

        final Map<String, List<String>> parameters = request.getParameterMap().entrySet()
                .stream()
                .collect(toMap(Map.Entry::getKey, entry ->
                        asList(entry.getValue()).stream()
                                .map(v -> parameterObfuscator.obfuscate(entry.getKey(), v))
                                .collect(toList())));

        final String body = bodyObfuscator.obfuscate("body", payload(request));
        return new RequestData(remote, method, uri, headers, parameters, body);
    }

    public ResponseData buildResponse(final HttpServletResponse response) {
        final int status = response.getStatus();

        final Map<String, List<String>> headers = (response.getHeaderNames())
                .stream()
                .collect(toMap(identity(), h -> singletonList(headerObfuscator.obfuscate(h, response.getHeader(h)))));

        final String contentType = response.getContentType();

        return new ResponseData(status, headers, contentType, payload(response));
    }

    private String payload(final HttpServletRequest request) {
        if (!includePayload) {
            return "<payload is not included>";
        } else if (request instanceof ConsumingHttpServletRequestWrapper) {
            final ConsumingHttpServletRequestWrapper wrapper = (ConsumingHttpServletRequestWrapper) request;
            return getPayload(wrapper);
        } else {
            return "<payload is not consumable>";
        }
    }

    private String payload(final HttpServletResponse response) {
        if (!includePayload) {
            return "<payload is not included>";
        } else if (response instanceof ContentCachingResponseWrapper) {
            final ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
            return getPayload(wrapper);
        } else {
            return "<payload is not consumable>";
        }
    }

    private String getPayload(ConsumingHttpServletRequestWrapper request) {
        return getPayload(request.getContentAsByteArray(), request.getCharacterEncoding());
    }

    private String getPayload(ContentCachingResponseWrapper response) {
        return getPayload(response.getContentAsByteArray(), response.getCharacterEncoding());
    }

    private String getPayload(byte[] content, String charset) {
        return new String(content, 0, content.length, getCharset(charset));
    }

    private Charset getCharset(@Nullable String charset) {
        if (charset == null) {
            return Charset.defaultCharset();
        }

        if (Charset.isSupported(charset)) {
            return Charset.forName(charset);
        } else {
            return Charset.defaultCharset();
        }
    }

}
