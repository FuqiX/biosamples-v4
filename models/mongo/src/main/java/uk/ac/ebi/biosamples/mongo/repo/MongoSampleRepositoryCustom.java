package uk.ac.ebi.biosamples.mongo.repo;

import uk.ac.ebi.biosamples.model.StaticViews;
import uk.ac.ebi.biosamples.mongo.model.MongoSample;

public interface MongoSampleRepositoryCustom {	
	
	public MongoSample insertNew(MongoSample sample);

	//to provide static view of samples
	public void insertSampleToCollection(MongoSample sample, StaticViews.MongoSampleStaticViews collectionName);
	public MongoSample findSampleFromCollection(String accession, StaticViews.MongoSampleStaticViews collectionName);
}