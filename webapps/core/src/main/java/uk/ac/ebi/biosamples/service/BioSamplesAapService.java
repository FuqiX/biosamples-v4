package uk.ac.ebi.biosamples.service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

import uk.ac.ebi.biosamples.BioSamplesProperties;
import uk.ac.ebi.biosamples.model.CurationLink;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.tsc.aap.client.model.Domain;
import uk.ac.ebi.tsc.aap.client.security.UserAuthentication;

@Service
public class BioSamplesAapService {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	private final Traverson traverson;
	private final BioSamplesProperties bioSamplesProperties;
	
	public BioSamplesAapService(RestTemplateBuilder restTemplateBuilder, BioSamplesProperties bioSamplesProperties) {
		traverson = new Traverson(bioSamplesProperties.getBiosamplesClientAapUri(), MediaTypes.HAL_JSON);
		traverson.setRestOperations(restTemplateBuilder.build());
		this.bioSamplesProperties = bioSamplesProperties;
	}

	@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Curation Link must specify a domain") // 400
	public static class CurationLinkDomainMissingException extends RuntimeException {
	}	

	@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Sample must specify a domain") // 400
	public static class DomainMissingException extends RuntimeException {
	}

	@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "This sample is private and not available for browsing. If you think this is an error and/or you should have access please contact the BioSamples Helpdesk at biosamples@ebi.ac.uk") // 403
	public static class SampleNotAccessibleException extends RuntimeException {
	}
	
	/**
	 * 
	 * Returns a set of domains that the current user has access to (uses thread-bound spring security)
	 * Always returns a set, even if its empty if not logged in
	 * 
	 * @return
	 */
	public Set<String> getDomains() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		log.trace("authentication = "+authentication);

		//not sure this can ever happen
		if (authentication == null) {
			return Collections.emptySet();

		} else if (authentication instanceof AnonymousAuthenticationToken) {
			return Collections.emptySet();
		} else if (authentication instanceof UserAuthentication) {
			
			UserAuthentication userAuthentication = (UserAuthentication) authentication;
					
			log.trace("userAuthentication = "+userAuthentication.getName());
			log.trace("userAuthentication = "+userAuthentication.getAuthorities());
			log.trace("userAuthentication = "+userAuthentication.getPrincipal());
			log.trace("userAuthentication = "+userAuthentication.getCredentials());

			Set<String> domains = new HashSet<>();			
			for (GrantedAuthority authority : userAuthentication.getAuthorities()) {
				if (authority instanceof Domain) {
					log.trace("Found domain "+authority);
					Domain domain = (Domain) authority;
	
					log.trace("domain.getDomainName() = "+domain.getDomainName());
					log.trace("domain.getDomainReference() = "+domain.getDomainReference());
					
					//NOTE this should use reference, but that is not populated in the tokens at the moment
					//domains.add(domain.getDomainReference());
					domains.add(domain.getDomainName());
				} else {
					log.warn("Found non-domain GrantedAuthority "+authority+" for user "+userAuthentication.getName());
				}
			}			
			return domains;
		} else {
			return Collections.emptySet();
		}
	}
	
	/**
	 * Function that checks if a sample has a domain the current user has access to,
	 * or if the user only has a single domain sets the sample to that domain.
	 * 
	 * May return a different version of the sample, so return needs to be stored in future for that sample.
	 * 
	 * @param sample
	 * @return
	 * @throws SampleNotAccessibleException
	 * @throws DomainMissingException
	 */
	public Sample handleSampleDomain(Sample sample) throws SampleNotAccessibleException, DomainMissingException {
		
		//get the domains the current user has access to
		Set<String> usersDomains = getDomains();
		
		if (sample.getDomain() == null || sample.getDomain().length() == 0) {
			//if the sample doesn't have a domain, and the user has one domain, then they must be submitting to that domain
			if (usersDomains.size() == 1) {
//				sample = Sample.build(sample.getName(), sample.getAccession(),
//						usersDomains.iterator().next(), sample.getRelease(), sample.getUpdate(),
//						sample.getAttributes(), sample.getRelationships(), sample.getExternalReferences(),
//						sample.getOrganizations(), sample.getContacts(), sample.getPublications());
                sample = Sample.Builder.fromSample(sample).withDomain(usersDomains.iterator().next()).build();
			} else {			
				//if the sample doesn't have a domain, and we can't guess one, then end
				throw new DomainMissingException();
			}
		}

		//check sample is assigned to a domain that the authenticated user has access to
		if (usersDomains.contains(bioSamplesProperties.getBiosamplesAapSuperWrite())) {
			return sample;
		} else if (usersDomains.contains(sample.getDomain())) {
			return sample;
		} else {
			log.warn("User asked to submit sample to domain "+sample.getDomain()+" but has access to "+usersDomains);
			throw new SampleNotAccessibleException();
		}
	}

	/**
	 * Function that checks if a CurationLink has a domain the current user has access to,
	 * or if the user only has a single domain sets theCurationLink to that domain.
	 * 
	 * May return a different version of the CurationLink, so return needs to be stored in future for that CurationLink.
	 * 
	 * @param sample
	 * @return
	 * @throws SampleNotAccessibleException
	 * @throws DomainMissingException
	 */
	public CurationLink handleCurationLinkDomain(CurationLink curationLink) throws CurationLinkDomainMissingException {
		
		//get the domains the current user has access to
		Set<String> usersDomains = getDomains();
		
		if (curationLink.getDomain() == null || curationLink.getDomain().length() == 0) {
			//if the sample doesn't have a domain, and the user has one domain, then they must be submitting to that domain
			if (usersDomains.size() == 1) {
				curationLink = CurationLink.build(curationLink.getSample(), curationLink.getCuration(), 
						usersDomains.iterator().next(), curationLink.getCreated());
			} else {			
				//if the sample doesn't have a domain, and we can't guess one, then end
				throw new CurationLinkDomainMissingException();
			}
		}

		//check sample is assigned to a domain that the authenticated user has access to
		if (usersDomains.contains(bioSamplesProperties.getBiosamplesAapSuperWrite())) {
			return curationLink;
		} else if (usersDomains.contains(curationLink.getDomain())) {
			return curationLink;
		} else {
			log.info("User asked to submit curation to domain "+curationLink.getDomain()+" but has access to "+usersDomains);
			throw new SampleNotAccessibleException();
		}
		
	}
	
	public boolean isReadSuperUser() {
		return getDomains().contains(bioSamplesProperties.getBiosamplesAapSuperRead());
	}

	public boolean isWriteSuperUser() {
		return getDomains().contains(bioSamplesProperties.getBiosamplesAapSuperWrite());
	}
	
	public void checkAccessible(Sample sample) throws SampleNotAccessibleException {
		//TODO throw different exceptions in different situations
		if (sample.getRelease().isBefore(Instant.now())) {
			//release date in past, accessible
		} else if (getDomains().contains(bioSamplesProperties.getBiosamplesAapSuperRead())) {
			//if the current user belongs to a super read domain, accessible
		} else if (getDomains().contains(sample.getDomain())) {
			//if the current user belongs to a domain that owns the sample, accessible
		} else {
			throw new SampleNotAccessibleException();
		}
	}
}
