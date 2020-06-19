package io.github.qsolutionsde.jfhem.rest;

import io.github.qsolutionsde.jfhem.data.TimestampedValue;
import io.github.qsolutionsde.jfhem.http.FHEMHttpConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fhem")
public class FHEMRestController {
    protected final Map<String, FHEMHttpConnection> hosts = new HashMap<>();

    public FHEMRestController(List<FHEMHttpConnection> cs) {
        cs.forEach(c -> hosts.put(c.getHost(),c));
    }

    @GetMapping(path="/{host}/{device}")
    public Map<String,TimestampedValue<String>> getDeviceReadings(@PathVariable("host") String host, @PathVariable("device") String device) {
        if (!hosts.containsKey(host))
            throw new NotFoundException();

        return hosts.get(host).getReadings(device);
    }

    @PostMapping(path="/{host}/{device}/{reading}")
    public String executeCommand(@PathVariable("host") String host,
                                 @PathVariable("device") String device,
                                 @PathVariable(name = "reading", required = false) String reading,
                                 HttpEntity<String> httpEntity) {
        if (!hosts.containsKey(host))
            throw new NotFoundException();

        String value = httpEntity.getBody();

        return hosts.get(host).execute("set " + device + (reading == null ? "" : " " + reading) + " " + value);
    }

    @PutMapping(path="/{host}/{device}/{reading}")
    public String setReading(@PathVariable("host") String host, @PathVariable("device") String device, @PathVariable("reading") String reading, HttpEntity<String> httpEntity ) {
        if (!hosts.containsKey(host))
            throw new NotFoundException();

        String value = httpEntity.getBody();

        return hosts.get(host).execute("setreading " + device + (reading == null ? "" : " " + reading) + " " + value);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public class NotFoundException extends RuntimeException {}

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public class BadRequestException extends RuntimeException {}

}
