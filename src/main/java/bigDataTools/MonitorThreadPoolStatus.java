package bigDataTools;

import ij.IJ;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by tischi on 11/04/17.
 */
class MonitorThreadPoolStatus {

    private static Logger logger = new IJLazySwingLogger();

    public static void showProgressAndWaitUntilDone(List<Future> futures,
                                                    String message,
                                                    int updateFrequencyMilliseconds) {
        int done = 0;
        while( done != futures.size() )
        {
            done = 0;
            for ( Future f : futures )
            {
                if (f.isDone() ) done++;
            }
            logger.progress(message + done + "/" + futures.size());

            try {
                Thread.sleep(updateFrequencyMilliseconds);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

        }

    }

}
