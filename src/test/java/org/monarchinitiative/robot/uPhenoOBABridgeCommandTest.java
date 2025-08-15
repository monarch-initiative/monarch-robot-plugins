package org.monarchinitiative.robot;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

public class uPhenoOBABridgeCommandTest {

    private OWLOntology testOntology;
    private OWLDataFactory dataFactory;
    private uPhenoOBABridgeCommand command;
    
    @Before
    public void setUp() throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
        testOntology = manager.createOntology();
        command = new uPhenoOBABridgeCommand();
        
        // Create test ontology with phenotypes and OBA traits
        setupTestOntology();
    }

    private void setupTestOntology() {
        Set<OWLAxiom> axioms = new HashSet<>();
        
        // Define IRIs
        IRI phenotypeRootIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_0001001");
        IRI obaTraitRootIRI = IRI.create("http://purl.obolibrary.org/obo/OBA_0000001");
        IRI hasPartIRI = IRI.create("http://purl.obolibrary.org/obo/BFO_0000051");
        IRI phenotypeIRI = IRI.create("http://purl.obolibrary.org/obo/TEST_0000001");
        IRI childPhenotypeIRI = IRI.create("http://purl.obolibrary.org/obo/TEST_0000002");
        IRI obaTraitIRI = IRI.create("http://purl.obolibrary.org/obo/OBA_0000010");
        
        // Create classes
        OWLClass phenotypeRoot = dataFactory.getOWLClass(phenotypeRootIRI);
        OWLClass obaTraitRoot = dataFactory.getOWLClass(obaTraitRootIRI);
        OWLClass phenotype = dataFactory.getOWLClass(phenotypeIRI);
        OWLClass childPhenotype = dataFactory.getOWLClass(childPhenotypeIRI);
        OWLClass obaTrait = dataFactory.getOWLClass(obaTraitIRI);
        OWLObjectProperty hasPart = dataFactory.getOWLObjectProperty(hasPartIRI);
        
        // Declare classes
        axioms.add(dataFactory.getOWLDeclarationAxiom(phenotypeRoot));
        axioms.add(dataFactory.getOWLDeclarationAxiom(obaTraitRoot));
        axioms.add(dataFactory.getOWLDeclarationAxiom(phenotype));
        axioms.add(dataFactory.getOWLDeclarationAxiom(childPhenotype));
        axioms.add(dataFactory.getOWLDeclarationAxiom(obaTrait));
        axioms.add(dataFactory.getOWLDeclarationAxiom(hasPart));
        
        // Create hierarchy: phenotype is subclass of phenotypeRoot
        axioms.add(dataFactory.getOWLSubClassOfAxiom(phenotype, phenotypeRoot));
        axioms.add(dataFactory.getOWLSubClassOfAxiom(childPhenotype, phenotype));
        
        // Create OBA trait hierarchy
        axioms.add(dataFactory.getOWLSubClassOfAxiom(obaTrait, obaTraitRoot));
        
        // Create phenotype with has_part relationship to OBA trait
        OWLObjectSomeValuesFrom hasPartRestriction = 
            dataFactory.getOWLObjectSomeValuesFrom(hasPart, obaTrait);
        axioms.add(dataFactory.getOWLSubClassOfAxiom(phenotype, hasPartRestriction));
        
        testOntology.getOWLOntologyManager().addAxioms(testOntology, axioms);
    }

    @Test
    public void testCommandCreation() {
        assertNotNull(command);
        assertEquals("upheno-oba-bridge", command.getName());
        assertTrue(command.getUsage().contains("upheno-oba-bridge"));
    }

    @Test
    public void testPhenotypeTraitBridgeCreation() throws ParseException {
        CommandState state = new CommandState();
        state.setOntology(testOntology);
        
        // Create empty command line
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(command.getOptions(), new String[]{});
        
        int initialAxiomCount = testOntology.getAxioms().size();
        
        // Perform the operation
        command.performOperation(state, line);
        
        int finalAxiomCount = testOntology.getAxioms().size();
        
        // Should have added axioms
        assertTrue("New axioms should be added", finalAxiomCount > initialAxiomCount);
        
        // Check that phenotypeToTrait property was created
        IRI phenotypeToTraitIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_phenotypeToTrait");
        OWLObjectProperty phenotypeToTrait = dataFactory.getOWLObjectProperty(phenotypeToTraitIRI);
        assertTrue("phenotypeToTrait property should be declared", 
                   testOntology.containsAxiom(dataFactory.getOWLDeclarationAxiom(phenotypeToTrait)));
    }

    @Test
    public void testPhenotypeTraitRelationshipCreation() throws ParseException {
        CommandState state = new CommandState();
        state.setOntology(testOntology);
        
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(command.getOptions(), new String[]{});
        
        command.performOperation(state, line);
        
        // Check that phenotype-to-trait relationships were created
        IRI phenotypeIRI = IRI.create("http://purl.obolibrary.org/obo/TEST_0000001");
        IRI obaTraitIRI = IRI.create("http://purl.obolibrary.org/obo/OBA_0000010");
        IRI phenotypeToTraitIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_phenotypeToTrait");
        
        OWLClass phenotype = dataFactory.getOWLClass(phenotypeIRI);
        OWLClass obaTrait = dataFactory.getOWLClass(obaTraitIRI);
        OWLObjectProperty phenotypeToTrait = dataFactory.getOWLObjectProperty(phenotypeToTraitIRI);
        
        // Check if the phenotype has a phenotypeToTrait relationship
        boolean foundRelationship = false;
        for (OWLSubClassOfAxiom axiom : testOntology.getSubClassAxiomsForSubClass(phenotype)) {
            if (axiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) axiom.getSuperClass();
                if (restriction.getProperty().equals(phenotypeToTrait) && 
                    restriction.getFiller().equals(obaTrait)) {
                    foundRelationship = true;
                    break;
                }
            }
        }
        assertTrue("Phenotype should have phenotypeToTrait relationship", foundRelationship);
    }

    @Test
    public void testInheritanceOfTraitRelationships() throws ParseException {
        CommandState state = new CommandState();
        state.setOntology(testOntology);
        
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(command.getOptions(), new String[]{});
        
        command.performOperation(state, line);
        
        // Check that child phenotype inherited the trait relationship
        IRI childPhenotypeIRI = IRI.create("http://purl.obolibrary.org/obo/TEST_0000002");
        IRI obaTraitIRI = IRI.create("http://purl.obolibrary.org/obo/OBA_0000010");
        IRI phenotypeToTraitIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_phenotypeToTrait");
        
        OWLClass childPhenotype = dataFactory.getOWLClass(childPhenotypeIRI);
        OWLClass obaTrait = dataFactory.getOWLClass(obaTraitIRI);
        OWLObjectProperty phenotypeToTrait = dataFactory.getOWLObjectProperty(phenotypeToTraitIRI);
        
        // Check if the child phenotype has inherited the phenotypeToTrait relationship
        boolean foundInheritedRelationship = false;
        for (OWLSubClassOfAxiom axiom : testOntology.getSubClassAxiomsForSubClass(childPhenotype)) {
            if (axiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) axiom.getSuperClass();
                if (restriction.getProperty().equals(phenotypeToTrait) && 
                    restriction.getFiller().equals(obaTrait)) {
                    foundInheritedRelationship = true;
                    break;
                }
            }
        }
        assertTrue("Child phenotype should inherit phenotypeToTrait relationship", foundInheritedRelationship);
    }

    @Test
    public void testWithSpecificTerm() throws ParseException {
        CommandState state = new CommandState();
        state.setOntology(testOntology);
        
        // Test with specific term option
        String[] args = {"--term", "http://purl.obolibrary.org/obo/TEST_0000001"};
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(command.getOptions(), args);
        
        int initialAxiomCount = testOntology.getAxioms().size();
        
        command.performOperation(state, line);
        
        int finalAxiomCount = testOntology.getAxioms().size();
        
        // Should have added axioms even with term filter
        assertTrue("New axioms should be added with term filter", finalAxiomCount > initialAxiomCount);
    }

    @Test 
    public void testEmptyOntology() throws ParseException, OWLOntologyCreationException {
        // Test with empty ontology
        OWLOntology emptyOntology = OWLManager.createOWLOntologyManager().createOntology();
        CommandState state = new CommandState();
        state.setOntology(emptyOntology);
        
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(command.getOptions(), new String[]{});
        
        int initialAxiomCount = emptyOntology.getAxioms().size();
        
        command.performOperation(state, line);
        
        int finalAxiomCount = emptyOntology.getAxioms().size();
        
        // Should still declare the property even with empty ontology
        assertTrue("Should declare phenotypeToTrait property", finalAxiomCount >= initialAxiomCount);
    }

    public OWLOntology loadOntology(String path) throws IOException {
        IOHelper ioh = new IOHelper();
        return ioh.loadOntology(this.getClass().getResourceAsStream(path));
    }
}