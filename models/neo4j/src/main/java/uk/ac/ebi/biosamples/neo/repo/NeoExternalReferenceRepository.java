package uk.ac.ebi.biosamples.neo.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.annotation.Depth;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;

import uk.ac.ebi.biosamples.neo.model.NeoExternalReference;
import uk.ac.ebi.biosamples.neo.model.NeoSample;

public interface NeoExternalReferenceRepository extends Neo4jRepository<NeoExternalReference,String> {

	public NeoExternalReference findOneByUrlHash(String accession, @Depth int depth);
	public NeoExternalReference findOneById(Long id, @Depth int depth);

	@Query("MATCH (s:Sample)-(:ExternalReferenceLink)-(x:ExternalReference) WHERE s.accession={accession} RETURN x")
	public Page<NeoExternalReference> findBySampleAccession(@Param("accession") String accession, Pageable pageable);
	
}
