package uk.ac.ebi.biosamples.solr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biosamples.MessageContent;
import uk.ac.ebi.biosamples.Messaging;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.ols.OlsProcessor;
import uk.ac.ebi.biosamples.solr.model.SolrSample;
import uk.ac.ebi.biosamples.solr.repo.SolrSampleRepository;
import uk.ac.ebi.biosamples.solr.service.SampleToSolrSampleConverter;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class MessageHandlerSolr {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHandlerSolr.class);

    @Autowired
    private SolrSampleRepository repository;

    @Autowired
    private SampleToSolrSampleConverter sampleToSolrSampleConverter;

    @Autowired
    private OlsProcessor olsProcessor;

    @RabbitListener(queues = Messaging.queueToBeIndexedSolr)
    public void handle(MessageContent messageContent) throws Exception {

        if (messageContent.getSample() == null) {
            LOGGER.warn("received message without sample");
            return;
        }

        Sample sample = messageContent.getSample();
        handleSample(sample, messageContent.getCreationTime());
        for (Sample related : messageContent.getRelated()) {
            handleSample(related, messageContent.getCreationTime());
        }

    }

    private void handleSample(Sample sample, String modifiedTime) {
        if (isIndexingCandidate(sample)) {
            SolrSample solrSample = sampleToSolrSampleConverter.convert(sample);
            //add the modified time to the solrSample
            String indexedTime = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT);

            solrSample = SolrSample.build(solrSample.getName(), solrSample.getAccession(), solrSample.getDomain(),
                    solrSample.getRelease(), solrSample.getUpdate(),
                    modifiedTime, indexedTime,
                    solrSample.getAttributeValues(), solrSample.getAttributeIris(), solrSample.getAttributeUnits(),
                    solrSample.getOutgoingRelationships(), solrSample.getIncomingRelationships(),
                    solrSample.getExternalReferencesData(), solrSample.getKeywords());

            //expand ontology terms from OLS
            for (List<String> iris : solrSample.getAttributeIris().values()) {
                for (String iri : iris) {
                    solrSample.getKeywords().addAll(olsProcessor.ancestorsAndSynonyms("efo", iri));
                    solrSample.getKeywords().addAll(olsProcessor.ancestorsAndSynonyms("NCBITaxon", iri));
                }
            }

            solrSample = repository.saveWithoutCommit(solrSample);
            LOGGER.info(String.format("added %s to index", sample.getAccession()));
        } else {
            if (repository.exists(sample.getAccession())) {
                repository.delete(sample.getAccession());
                LOGGER.info(String.format("removed %s from index", sample.getAccession()));
            }
        }
    }

    static boolean isIndexingCandidate(Sample sample) {
        for (Attribute attribute : sample.getAttributes()) {
            if (attribute.getType().equals("INSDC status")) {
                List<String> publicStatuses = Arrays.asList("public", "live");
                if (!publicStatuses.contains(attribute.getValue())) {
                    LOGGER.debug(String.format("not indexing %s as INSDC status is %s", sample.getAccession(), attribute.getValue()));
                    return false;
                }
            }
        }
        return true;
    }
}
