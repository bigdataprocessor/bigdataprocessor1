package bigDataTools;

import ij.IJ;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by tischi on 11/04/17.
 */
class MonitorThreadPoolStatus {

    private static Logger logger = new IJLazySwingLogger();

    public MonitorThreadPoolStatus(){
    }

    public static void showProgressAndWaitUntilDone(ExecutorService es, Future[] futures, String message, int updateFrequencyMilliseconds) {

        // Wait until all tasks are done and info status
        es.shutdown();
        int done = 0;
        while( done != futures.length )
        {
            done = 0;
            for ( Future f : futures )
            {
                if (f.isDone() ) done++;
            }
            logger.info(message + done + "/" + futures.length);

            try {
                Thread.sleep(updateFrequencyMilliseconds);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

        }

    }

}
