package com.zvonok.config;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AttachmentUploadTimingFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !request.getRequestURI().endsWith("/messages/attachments");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String trace = UUID.randomUUID().toString();
        request.setAttribute("attachmentUploadStart", start);
        request.setAttribute("attachmentUploadTrace", trace);
        response.setHeader("X-Upload-Trace", trace);
        chain.doFilter(request, response);
        if (request.isAsyncStarted()) request.getAsyncContext().addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent event) { completed("complete"); }
            @Override public void onTimeout(AsyncEvent event) { completed("timeout"); }
            @Override public void onError(AsyncEvent event) { completed("transport_error"); }
            @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
            private void completed(String outcome) {
                log.info("attachment_upload trace={} stage=http_complete outcome={} status={} request_bytes={} total_ms={}",
                        trace, outcome, response.getStatus(), request.getContentLengthLong(), (System.nanoTime() - start) / 1_000_000);
            }
        });
        else log.info("attachment_upload trace={} stage=http_complete status={} request_bytes={} total_ms={}",
                trace, response.getStatus(), request.getContentLengthLong(), (System.nanoTime() - start) / 1_000_000);
    }
}
