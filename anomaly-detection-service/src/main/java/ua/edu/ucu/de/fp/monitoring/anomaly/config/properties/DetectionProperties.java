package ua.edu.ucu.de.fp.monitoring.anomaly.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "detection")
public class DetectionProperties {

    private String sourceQueue;
    private String targetQueue;
    private List<String> enabledRules;

    public String getSourceQueue() {
        return sourceQueue;
    }

    public void setSourceQueue(String sourceQueue) {
        this.sourceQueue = sourceQueue;
    }

    public String getTargetQueue() {
        return targetQueue;
    }

    public void setTargetQueue(String targetQueue) {
        this.targetQueue = targetQueue;
    }

    public List<String> getEnabledRules() {
        return enabledRules;
    }

    public void setEnabledRules(List<String> enabledRules) {
        this.enabledRules = enabledRules;
    }
}