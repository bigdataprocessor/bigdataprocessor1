import de.embl.cba.bigdataprocessor.utils.Region5D;
import de.embl.cba.bigdataprocessor.virtualstack2.VirtualStack2;
import de.embl.cba.bigdataprocessor.utils.Utils;
import javafx.geometry.Point3D;
import net.imglib2.FinalInterval;

public class ConcurrentFileAccess
{
    public static void main(String[] args)
    {

        final Region5D region5D = new Region5D();
        region5D.offset.add( new Point3D( 0,0,0 ) );

        String[] channelFolders = new String[]{""};
        String[][][] fileList = new String[1][1][1];
        String fileType = Utils.FileType.TIFF_PLANES.toString();

        fileList[0][0][0] = "image";
        VirtualStack2 vs2 = new VirtualStack2(
                "/Users/tischer/Documents/tmp/stack",
                channelFolders,
                fileList, 1, 1, 500, 500, 1, 8, fileType, "");


        byte[][][] dataCube = new byte[1][100][100];


        Thread thread = new Thread(new Runnable() {
            public void run()
            {
                vs2.saveByteCube(  dataCube, new FinalInterval( new long[]{0,0,0,0,0}, new long[]{50,50,0,0,0} ) );
            }
        });
        thread.start();


        Thread thread2 = new Thread(new Runnable() {
            public void run()
            {
                vs2.saveByteCube(  dataCube, new FinalInterval( new long[]{10,10,0,0,0}, new long[]{50,50,0,0,0} ) );
            }
        });
        thread2.start();


    }
}
