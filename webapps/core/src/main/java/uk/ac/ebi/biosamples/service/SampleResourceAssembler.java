package uk.ac.ebi.biosamples.service;

import java.util.List;
import java.util.Optional;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import uk.ac.ebi.biosamples.controller.SampleCurationLinksRestController;
import uk.ac.ebi.biosamples.controller.SampleRestController;
import uk.ac.ebi.biosamples.controller.SamplesRestController;
import uk.ac.ebi.biosamples.model.Sample;

/**
 * This class is used by Spring to add HAL _links for {@Link Sample} objects.
 * 
 * @author faulcon
 *
 */
@Service
public class SampleResourceAssembler implements ResourceAssembler<Sample, Resource<Sample>> {
	
	public static final String REL_CURATIONDOMAIN="curationDomain";
	public static final String REL_CURATIONLINKS="curationLinks";
	public static final String REL_CURATIONLINK="curationLink";
	
	public SampleResourceAssembler() {
	}

	private Link getSelfLink(String accession, Optional<Boolean> legacydetails, Optional<List<String>> curationDomains, Class controllerClass) {
    	UriComponentsBuilder uriComponentsBuilder = ControllerLinkBuilder.linkTo(controllerClass, accession).toUriComponentsBuilder();
    	if (legacydetails.isPresent() && legacydetails.get()) {
    		uriComponentsBuilder.queryParam("legacydetails", legacydetails);
    	}
    	if (curationDomains != null && curationDomains.isPresent()) {
    		if (curationDomains.get().size() == 0) {
    			uriComponentsBuilder.queryParam("curationdomain", (Object[])null);
    		} else {
        		for (String curationDomain : curationDomains.get()) {
        			uriComponentsBuilder.queryParam("curationdomain", curationDomain);
        		}
    		}
    	}
    	return new Link(uriComponentsBuilder.build().toUriString(), Link.REL_SELF);
    }
    
    private Link getCurationDomainLink(Link selfLink) {
		UriComponents selfUriComponents = UriComponentsBuilder.fromUriString(selfLink.getHref()).build();
		if (selfUriComponents.getQueryParams().size() == 0) {
			return new Link(selfLink.getHref()+"{?curationdomain}", REL_CURATIONDOMAIN);
		} else {
			return new Link(selfLink.getHref()+"{&curationdomain}", REL_CURATIONDOMAIN);
		}
    }
    
    private Link getCurationLinksLink(String accession) {
    	return ControllerLinkBuilder.linkTo(
			ControllerLinkBuilder.methodOn(SampleCurationLinksRestController.class)
				.getCurationLinkPageJson(accession, null, null)).withRel("curationLinks");
    }

    
    private Link getCurationLinkLink(String accession) {
		return ControllerLinkBuilder.linkTo(
				ControllerLinkBuilder.methodOn(SampleCurationLinksRestController.class)
					.getCurationLinkJson(accession, null)).withRel("curationLink");
    }
    
    public Resource<Sample> toResource(Sample sample, Optional<Boolean> legacydetails, Optional<List<String>> curationDomains, Class controllerClass) {
		Resource<Sample> sampleResource = new Resource<>(sample);
		sampleResource.add(getSelfLink(sample.getAccession(), legacydetails, curationDomains, controllerClass));
		//add link to select curation domain
		sampleResource.add(getCurationDomainLink(sampleResource.getLink(Link.REL_SELF)));				
		//add link to curationLinks on this sample
		sampleResource.add(getCurationLinksLink(sample.getAccession()));
		sampleResource.add(getCurationLinkLink(sample.getAccession()));
		return sampleResource;
    }

    public Resource<Sample> toResource(Sample sample, Optional<Boolean> legacydetails, Optional<List<String>> curationDomains) {
		Class controllerClass = SampleRestController.class;
		return toResource(sample, legacydetails, curationDomains, controllerClass);
    }

	public Resource<Sample> toResource(Sample sample, Class controllerClass) {
		return toResource(sample, Optional.empty(), Optional.empty(), controllerClass);
	}
    
	@Override
	public Resource<Sample> toResource(Sample sample) {
		Class controllerClass = SampleRestController.class;
		return toResource(sample, controllerClass);
	}

}