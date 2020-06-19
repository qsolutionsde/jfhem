package io.github.qsolutionsde.jfhem.http;

import io.github.qsolutionsde.jfhem.FHEMCommandExecutor;
import io.github.qsolutionsde.jfhem.data.TimestampedValue;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * Represents a connection to an FHEM instance using HTTP (no longpoll).
 *
 * Supports CSRF tokens
 */

@Slf4j
public class FHEMHttpConnection implements FHEMCommandExecutor {
    protected final FHEMWebConfig.FHEMHostConfig config;

    protected final CloseableHttpClient client;

    public FHEMHttpConnection(FHEMWebConfig.FHEMHostConfig config)
    {
        this.config = config;

        final int timeout = 5;
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000).build();

        if (config.getUsername() != null) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials
                    = new UsernamePasswordCredentials(config.getUsername(), config.getPassword());
            provider.setCredentials(AuthScope.ANY, credentials);

            client = HttpClientBuilder.create()
                    .setDefaultRequestConfig(rc)
                    .setDefaultCredentialsProvider(provider)
                    .build();
        } else
            client = HttpClientBuilder.create().setDefaultRequestConfig(rc).build();
    }

    protected LoadingCache<String, Map<String, TimestampedValue<String>>> devices = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build(this::getReadingsDirect);

    public TimestampedValue<String> getReading(String device, String reading) {
        Map<String, TimestampedValue<String>> m = getReadings(device);
        if (m != null)
            return m.get(reading);
        else
            return null;
    }

    protected Map<String, TimestampedValue<String>> getReadingsDirect(String device) {
        return getReadingsDirect(Collections.singletonList(device)).get(device);
    }

    public Map<String, TimestampedValue<String>> getReadings(String device) {
        return devices.get(device);
    }

    public Map<String, Map<String, TimestampedValue<String>>> getReadingsDirect(List<String> devices) {
        Map<String, Map<String, TimestampedValue<String>>> rl = new HashMap<>();

        HttpGet method = new HttpGet(config.getUrl() + "?cmd=jsonlist2+"
                                + String.join("|", devices) + "&XHR=1"
                                + (config.isUseCsrf() ? "&fwcsrf=" + getCsrfToken() : ""));

        try (final CloseableHttpResponse rsp = client.execute(method)) {
            final int statusCode = rsp.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                ObjectMapper m = new ObjectMapper();
                JsonNode o = m.readTree(rsp.getEntity().getContent());
                for (JsonNode res : o.withArray("Results")) {
                    Map<String, TimestampedValue<String>> rs = new HashMap<>();
                    rl.put(res.get("Name").asText(), rs);
                    if (res.has("Readings")) {
                        for (Iterator<String> f = res.get("Readings").fieldNames(); f.hasNext(); ) {
                            String reading = f.next();
                            String value = res.get("Readings").get(reading).get("Value").asText();
                            String date = res.get("Readings").get(reading).get("Time").asText();
                            Instant lastUpdate = null;
                            try {
                                lastUpdate = LocalDateTime.parse(date,
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                        .atZone(ZoneId.of(config.getTimezone()))
                                        .toInstant();
                            } catch (Exception e) {
                                log.error("Error parsing last update date", e);
                            }
                            log.debug("Reading found: {}:{} = {} at {}", res.get("Name"), reading, value, (lastUpdate == null) ? "null" : lastUpdate.toString());
                            rs.put(reading, new TimestampedValue<>(value, lastUpdate));
                        }
                    } else {
                        log.error("No readings {} {}", method.getURI(), (new ObjectMapper()).writeValueAsString(res));
                    }
                }
            } else {
                log.error("Status code from {}: {} {}", method.getURI(), statusCode, rsp.getStatusLine().getReasonPhrase());
            }
        } catch (IOException | NullPointerException e) {
            log.error("Error getting readings from {}", method.getURI(), e);
            return Collections.emptyMap();
        }

        rl.forEach((k, v) -> log.debug(k + "->" + String.join(",", v.keySet())));
        return rl;
    }

    protected static final String CSRF_HEADER = "X-FHEM-csrfToken";

    protected String getCsrfToken() {
        HttpGet method = new HttpGet(config.getUrl() + "?XHR=1");
        try (final CloseableHttpResponse rsp = client.execute(method)) {
            final int statusCode = rsp.getStatusLine().getStatusCode();
            // otherwise connection leak
            EntityUtils.consumeQuietly(rsp.getEntity());
            if (statusCode == HttpStatus.SC_OK) {
                if (rsp.containsHeader(CSRF_HEADER))
                    return rsp.getFirstHeader(CSRF_HEADER).getValue();
                else {
                    log.warn("No CSRF Token found {}", method.getURI());
                    return "";
                }
            }
        } catch (IOException | NullPointerException e) {
            log.error("Error getting CSRF token from {}", method.getURI(), e);
            return "";
        }

        return "";
    }

    @Override
    public String getHost() {
        return URI.create(config.getUrl()).getHost();
    }

    @Override
    public String execute(String command) {
        HttpGet method = new HttpGet(config.getUrl() + "?cmd=" + URLEncoder.encode(command, Charset.defaultCharset())
                + "&XHR=1"
                + (config.isUseCsrf() ? "&fwcsrf=" + getCsrfToken() : ""));
        try (final CloseableHttpResponse rsp = client.execute(method)) {
            return EntityUtils.toString(rsp.getEntity(), "UTF-8");
        } catch (IOException | NullPointerException e) {
            log.error("Error getting readings from {}", method.getURI(), e);
            return null;
        }

    }
}