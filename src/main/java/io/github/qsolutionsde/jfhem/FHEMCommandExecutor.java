package io.github.qsolutionsde.jfhem;

public interface FHEMCommandExecutor {
    String getHost();
    String execute(String command);
}
