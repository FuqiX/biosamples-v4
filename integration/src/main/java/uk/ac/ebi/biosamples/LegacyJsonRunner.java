package uk.ac.ebi.biosamples;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.ExternalReference;
import uk.ac.ebi.biosamples.model.Relationship;
import uk.ac.ebi.biosamples.model.Sample;

@SuppressWarnings("Duplicates")
//@Component
@Order(5)
@Profile({"default", "rest", "test"})
public class LegacyJsonRunner implements ApplicationRunner, ExitCodeGenerator {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @SuppressWarnings("FieldCanBeLocal") private int exitCode = 1;

    private final BiosamplesRestOperations biosamplesCommonRest;

    public LegacyJsonRunner(BiosamplesRestOperations biosamplesCommonRest) {
        this.biosamplesCommonRest = biosamplesCommonRest;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        switch (Phase.readPhaseFromArguments(args)) {
            case ONE:
                phaseOne();
                break;
            case TWO:
                phaseTwo();
                break;
            default:

        }
    }


    /**
     * Input phase
     */
    private void phaseOne() throws URISyntaxException {
        Sample sample  = this.getSampleTest();
        doGetAndFail(sample);
        doPut(sample);
        doGetAndSuccess(sample);

    }

    /**
     * Read phase
     */
    private void phaseTwo() {

    }

    private Sample doGetAndSuccess(Sample sample) {
        ResponseEntity<Resource<Sample>> response = biosamplesCommonRest.doGet(sample);
        // check the status code is 200 success
        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            throw new RuntimeException("Expected a 200 response");
        }
        if (!sample.equals(response.getBody().getContent())) {
            log.info("sample = "+sample);
            log.info("response.getBody() = "+response.getBody());
            throw new RuntimeException("Expected response to equal submission");
        }

        return response.getBody().getContent();
    }

    private void doGetAndFail(Sample sample) {
        try {
            biosamplesCommonRest.doGet(sample);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                // we expect to get a 404 error
                return;
            } else {
                // we got something else
                throw e;
            }
        }
        throw new RuntimeException("Expected a 404 response");
    }

    private void doPut(Sample sample) {
       biosamplesCommonRest.doPut(sample);
    }

    private Sample getSampleTest() throws URISyntaxException {
        String name = "ERS1077923";
        String accession = "SAMEA3890789";
        LocalDateTime update = LocalDateTime.of(LocalDate.of(2016, 8, 6), LocalTime.of(11, 36, 57, 0));
        LocalDateTime release = LocalDateTime.of(LocalDate.of(2016, 8, 6), LocalTime.of(11, 36, 57, 0));

        SortedSet<Attribute> attributes = new TreeSet<>();
        attributes.add(
                Attribute.build("organism", "Staphylococcus aureus", "http://purl.obolibrary.org/obo/NCBITaxon_1280", null));
        attributes.add(Attribute.build("species", "Staphylococcus aureus", "http://purl.obolibrary.org/obo/NCBITaxon_1280", null));
        attributes.add(Attribute.build("synonym", "f6ac50b0-cc0c-11e5-bc69-3c4a9275d6c6", null, null));
        attributes.add(Attribute.build("synonym", "3660STDY6324084", null, null));

        SortedSet<Relationship> relationships = new TreeSet<>();
//        relationships.add(Relationship.build("derived from", "TEST2", "TEST1"));

        SortedSet<ExternalReference> externalReferences = new TreeSet<>();
        externalReferences.add(ExternalReference.build("http://www.ebi.ac.uk/ena/data/view/SAMEA3890789"));
        externalReferences.add(ExternalReference.build("http://www.ebi.ac.uk/ena/data/view/ERS1077923"));

        return Sample.build(name, accession, release, update, attributes, relationships, externalReferences);
    }


    @Override
    public int getExitCode() {
        return exitCode;
    }

}
