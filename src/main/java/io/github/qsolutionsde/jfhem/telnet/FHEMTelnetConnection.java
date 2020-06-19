package io.github.qsolutionsde.jfhem.telnet;

import io.github.qsolutionsde.jfhem.FHEMEventListener;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.telnet.TelnetClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
public class FHEMTelnetConnection {
    public static void main(String[] args) {
        for (String h : args)
            new FHEMTelnetConnection(h);
    }

    protected final TelnetClient telnet;
    protected final Executor executor;

    @Getter
    protected final FHEMTelnetConfig.FHEMTelnetHostConfig host;

    public FHEMTelnetConnection(String host) {
        this(new FHEMTelnetConfig.FHEMTelnetHostConfig().host(host));
    }

    public FHEMTelnetConnection(String host, int port) {
        this(new FHEMTelnetConfig.FHEMTelnetHostConfig().host(host).port(port));
    }

    public FHEMTelnetConnection(FHEMTelnetConfig.FHEMTelnetHostConfig config) {
        this.host = config;
        telnet = new TelnetClient();
        executor = Executors.newFixedThreadPool(4);
        connect();
    }

    protected List<FHEMEventListener> listeners = new LinkedList<>();

    @Synchronized
    public void addListener(FHEMEventListener l) {
        listeners.add(l);
        log.info("Registered listener {}",l);
    }

    @Synchronized
    public void removeListener(FHEMEventListener l) {
        listeners.remove(l);
    }

    @Retryable(
            value = { IOException.class },
            maxAttempts = 100,
            backoff = @Backoff(delay = 5000, maxDelay = 60000, multiplier = 1.5))
    protected void connect() {
        try
        {
            log.debug("Connecting");
            telnet.connect(host.getHost(),  host.getPort());
            log.debug("Connected to telnet");
            PrintWriter w = new PrintWriter(new OutputStreamWriter(telnet.getOutputStream()));
            if (host.getPassword() != null) {
                w.print(host.getPassword() + "\r\n");
                w.flush();
            }

            w.print("inform on\r\n");
            w.flush();
            log.debug("Sent inform");
            executor.execute(new TelnetReader());
        }
        catch (IOException e) {
            log.error("Error connecting via Telnet", e);
        }
    }

    protected void processLine(String line) {
        log.debug(line);

        if (line.contains("<html"))
            return;

        String[] lineparts = line.trim().split(" ");

        if (lineparts.length > 1) {
            String deviceType = lineparts[0];
            String device = lineparts[1];
            String reading;
            String value;

            if ("global".equals(device) && lineparts.length > 3) {
                device = lineparts[3];
            }

            if (lineparts.length == 3) {
                String s = lineparts[2].trim();
                if (s.endsWith(":")) {
                    reading = s.substring(0, s.length() - 1);
                    value = "";
                    process(deviceType, device, reading, value);
                } else {
                    reading = "state";
                    value = lineparts[2];
                    process(deviceType, device, reading, value);
                }
            } else if (lineparts.length > 3) {
                String s = lineparts[2].trim();
                reading = s;
                if (reading.endsWith(":")) {
                    reading = s.substring(0, s.length() - 1);
                }
                value = String.join(" ", Arrays.copyOfRange(lineparts, 3, lineparts.length));
                process(deviceType, device, reading, value);
            }
        }
    }

    @Synchronized
    private void process(String deviceType, String device, String reading, String value) {
        log.debug("=> Type {}, Device {}, Reading {}, Value {}",deviceType,device,reading,value);

        if (! Character.isAlphabetic(device.charAt(0))) {
            log.warn("Illegal device {}|{}:{}={}", deviceType, device, reading, value);
            return;
        }

        if (device.indexOf(':') > 0) {
            log.warn("Illegal device {}|{}:{}={}", deviceType, device, reading, value);
            return;
        }

        for (FHEMEventListener l : listeners) {
            try {
                l.event(host.getHost(), deviceType, device, reading, value);
            } catch (Exception ex) {
                log.error("Error notifying listener {}",l,ex);
            }
        }
    }

    protected class TelnetReader implements Runnable {
        @Override
        public void run() {
            log.debug("Running");
            try {
                Reader r = new InputStreamReader(telnet.getInputStream());
                BufferedReader reader = new BufferedReader(r);
                String line = reader.readLine();
                while (line != null) {
                    processLine(line);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                log.error("Error reading",e);
                connect();
                return;
            }
            connect();
        }
    }

}
