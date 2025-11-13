package top.tobyprime.mcedia.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PresetData;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PresetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PresetManager INSTANCE = new PresetManager();
    private final Path presetFile;
    private final Map<String, PresetData> presets = new ConcurrentHashMap<>();

    private PresetManager() {
        this.presetFile = Mcedia.getCacheDirectory().resolve("presets.json");
        loadPresets();
    }

    public static PresetManager getInstance() {
        return INSTANCE;
    }

    public Path getPresetFile() {
        return this.presetFile;
    }

    public void reloadPresets() {
        Mcedia.LOGGER.info("正在从文件热重载 Mcedia 预设...");
        this.presets.clear();
        loadPresets();
    }

    private void loadPresets() {
        if (!Files.exists(presetFile)) {
            return;
        }
        try (FileReader reader = new FileReader(presetFile.toFile())) {
            Type type = new TypeToken<Map<String, PresetData>>() {}.getType();
            Map<String, PresetData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                presets.putAll(loaded);
            }
        } catch (IOException e) {
            Mcedia.LOGGER.error("无法加载 Mcedia 预设文件", e);
        }
    }

    private void savePresets() {
        try (FileWriter writer = new FileWriter(presetFile.toFile())) {
            GSON.toJson(presets, writer);
        } catch (IOException e) {
            Mcedia.LOGGER.error("无法保存 Mcedia 预设文件", e);
        }
    }

    public boolean savePreset(String name, PresetData data) {
        if (name == null || name.isBlank() || data == null) {
            return false;
        }
        presets.put(name.toLowerCase(), data);
        savePresets();
        return true;
    }

    public PresetData getPreset(String name) {
        return presets.get(name.toLowerCase());
    }

    public boolean deletePreset(String name) {
        if (presets.remove(name.toLowerCase()) != null) {
            savePresets();
            return true;
        }
        return false;
    }

    public Map<String, PresetData> getAllPresets() {
        return Collections.unmodifiableMap(presets);
    }
}