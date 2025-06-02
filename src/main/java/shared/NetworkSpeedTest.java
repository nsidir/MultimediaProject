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
        final double[] defaultSpeed = {3.0};

        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                System.out.println("Speed test complete: " + report.getTransferRateBit().doubleValue() / 1000000 + " Mbps");
                speedResult[0] = report.getTransferRateBit().doubleValue() / 1000000;
                latch.countDown();
            }

            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                System.err.println("Speed test error: " + errorMessage);
                speedResult[0] = defaultSpeed[0];
                latch.countDown();
            }


            @Override
            public void onProgress(float percent, SpeedTestReport report) {}
        });

        try {
            speedTestSocket.startFixedDownload(Constants.SPEED_TEST_SERVER, TEST_DURATION_MS);
            latch.await(TEST_DURATION_MS + 2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return defaultSpeed[0];
        }
        return Math.round(speedResult[0] * 100.0) / 100.0;
    }
}
