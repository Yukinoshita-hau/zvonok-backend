package com.zvonok.unittests.service;

import com.zvonok.config.AttachmentUploadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentUploadExecutorTest {
    @Test void storageWorkIsBoundedAndNeverFallsBackToTheServletThread() throws Exception {
        var executor = new AttachmentUploadConfig().attachmentUploadExecutor();
        executor.initialize();
        var started = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        Runnable blocked = () -> {
            assertTrue(Thread.currentThread().getName().startsWith("attachment-upload-"));
            started.countDown();
            try { release.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        };
        try {
            for (int i = 0; i < 4; i++) executor.execute(blocked);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 8; i++) executor.execute(blocked);
            assertThrows(TaskRejectedException.class, () -> executor.execute(blocked));
        } finally { release.countDown(); executor.shutdown(); }
    }
}
