package com.nurseli.nrsfinanceportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * finance-service OpenAI konfigürasyon özellikleri — API key, model, temperature ve timeout ayarlarını tutar.
 */
@ConfigurationProperties(prefix = "openai")

public class OpenAiProperties {

    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private double temperature = 0.2;
    private int timeoutSeconds = 30;

    /**
     * {@code getApiKey} — OpenAI API anahtarını döner.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * {@code setApiKey} — OpenAI API anahtarını ayarlar.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * {@code getModel} — Kullanılacak OpenAI model adını döner.
     */
    public String getModel() {
        return model;
    }

    /**
     * {@code setModel} — OpenAI model adını ayarlar.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * {@code getTemperature} — Model temperature değerini döner.
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * {@code setTemperature} — Model temperature değerini ayarlar.
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * {@code getTimeoutSeconds} — API timeout süresini saniye olarak döner.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * {@code setTimeoutSeconds} — API timeout süresini ayarlar.
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * {@code isConfigured} — API key tanımlı ve boş değilse true döner.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
    