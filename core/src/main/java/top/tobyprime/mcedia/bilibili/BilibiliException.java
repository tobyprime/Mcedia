package top.tobyprime.mcedia.bilibili;

public class BilibiliException extends RuntimeException {
    public BilibiliException(String message) {
        super(message);
    }
    public BilibiliException(String message, Throwable inner) {
        super(message, inner);
    }
}
