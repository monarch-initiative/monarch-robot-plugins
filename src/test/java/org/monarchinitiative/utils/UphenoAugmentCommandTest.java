package org.monarchinitiative.utils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class UphenoAugmentCommandTest {

    private OWLOntology ontology;
    
    @Before
    public void setUp() {
        try {
            ontology = loadOntology("/hp.owl");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // set up other common objects, mocks, or configurations here
    }

    public OWLOntology loadOntology(String path) throws IOException {
        IOHelper ioh = new IOHelper();
        return ioh.loadOntology(this.getClass().getResourceAsStream(path));
    }

    @SuppressWarnings("null")
    @Test
    public void testAugmentRelations() throws OWLOntologyCreationException, IOException {
        OWLOntology ontology = null;
        ontology = OWLManager.createOWLOntologyManager().createOntology();
        ontology.getOWLOntologyManager().addAxioms(ontology, this.ontology.getAxioms());

        //OWLDataFactory fac = ontology.getOWLOntologyManager().getOWLDataFactory();
        List<String> relationships = new ArrayList<>();
        Set<OWLAxiom> axioms = new HashSet<>();
        Set<OWLClass> phenotypes = new HashSet<>();
        axioms = UphenoUtils.augment_relations(ontology, relationships, phenotypes);
        ontology.getOWLOntologyManager().addAxioms(ontology, axioms);

        // Test that nothing is augmented
        Set<OWLAxiom> axioms_expected = new HashSet<>(this.ontology.getAxioms());
        assertEquals(axioms_expected.size(), ontology.getAxioms().size());
        
        
        phenotypes.add(OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/HP_0000118")));


        axioms = UphenoUtils.augment_relations(ontology, relationships, phenotypes);
        ontology.getOWLOntologyManager().addAxioms(ontology, axioms);

    }

    // Additional tests as needed

}
