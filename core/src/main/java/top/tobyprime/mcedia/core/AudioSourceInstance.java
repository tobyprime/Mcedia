package top.tobyprime.mcedia.core;


public class AudioSourceInstance {
    public AudioSource audioSource;
    public float offsetX, offsetY, offsetZ;
    public int channel;

    public AudioSourceInstance(AudioSource audioSource, float offsetX, float offsetY, float offsetZ, int channel) {
        this.audioSource = audioSource;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.channel = channel;
    }
}
