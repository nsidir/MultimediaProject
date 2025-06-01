package shared;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkSpeedTest {
    private static final int TEST_DURATION_MS = 5000; // 5 seconds

    public static double measureDownloadSpeed() {
        final SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        final CountDownLatch latch = new CountDownLatch(1);
        final double[] speedResult = {0.0};

        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                speedResult[0] = report.getTransferRateBit().doubleValue() / 1000000; // Convert to Mbps
                latch.countDown();
            }

            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                speedResult[0] = 2.0; // Default fallback speed
                latch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                // Progress updates can be handled here if needed
            }
        });

        try {
            speedTestSocket.startFixedDownload(Constants.SPEED_TEST_SERVER, TEST_DURATION_MS);
            latch.await(TEST_DURATION_MS + 2000, TimeUnit.MILLISECONDS); // Wait with timeout
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 2.0; // Default fallback speed
        }

        return Math.round(speedResult[0] * 100.0) / 100.0; // Round to 2 decimal places
    }
}