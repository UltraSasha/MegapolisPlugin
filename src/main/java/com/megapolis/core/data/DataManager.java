package com.megapolis.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megapolis.core.MegapolisPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataManager {

    private final MegapolisPlugin plugin;
    private final Gson gson;
    private final boolean useMySQL;
    private final Path dataFolder;
    private final Map<String, Object> cache = new HashMap<>();

    public DataManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = plugin.getDataFolder().toPath().resolve("data");
        this.useMySQL = plugin.getConfig().getBoolean("storage.mysql.enabled", false);

        if (!useMySQL) {
            try {
                Files.createDirectories(dataFolder);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать папку данных: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("Режим MySQL включен (реализация будет добавлена)");
        }
    }

    public <T> void save(String fileName, T data) {
        if (useMySQL) return;
        Path file = dataFolder.resolve(fileName + ".json");
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка сохранения " + fileName + ": " + e.getMessage());
        }
    }

    public <T> T load(String fileName, Class<T> clazz) {
        if (useMySQL) return null;
        Path file = dataFolder.resolve(fileName + ".json");
        if (!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            return gson.fromJson(reader, clazz);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка загрузки " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    public void saveAll() {
        for (Map.Entry<String, Object> entry : cache.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        plugin.getLogger().info("Все данные сохранены.");
    }
}