package uk.ac.ebi.biosamples.curation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.hateoas.Resource;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biosamples.PipelinesProperties;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.model.filter.Filter;
import uk.ac.ebi.biosamples.ols.OlsProcessor;
import uk.ac.ebi.biosamples.service.CurationApplicationService;
import uk.ac.ebi.biosamples.utils.AdaptiveThreadPoolExecutor;
import uk.ac.ebi.biosamples.utils.ArgUtils;
import uk.ac.ebi.biosamples.utils.ThreadUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Component
public class CurationApplicationRunner implements ApplicationRunner {

    private Logger log = LoggerFactory.getLogger(getClass());

    private final BioSamplesClient bioSamplesClient;
    private final PipelinesProperties pipelinesProperties;
    private final OlsProcessor olsProcessor;
    private final CurationApplicationService curationApplicationService;

    public CurationApplicationRunner(BioSamplesClient bioSamplesClient,
                                     PipelinesProperties pipelinesProperties,
                                     OlsProcessor olsProcessor,
                                     CurationApplicationService curationApplicationService) {
        this.bioSamplesClient = bioSamplesClient;
        this.pipelinesProperties = pipelinesProperties;
        this.olsProcessor = olsProcessor;
        this.curationApplicationService = curationApplicationService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        Collection<Filter> filters = ArgUtils.getDateFilters(args);

        try (AdaptiveThreadPoolExecutor executorService = AdaptiveThreadPoolExecutor.create(100, 10000, true,
                pipelinesProperties.getThreadCount(), pipelinesProperties.getThreadCountMax())) {

            Map<String, Future<Void>> futures = new HashMap<>();
            long sampleCount = 0;
            for (Resource<Sample> sampleResource : bioSamplesClient.fetchSampleResourceAll("", filters)) {
                log.trace("Handling " + sampleResource);
                Sample sample = sampleResource.getContent();
                if (sample == null) {
                    throw new RuntimeException("Sample should not be null");
                }

                Callable<Void> task = new SampleCurationCallable(bioSamplesClient, sample, olsProcessor,
                        curationApplicationService, pipelinesProperties.getCurationDomain());
                sampleCount++;
                if (sampleCount % 500 == 0) {
                    log.info(sampleCount + " scheduled");
                }
                futures.put(sample.getAccession(), executorService.submit(task));
            }

            log.info("waiting for futures");
            // wait for anything to finish
            ThreadUtils.checkFutures(futures, 0);
        } finally {
            //now print a list of things that failed
            if (SampleCurationCallable.failedQueue.size() > 0) {
                //put the first ones on the queue into a list
                //limit the size of list to avoid overload
                List<String> fails = new LinkedList<>();
                while (fails.size() < 100 && SampleCurationCallable.failedQueue.peek() != null) {
                    fails.add(SampleCurationCallable.failedQueue.poll());
                }
                log.info("Failed files (" + SampleCurationCallable.failedQueue.size() + ") " + String.join(" , ", fails));
            }
        }
    }

}
