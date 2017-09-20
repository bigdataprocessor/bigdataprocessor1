package bigDataTools.logging;

import ij.IJ;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class IJLazySwingLogger implements Logger {

    private boolean showDebug = false;

    public boolean isIJLogWindowLogging = true;
    public boolean isFileLogging = false;
    private String logFileDirectory = null;
    private String logFileName = null;
    private String logFilePath = null;

    final static Charset ENCODING = StandardCharsets.UTF_8;

    public IJLazySwingLogger() {
    }

    public void setLogFileNameAndDirectory( String logFileName, String logFileDirectory )
    {
        this.logFileDirectory = logFileDirectory;
        this.logFileName = logFileName;
        this.logFilePath = logFileDirectory + File.separatorChar + logFileName;

        File directory = new File( logFileDirectory );

        if (! directory.exists())
        {

            directory.mkdir();
            // If you require it to make the entire directory path including parents,
            // use directory.mkdirs(); here instead.
        }

        List<String> logs = new ArrayList<>();
        logs.add( "Start logging to file." );

        writeSmallTextFile( logs, logFilePath );

    }

    List<String> readSmallTextFile(String aFileName)
    {
        try
        {
            Path path = Paths.get(aFileName);
            return Files.readAllLines(path, ENCODING);
        }
        catch ( IOException e )
        {
            String errorMessage = "Something went wrong accessing the log file " + aFileName;
            if ( isIJLogWindowLogging )
            {
               error( errorMessage );
            }

            List<String> logs = new ArrayList<>();
            logs.add( errorMessage );
            return ( logs );
        }
    }

    void writeSmallTextFile(List<String> aLines, String aFileName)
    {
        try
        {
            Path path = Paths.get( aFileName );
            Files.write(path, aLines, ENCODING);
        }
        catch ( IOException e )
        {

        }
    }

    @Override
    public void setShowDebug(boolean showDebug)
    {
        this.showDebug = showDebug;
    }

    @Override
    public boolean isShowDebug()
    {
        return ( showDebug );
    }

    @Override
    public synchronized void info( String message )
    {
        ijLazySwingLog( String.format("%s", message) );
    }

    @Override
    public synchronized void progress( String message, String progress )
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {

                if (isIJLogWindowLogging)
                {

                    if (IJ.getLog() != null)
                    {
                        String[] logs = IJ.getLog().split("\n");
                        if (logs[logs.length - 1].contains(message))
                        {
                            IJ.log(String.format("\\Update:[PROGRESS]: %s %s", message, progress));
                        }
                        else
                        {
                            IJ.log(String.format("[PROGRESS]: %s %s", message, progress));
                        }
                    }
                    else
                    {
                        IJ.log(String.format("[PROGRESS]: %s %s", message, progress));
                    }

                }

                if (isFileLogging)
                {
                    List<String> logs = readSmallTextFile( logFilePath );
                    int i = logs.size() - 1;

                    if ( logs.get(i).contains(message) )
                    {
                        logs.set(i, String.format("\\Update:[PROGRESS]: %s %s", message, progress));
                    }
                    else
                    {
                        logs.set(i, String.format("[PROGRESS]: %s %s", message, progress));
                    }

                    writeSmallTextFile(logs, logFilePath);
                }
            }
        });
    }

    AtomicInteger progressPos = new AtomicInteger(0);

    @Override
    public void progressWheel(String message)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                String[] logs = IJ.getLog().split("\n");
                String lastLog = logs[logs.length - 1];

                int currentPos = progressPos.getAndIncrement();
                if (currentPos == bouncingChars.length)
                {
                    currentPos = 0;
                    progressPos.set(0);
                }

                String wheel = bouncingChars[currentPos];

                if (lastLog.contains(message))
                {
                    IJ.log(String.format("\\Update:[PROGRESS]: %s %s", message, wheel));
                }
                else
                {
                    IJ.log(String.format("[PROGRESS]: %s %s", message, wheel));
                }
            }
        });
    }

    @Override
    public synchronized void error(String _message)
    {
        if ( isIJLogWindowLogging )
        {
            IJ.showMessage(String.format("Error: %s", _message));
        }

        ijLazySwingLog( String.format("ERROR: %s", _message) );

    }

    @Override
    public synchronized void warning( String _message )
    {
        ijLazySwingLog( String.format("WARNING: %s", _message) );
    }

    @Override
    public synchronized void debug( String _message ){
        if ( showDebug )
        {
            ijLazySwingLog( String.format("[DEBUG]: %s", _message) );
        }
    }


    private void ijLazySwingLog( String message )
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                if (isIJLogWindowLogging)
                {
                    IJ.log( message );
                }

                if ( isFileLogging )
                {
                    List<String> lines = readSmallTextFile(logFilePath);
                    lines.add( message );
                    writeSmallTextFile(lines, logFilePath);
                }
            }
        });
    }


    private String[] wheelChars = new String[]{
            "|", "/", "-", "\\", "|", "/", "-", "\\"
    };

    private String[] bouncingChars = new String[] {

            "(*---------)", // moving -->
            "(-*--------)", // moving -->
            "(--*-------)", // moving -->
            "(---*------)", // moving -->
            "(----*-----)", // moving -->
            "(-----*----)", // moving -->
            "(------*---)", // moving -->
            "(-------*--)", // moving -->
            "(--------*-)", // moving -->
            "(---------*)", // moving -->
            "(--------*-)", // moving -->
            "(-------*--)", // moving -->
            "(------*---)", // moving -->
            "(-----*----)", // moving -->
            "(----*-----)", // moving -->
            "(---*------)", // moving -->
            "(--*-------)", // moving -->
            "(-*--------)", // moving -->

    };
}
