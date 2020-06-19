package io.github.qsolutionsde.jfhem.telnet;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
public class FHEMTelnetConfig {
    @Getter @Setter protected List<FHEMTelnetHostConfig> hosts;

    public void add(FHEMTelnetHostConfig c) {
        hosts.add(c);
    }

    @NoArgsConstructor
    @Accessors
    @ToString
    @EqualsAndHashCode
    public static class FHEMTelnetHostConfig {
        @Getter @Setter protected String host;
        @Getter @Setter protected int port = 7072;
        @Getter @Setter protected String password = null;

        public FHEMTelnetHostConfig host(String host) {
            setHost(host);
            return this;
        }

        public FHEMTelnetHostConfig port(int port) {
            setPort(port);
            return this;
        }

        public FHEMTelnetHostConfig password(String pass) {
            setPassword(pass);
            return this;
        }

    }
}
