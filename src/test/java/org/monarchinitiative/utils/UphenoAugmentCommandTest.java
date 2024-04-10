package org.monarchinitiative.utils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.vocabulary.OWL;
import org.junit.Before;
import org.junit.Test;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

public class UphenoAugmentCommandTest {

    private OWLOntology hp_ontology;
    //private OWLOntology phenio_ontology;
    
    @Before
    public void setUp() {
        try {
            hp_ontology = loadOntology("/hp.owl");
            //phenio_ontology = loadOntology("/phenio.owl");
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
        //runAugmentationTestOnOntology(this.phenio_ontology);
        runAugmentationTestOnOntology(this.hp_ontology);
    }

    @SuppressWarnings("null")
    @Test
    public void runPhenotypeBranchTest() throws OWLOntologyCreationException, IOException {
        //runPhenotypeBranchTest(this.phenio_ontology);
        runPhenotypeBranchTest(this.hp_ontology);
    }


    private void runPhenotypeBranchTest(OWLOntology source) throws OWLOntologyCreationException, IOException {
        OWLOntology ontology = OWLManager.createOWLOntologyManager().createOntology();
        ontology.getOWLOntologyManager().addAxioms(ontology, source.getAxioms());

        OWLDataFactory fac = ontology.getOWLOntologyManager().getOWLDataFactory();

        List<OWLClass> roots = new ArrayList<>();
        roots.add(fac.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/HP_0000118")));

        Set<OWLClassExpression> descendants = new HashSet<>();
            StructuralReasonerFactory reasonerFactory = new StructuralReasonerFactory();
            OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
            for (OWLClass root : roots) {
                descendants.addAll(reasoner.getSubClasses(root, false).getFlattened());
            //descendants.addAll(UphenoUtils.getDescendants(ontology, root, visited));
            }
        
        assertTrue(descendants.size()>10);
    }

    private void runAugmentationTestOnOntology(OWLOntology source) throws OWLOntologyCreationException, IOException {
        OWLOntology ontology = OWLManager.createOWLOntologyManager().createOntology();
        ontology.getOWLOntologyManager().addAxioms(ontology, source.getAxioms());

        //OWLDataFactory fac = ontology.getOWLOntologyManager().getOWLDataFactory();
        List<String> relationships = new ArrayList<>();
        relationships.add("UPHENO:0000001");
        Set<OWLAxiom> axioms = new HashSet<>();
        Set<OWLClass> phenotypes = new HashSet<>();
        axioms = UphenoUtils.augment_relations(ontology, relationships, phenotypes);
        ontology.getOWLOntologyManager().addAxioms(ontology, axioms);

        // Test that nothing is augmented
        Set<OWLAxiom> axioms_expected = new HashSet<>(source.getAxioms());
        assertEquals(axioms_expected.size(), ontology.getAxioms().size());
        
        
        phenotypes.add(OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/HP_0002715")));


        axioms = UphenoUtils.augment_relations(ontology, relationships, phenotypes);
        ontology.getOWLOntologyManager().addAxioms(ontology, axioms);
        assertEquals(axioms_expected.size()+1, ontology.getAxioms().size());
    }

    // Additional tests as needed

}
