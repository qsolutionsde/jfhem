package io.github.qsolutionsde.jfhem.http;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class FHEMWebConfig {
    @Setter
    @Getter
    protected List<FHEMHostConfig> hosts;

    @Data
    @NoArgsConstructor
    public static class FHEMHostConfig {
        protected String url;
        protected String username = null;
        protected String password = null;
        protected boolean useCsrf = false;
        protected String timezone = "Europe/Berlin";

        public FHEMHostConfig url(String host) {
            setUrl(host);
            return this;
        }

        public FHEMHostConfig username(String user) {
            setUsername(user);
            return this;
        }

        public FHEMHostConfig password(String pass) {
            setPassword(pass);
            return this;
        }

        public FHEMHostConfig useCsrf() {
            setUseCsrf(true);
            return this;
        }

    }
}
