package top.tobyprime.mcedia_platforms.auth;

public final class NeteaseAccountStatus {
    public final boolean isLoggedIn;
    public final boolean isVip;
    public final String nickname;

    public NeteaseAccountStatus(boolean isLoggedIn, boolean isVip, String nickname) {
        this.isLoggedIn = isLoggedIn;
        this.isVip = isVip;
        this.nickname = nickname;
    }
}
