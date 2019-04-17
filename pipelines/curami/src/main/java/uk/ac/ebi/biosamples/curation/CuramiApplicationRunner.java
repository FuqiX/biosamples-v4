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
import uk.ac.ebi.biosamples.mongo.model.MongoCurationRule;
import uk.ac.ebi.biosamples.mongo.repo.MongoCurationRuleRepository;
import uk.ac.ebi.biosamples.utils.AdaptiveThreadPoolExecutor;
import uk.ac.ebi.biosamples.utils.ArgUtils;
import uk.ac.ebi.biosamples.utils.ThreadUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class CuramiApplicationRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CuramiApplicationRunner.class);

    private final BioSamplesClient bioSamplesClient;
    private final PipelinesProperties pipelinesProperties;
    private final Map<String, String> curationRules;
    private final MongoCurationRuleRepository repository;

    public CuramiApplicationRunner(BioSamplesClient bioSamplesClient,
                                   PipelinesProperties pipelinesProperties,
                                   MongoCurationRuleRepository repository) {
        this.bioSamplesClient = bioSamplesClient;
        this.pipelinesProperties = pipelinesProperties;
        this.repository = repository;
        this.curationRules = loadCurationRulesToMemory();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Collection<Filter> filters = ArgUtils.getDateFilters(args);

        try (AdaptiveThreadPoolExecutor executorService = AdaptiveThreadPoolExecutor.create(100, 10000, true,
                pipelinesProperties.getThreadCount(), pipelinesProperties.getThreadCountMax())) {

            Map<String, Future<Void>> futures = new HashMap<>();
            long sampleCount = 0;
            for (Resource<Sample> sampleResource : bioSamplesClient.fetchSampleResourceAll("", filters)) {
                LOG.trace("Handling {}", sampleResource);
                Sample sample = sampleResource.getContent();
                if (sample == null) {
                    throw new RuntimeException("Null sample found while traversing through all samples. " +
                            "This could be due to network error or data inconsistency");
                }

                Callable<Void> task = new SampleCuramiCallable(
                        bioSamplesClient, sample, pipelinesProperties.getCurationDomain(), curationRules);
                sampleCount++;
                if (sampleCount % 500 == 0) {
                    LOG.info("Scheduled sample count {}", sampleCount);
                }
                futures.put(sample.getAccession(), executorService.submit(task));
            }

            LOG.info("waiting for futures");
            ThreadUtils.checkFutures(futures, 0);
        } finally {
            LOG.error("Pipeline finished at {}", Instant.now());
        }
    }

    private Map<String, String> loadCurationRulesToMemory() {
        List<MongoCurationRule> mongoCurationRules = repository.findAll();
        return mongoCurationRules.stream()
                .collect(Collectors.toMap(MongoCurationRule::getAttributePre, MongoCurationRule::getAttributePost));
    }

}
