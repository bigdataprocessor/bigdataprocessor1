package bigDataTools;

import ij.IJ;

import javax.swing.*;


class IJLazySwingLogger implements Logger {

    public IJLazySwingLogger() {
    }

    @Override
    public void info(String _message){
        ijLazySwingLog(_message);
    }

    @Override
    public void error(String _message){
        IJ.showMessage(String.format("[ERROR]: %s", _message));
    }

    @Override
    public void warning(String _message){
        ijLazySwingLog(String.format("[WARNING]: %s", _message));
    }

    private void ijLazySwingLog(String message)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                IJ.log(message);
            }
        });
    }

}
