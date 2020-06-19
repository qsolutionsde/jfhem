package io.github.qsolutionsde.jfhem.telnet;

import io.github.qsolutionsde.jfhem.FHEMCommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.telnet.TelnetClient;

import java.io.*;

@Slf4j
@RequiredArgsConstructor
public class FHEMTelnetCommandExecutor implements FHEMCommandExecutor {

    protected final FHEMTelnetConfig.FHEMTelnetHostConfig config;
    protected final TelnetClient telnet = new TelnetClient();

    @Override
    public String getHost() {
        return config.getHost();
    }

    @Synchronized
    @Override
    public String execute(String cmd) {
        try {
            log.debug("Connecting");
            telnet.connect(config.getHost(),  config.getPort());
            log.debug("Connected to telnet");

            PrintWriter w = new PrintWriter(new OutputStreamWriter(telnet.getOutputStream()));
            if (config.getPassword() != null) {
                w.print(config.getPassword() + "\r\n");
                w.flush();
            }

            w.print(cmd + "\r\n");
            w.flush();
            log.debug("Sent command {}",cmd);

            BufferedReader reader = new BufferedReader(new InputStreamReader(telnet.getInputStream()));
            String line = reader.readLine();
            reader.close();
            telnet.disconnect();
            return line;
        } catch (IOException e) {
            log.error("Error processing reply",e);
        }

        return null;
    }

}
