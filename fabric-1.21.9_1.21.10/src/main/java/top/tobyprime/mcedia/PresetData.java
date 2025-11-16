package top.tobyprime.mcedia;

public class PresetData {
    public float screenX, screenY, screenZ, screenScale;
    public AudioData primaryAudio = new AudioData();
    public AudioData secondaryAudio = new AudioData();
    public boolean secondaryAudioEnabled;

    public static class AudioData {
        public float x, y, z, maxVol, minRange, maxRange;
    }
}