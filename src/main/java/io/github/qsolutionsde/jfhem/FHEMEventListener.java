package io.github.qsolutionsde.jfhem;

public interface FHEMEventListener {
    public void event(String host, String deviceType, String device, String reading, String value);
}
