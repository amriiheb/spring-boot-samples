package com.wxs.aop.utils;


import com.wxs.aop.common.HTTPConstants;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Vector;

/**
 * @Author: yoyo
 * @Description:
 * @Date: Created in 2018/12/21 17:53
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    private ServletInputStream inputStream;

    private String originalBody;

    private String body;

    private BufferedReader reader;

    private boolean secure;

    private String scheme;

    private boolean proxied;

    private String remoteAddr;

    public RequestWrapper(HttpServletRequest request) throws Exception {
        super(request);
        if (!isMultipart()) {
            preLoadBody(request);
        }
        parseProxyInfo(request);
    }

    private void parseProxyInfo(HttpServletRequest request) {
        secure = request.isSecure();
        scheme = request.getScheme();
        String forwardedProtocol = request.getHeader("x-forwarded-proto");
        if (StringUtils.isNotEmpty(forwardedProtocol)) {
            proxied = true;
            scheme = forwardedProtocol.toLowerCase();
            //secure = HTTPConstants.SCHEME_HTTPS.equals(scheme);
            String forwardedForInfo = request.getHeader("x-forwarded-for");
            remoteAddr = StringUtils.isNotEmpty(forwardedForInfo) ? forwardedForInfo.trim().split(",")[0]
                    : request.getRemoteAddr();
        }
    }

    private void preLoadBody(HttpServletRequest request) throws Exception {
        Charset charset = Charset.forName(getCharacterEncoding());
        byte[] bodyBytes = bytes(request.getInputStream());
        originalBody = new String(bodyBytes, charset);
        body = getParameter("_body");
        if (body == null)
            body = originalBody;
        inputStream = new RequestCachingInputStream(body.getBytes(charset));
    }

    @Override
    public final String getContentType() {
        String overrideContentType = getParameter("_contentType");
        if (overrideContentType != null)
            return overrideContentType;
        return super.getContentType();
    }

    @Override
    public String getHeader(String name) {
        if (HTTPConstants.HEADER_ACCEPT.equals(name)) {
            String overrideAccept = getParameter("_accept");
            if (overrideAccept != null)
                return overrideAccept;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        // server side all headers are in lower case
        if (HTTPConstants.HEADER_ACCEPT.equalsIgnoreCase(name)) {
            String overrideAccept = getParameter("_accept");
            if (overrideAccept != null) {
                Vector<String> headers = new Vector<String>();
                headers.add(overrideAccept);
                return headers.elements();
            }
        }
        return super.getHeaders(name);
    }

    @Override
    public String getMethod() {
        String overrideMethod = getParameter("_method");
        if (overrideMethod != null)
            return overrideMethod;
        return super.getMethod();
    }

    @Override
    public int getServerPort() {
        if (proxied) {
            // if it's reversed proxy, we use standard port for simplicity
            if (HTTPConstants.SCHEME_HTTP.equals(scheme))
                return 80;
            if (HTTPConstants.SCHEME_HTTPS.equals(scheme))
                return 443;
        }
        return super.getServerPort();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream != null)
            return inputStream;
        return super.getInputStream();
    }

    @Override
    public final String getCharacterEncoding() {
        String defaultEncoding = super.getCharacterEncoding();
        return defaultEncoding != null ? defaultEncoding : "UTF-8";
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(inputStream, getCharacterEncoding()));
        }
        return reader;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getRemoteAddr() {
        if (proxied) {
            return remoteAddr;
        }
        return super.getRemoteAddr();
    }

    // port from tomcat implementation,
    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0) {
            port = 80;
        }
        url.append(scheme).append("://").append(getServerName());
        if (HTTPConstants.SCHEME_HTTP.equals(scheme) && port != 80) {
            url.append(':');
            url.append(port);
        }
        if (HTTPConstants.SCHEME_HTTPS.equals(scheme) && port != 443) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return url;
    }

    public String getOriginalBody() {
        if (isMultipart())
            throw new IllegalStateException("multipart request does not support preloaded body");
        return originalBody;
    }

    public String getBody() {
        if (isMultipart())
            throw new IllegalStateException("multipart request does not support preloaded body");
        return body;
    }

    public final boolean isMultipart() {
        String contentType = getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private static class RequestCachingInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        private boolean finished;

        public RequestCachingInputStream(byte[] bytes) {
            inputStream = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() throws IOException {
            int eof = inputStream.read();
            finished = eof == -1;
            return eof;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean isReady() {
            return !finished;
        }

        @Override
        public void setReadListener(ReadListener listener) {

        }

    }

    private byte[] bytes(InputStream stream) throws Exception {
        final int BUFFER_SIZE = 4096;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        try {
            while (true) {
                len = stream.read(buf);
                if (len < 0)
                    break;
                byteArrayOutputStream.write(buf, 0, len);
            }
        } catch (IOException e) {
            throw new Exception(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

}
