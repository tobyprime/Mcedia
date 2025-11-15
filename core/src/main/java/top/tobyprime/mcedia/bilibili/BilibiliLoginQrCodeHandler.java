package top.tobyprime.mcedia.bilibili;

@FunctionalInterface
public interface BilibiliLoginQrCodeHandler {
    void onDisplayQrCode(String qrCodeUrl);
}
