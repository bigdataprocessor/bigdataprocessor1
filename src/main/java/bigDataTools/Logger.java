package bigDataTools;

public interface Logger {

    /**
     * prints messages that are merely for information, such as progress of computations
     *
     * @param message
     */
    void info(String message);

    /**
     * shows important messages that should not be overlooked by the user
     *
     * @param message
     */
    void error(String message);

    /**
     * shows messages that contain warnings
     *
     * @param message
     */
    void warning(String message);

}
