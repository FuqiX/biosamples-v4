package uk.ac.ebi.biosamples.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.ac.ebi.biosamples.models.Sample;

@JsonSerialize(using = MongoSampleSerializer.class)
@JsonDeserialize(using = MongoSampleDeserializer.class)
public class MongoSample implements Sample {

	@Id
	protected String id;

	protected String previousAccession;
	
	//TODO date

	protected String accession;
	protected String name;
	protected Map<String, Set<String>> keyValues;
	protected Map<String, Map<String, String>> ontologyTerms;
	protected Map<String, Map<String, String>> units;

	private MongoSample() {
		super();
	}

	@Override
	public String getAccession() {
		return accession;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Set<String> getAttributeTypes() {
		return keyValues.keySet();
	}

	@Override
	public Set<String> getAttributeValues(String type) {
		return keyValues.get(type);
	}

	@Override
	public String getAttributeUnit(String type, String value) {
		if (!units.containsKey(type)) {
			return null;
		}
		if (!units.get(type).containsKey(value)) {
			return null;
		}
		return units.get(type).get(value);
	}

	@Override
	public String getAttributeOntologyTerm(String type, String value) {
		if (!ontologyTerms.containsKey(type)) {
			return null;
		}
		if (!ontologyTerms.get(type).containsKey(value)) {
			return null;
		}
		return ontologyTerms.get(type).get(value);
	}

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		if (other == this)
			return true;
		if (!(other instanceof MongoSample))
			return false;
		MongoSample that = (MongoSample) other;
		if (Objects.equals(this.name, that.name) && Objects.equals(this.accession, that.accession)
				&& Objects.equals(this.previousAccession, that.previousAccession)
				&& Objects.equals(this.keyValues, that.keyValues) && Objects.equals(this.units, that.units)
				&& Objects.equals(this.ontologyTerms, that.ontologyTerms)) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, accession, previousAccession, keyValues, units, ontologyTerms);
	}

	public String getId() {
		return id;
	}

	public String getPreviousAccession() {
		return previousAccession;
	}

	public void doArchive() {
		// can only do this if we currently have an accession
		if (accession != null) {
			this.previousAccession = accession;
			this.accession = null;
		}
	}

	public static MongoSample createFrom(Sample source) {
		MongoSample sample = new MongoSample();
		sample.accession = source.getAccession();
		sample.name = source.getName();
		sample.keyValues = new HashMap<>();
		sample.units = new HashMap<>();
		sample.ontologyTerms = new HashMap<>();

		for (String type : source.getAttributeTypes()) {
			sample.keyValues.put(type, new HashSet<>());
			for (String value : source.getAttributeValues(type)) {
				sample.keyValues.get(type).add(value);

				if (source.getAttributeUnit(type, value) != null) {
					if (!sample.units.containsKey(type)) {
						sample.units.put(type, new HashMap<>());
					}
					sample.units.get(type).put(value, source.getAttributeUnit(type, value));
				}

				if (source.getAttributeOntologyTerm(type, value) != null) {
					if (!sample.ontologyTerms.containsKey(type)) {
						sample.ontologyTerms.put(type, new HashMap<>());
					}
					sample.ontologyTerms.get(type).put(value, source.getAttributeOntologyTerm(type, value));
				}
			}
		}

		return sample;
	}

	public static MongoSample createFrom(String id, String name, String accession, String previousAccession, Map<String, Set<String>> keyValues,
			Map<String, Map<String, String>> ontologyTerms, Map<String, Map<String, String>> units) {
		MongoSample simpleSample = new MongoSample();
		simpleSample.id = id;
		simpleSample.name = name;
		simpleSample.accession = accession;
		simpleSample.previousAccession = previousAccession;
		simpleSample.keyValues = keyValues;
		simpleSample.ontologyTerms = ontologyTerms;
		simpleSample.units = units;
		return simpleSample;
	}

}
