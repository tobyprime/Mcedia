package top.tobyprime.mcedia_platforms.auth;

@FunctionalInterface
public interface NeteaseLoginQrCodeHandler {
    void onDisplayQrCode(String qrCodeUrl);
}
