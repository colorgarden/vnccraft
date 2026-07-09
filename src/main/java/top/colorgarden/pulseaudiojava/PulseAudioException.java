package top.colorgarden.pulseaudiojava;

/**
 * Exception for PulseAudio client errors.
 */
public class PulseAudioException extends Exception {
    private final int errorCode;

    public PulseAudioException(String message) {
        super(message);
        this.errorCode = -1;
    }

    public PulseAudioException(String message, int errorCode) {
        super(message + " (error code: " + errorCode + ")");
        this.errorCode = errorCode;
    }

    public PulseAudioException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
