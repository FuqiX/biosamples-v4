package uk.ac.ebi.biosamples.ena;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.biosamples.Application;
import uk.ac.ebi.biosamples.BioSamplesProperties;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Sample;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {EnaRunnerTest.TestConfig.class})
public class EnaRunnerTest {

    @Autowired
    private EnaRunner enaRunner;

    private static final Logger log = LoggerFactory.getLogger(EnaRunnerTest.class);

    @Configuration
    @Import(Application.class)
    public static class TestConfig {

        @Bean
        public BioSamplesClient bioSamplesClient() {
            return new MockBioSamplesClient();
        }

        @Autowired
        private RestTemplateBuilder restTemplateBuilder;

        private BioSamplesProperties bioSamplesProperties = new BioSamplesProperties() {

            @Override
            public int getBiosamplesClientThreadCount() {
                return 1;
            }

            @Override
            public int getBiosamplesClientThreadCountMax() {
                return 8;
            }

            @Override
            public URI getBiosamplesClientUri() {
                try {
                    return new URI("");
                } catch (URISyntaxException e) {
                    throw new RuntimeException();
                }
            }
        };

        private class MockBioSamplesClient extends BioSamplesClient {

            private BufferedWriter writer;

            public MockBioSamplesClient() {
                super(bioSamplesProperties.getBiosamplesClientUri(), restTemplateBuilder, null, null, bioSamplesProperties);
                try {
                    writer = Files.newBufferedWriter(Paths.get("output.txt"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public Future<Resource<Sample>> persistSampleResourceAsync(Sample sample, Boolean setUpdateDate, Boolean setFullDetails) {
                try {
                    writer.write(sample.toString() + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new Future<Resource<Sample>>() {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return false;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return false;
                    }

                    @Override
                    public Resource<Sample> get() throws InterruptedException, ExecutionException {
                        return null;
                    }

                    @Override
                    public Resource<Sample> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                        return null;
                    }
                };
            }

            @Override
            public void finalize() {
                try {
                    writer.close();
                    System.out.println("Closed BufferedWriter in the finalizer");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test
    public void run() throws Exception {
       // enaRunner.run(new DefaultApplicationArguments(new String[]{"from=2018-07-01"}));
    }
}
