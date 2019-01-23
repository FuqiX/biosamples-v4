package uk.ac.ebi.biosamples.certification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Sample;

import java.util.concurrent.Callable;

public class CertificationCallable implements Callable<Void> {

    private Logger log = LoggerFactory.getLogger(getClass());

    private final RestTemplate restTemplate;

    private final Sample sample;

    public CertificationCallable(RestTemplate restTemplate, Sample sample) {
        this.restTemplate = restTemplate;
        this.sample = sample;
    }

    @Override
    public Void call() throws Exception {
        try {
            log.info(restTemplate.postForObject("http://localhost:8080/certify", sample, String.class));
        }
        catch (RestClientException e) {
            e.printStackTrace();
        }
        return null;
    }
}
