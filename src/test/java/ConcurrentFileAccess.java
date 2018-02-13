import de.embl.cba.bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import de.embl.cba.bigDataTools.utils.MonitorThreadPoolStatus;
import de.embl.cba.bigDataTools.utils.Utils;
import net.imglib2.FinalInterval;

import java.io.IOException;

public class ConcurrentFileAccess
{
    public static void main(String[] args)
    {

        String[] channelFolders = new String[]{""};
        String[][][] fileList = new String[1][1][1];
        String fileType = Utils.FileType.SINGLE_PLANE_TIFF.toString();

        fileList[0][0][0] = "image";
        VirtualStackOfStacks vss = new VirtualStackOfStacks(
                "/Users/tischer/Documents/tmp/stack",
                channelFolders,
                fileList, 1, 1, 500, 500, 1, 8, fileType, "");


        byte[][][] dataCube = new byte[1][100][100];


        Thread thread = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    vss.saveByteCube(  dataCube, new FinalInterval( new long[]{0,0,0,0,0}, new long[]{50,50,0,0,0} ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        });
        thread.start();


        Thread thread2 = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    vss.saveByteCube(  dataCube, new FinalInterval( new long[]{10,10,0,0,0}, new long[]{50,50,0,0,0} ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        });
        thread2.start();


    }
}
