package com.zvonok.service;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.SyncProgressListener;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.util.TimingInfo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Whitelisted numeric diagnostics only: never log the request, key, URL or headers as a whole. */
@Slf4j
public class S3UploadMetrics extends RequestMetricCollector {
    private final long started = System.nanoTime();
    private final String trace = MDC.get("attachmentUpload") == null ? UUID.randomUUID().toString() : MDC.get("attachmentUpload");
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong resets = new AtomicLong();
    private final AtomicLong firstByte = new AtomicLong();
    private final AtomicLong lastByte = new AtomicLong();
    private final Map<String, Object> sdk = new LinkedHashMap<>();

    public final SyncProgressListener progress = new SyncProgressListener() {
        @Override public void progressChanged(ProgressEvent event) {
            switch (event.getEventType()) {
                case HTTP_REQUEST_STARTED_EVENT -> attempts.incrementAndGet();
                case HTTP_REQUEST_CONTENT_RESET_EVENT -> resets.incrementAndGet();
                case REQUEST_BYTE_TRANSFER_EVENT -> {
                    long now = System.nanoTime(); firstByte.compareAndSet(0, now); lastByte.set(now);
                    bytes.addAndGet(event.getBytesTransferred());
                }
                default -> { }
            }
        }
    };

    @Override public void collectMetrics(Request<?> request, Response<?> response) {
        TimingInfo timing = request.getAWSRequestMetrics().getTimingInfo();
        for (String name : List.of("RequestCount", "RetryCount", "HttpClientRetryCount")) {
            Number value = timing.getCounter(name); if (value != null) sdk.put(name, value);
        }
        for (String name : List.of("HttpClientGetConnectionTime", "HttpClientSendRequestTime", "HttpClientReceiveResponseTime", "HttpRequestTime", "RetryPauseTime", "RequestSigningTime")) {
            List<TimingInfo> values = timing.getAllSubMeasurements(name);
            if (values != null) sdk.put(name + "Ms", values.stream().limit(8).map(TimingInfo::getTimeTakenMillisIfKnown).toList());
        }
        for (String name : List.of("Content-Length", "Content-Encoding", "Transfer-Encoding", "Expect")) {
            request.getHeaders().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).findFirst()
                    .ifPresent(e -> sdk.put(name, e.getValue()));
        }
        request.getHeaders().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("x-amz-content-sha256")).findFirst()
                .ifPresent(e -> sdk.put("chunkedSigning", e.getValue().startsWith("STREAMING-")));
        if (response != null && response.getHttpResponse() != null) sdk.put("httpStatus", response.getHttpResponse().getStatusCode());
    }

    public void finish(long expected, long validationMs, long stagingMs, long putMs, boolean cleaned, Throwable failure) {
        StringBuilder errors = new StringBuilder();
        for (Throwable error = failure; error != null && errors.length() < 512; error = error.getCause()) {
            if (!errors.isEmpty()) errors.append('>'); errors.append(error.getClass().getSimpleName());
        }
        log.info("s3_upload trace={} bytes={} validation_ms={} staging_ms={} put_ms={} total_ms={} sdk_body_bytes={} progress_attempts={} body_resets={} first_body_ms={} last_body_ms={} temp_cleaned={} error_classes={} sdk={}",
                trace, expected, validationMs, stagingMs, putMs, (System.nanoTime() - started) / 1_000_000,
                bytes.get(), attempts.get(), resets.get(), offset(firstByte.get()), offset(lastByte.get()), cleaned, errors, sdk);
    }

    private long offset(long nano) { return nano == 0 ? -1 : (nano - started) / 1_000_000; }
}
