package uk.ac.ebi.biosamples.mongo.service;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

import uk.ac.ebi.biosamples.model.Relationship;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.mongo.model.MongoRelationship;
import uk.ac.ebi.biosamples.mongo.model.MongoSample;
import uk.ac.ebi.biosamples.mongo.repo.MongoSampleRepository;


public class MongoAccessionService {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final MongoSampleRepository mongoSampleRepository;
	private final SampleToMongoSampleConverter sampleToMongoSampleConverter;
	private final MongoSampleToSampleConverter mongoSampleToSampleConverter;
	private final String prefix;
	private final BlockingQueue<String> accessionCandidateQueue;;
	private long accessionCandidateCounter;
	
	
	public MongoAccessionService(MongoSampleRepository mongoSampleRepository, SampleToMongoSampleConverter sampleToMongoSampleConverter,
			MongoSampleToSampleConverter mongoSampleToSampleConverter, String prefix, long minimumAccession, int queueSize) {
		this.mongoSampleRepository = mongoSampleRepository;
		this.sampleToMongoSampleConverter = sampleToMongoSampleConverter;
		this.mongoSampleToSampleConverter = mongoSampleToSampleConverter;
		this.prefix = prefix;	
		this.accessionCandidateCounter = minimumAccession;
		this.accessionCandidateQueue = new LinkedBlockingQueue<>(queueSize);
	}

	public Sample generateAccession(Sample sample) {
		MongoSample mongoSample = sampleToMongoSampleConverter.convert(sample);
		mongoSample = accessionAndInsert(mongoSample);
		return mongoSampleToSampleConverter.convert(mongoSample);
	}
	
	private MongoSample accessionAndInsert(MongoSample sample) {
		log.trace("generating an accession");
		MongoSample originalSample = sample;
		// inspired by Optimistic Loops of
		// https://docs.mongodb.com/v3.0/tutorial/create-an-auto-incrementing-field/
		boolean success = false;
		// TODO limit number of tries
		while (!success) {
			// TODO add a timeout here
			try {
				sample = prepare(sample, accessionCandidateQueue.take());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			try {
				sample = mongoSampleRepository.insertNew(sample);
				success = true;
			} catch (MongoWriteException e) {
				if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
					success = false;
					sample = originalSample;
				} else {
					throw e;
				}
			}
		}
		log.debug("generated accession "+sample);
		return sample;
	}
	
	private MongoSample prepare(MongoSample sample, String accession) {
		SortedSet<MongoRelationship> relationships = sample.getRelationships();
		SortedSet<MongoRelationship> newRelationships = new TreeSet<>();
		for (MongoRelationship relationship : relationships) {
			//this relationship could not specify a source because the sample is unaccessioned
			//now we are assigning an accession, set the source to the accession
			if (relationship.getSource() == null || relationship.getSource().trim().length() == 0) {
				relationship = MongoRelationship.build(accession, relationship.getType(), relationship.getTarget());
			} else {
				newRelationships.add(relationship);
			}
		}
		sample = MongoSample.build(sample.getName(), accession, sample.getDomain(),
				sample.getRelease(), sample.getUpdate(), 
				sample.getAttributes(), relationships, sample.getExternalReferences(), 
				sample.getOrganizations(), sample.getContacts(), sample.getPublications());
		return sample;
	}

	@Scheduled(fixedDelay = 1000)
	public void prepareAccessions() {	
		//check that all accessions are still available		
		Iterator<String> it = accessionCandidateQueue.iterator();
		while (it.hasNext()) {
			String accessionCandidate = it.next();
			MongoSample sample = mongoSampleRepository.findOne(accessionCandidate);
			if (sample != null) {
				log.warn("Removing accession "+accessionCandidate+" from queue because now assigned");
				it.remove();
			}
		}
	
		
		while (accessionCandidateQueue.remainingCapacity() > 0) {
			log.debug("Adding more accessions to queue");
			String accessionCandidate = prefix + accessionCandidateCounter;
			// if the accession already exists, skip it
			if (mongoSampleRepository.exists(accessionCandidate)) {
				accessionCandidateCounter += 1;
				// if the accession can't be put in the queue at this time
				// (queue full), stop
			} else if (!accessionCandidateQueue.offer(accessionCandidate)) {
				return;
			} else {
				//put it into the queue, move on to next
				accessionCandidateCounter += 1;
			}
			log.trace("Added more accessions to queue");
		}
	}
}
