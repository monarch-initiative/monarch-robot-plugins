package org.monarchinitiative.robot;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.monarchinitiative.utils.UphenoUtils;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.IOHelper;
import org.obolibrary.robot.OntologyHelper;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * The uPheno relationship toolkit enables adding relationships 
 * to an ontology based on logical EQ definition
 */
public class UphenoAugmentCommand extends BasePlugin {

    public UphenoAugmentCommand() {
        super("upheno-augment", "Adding content into phenotype ontologies based on EQ logical definitions.",
                "robot upheno-augment --input <FILE> --relation <TARGET> --output <FILE>");
        options.addOption("r", "relation", true, "what relationship should be augmented");
    }

    /**
   * Given a command line, an IOHelper, an ontology, and a list of select groups, return the objects
   * from the ontology based on the select groups.
   *
   * @param line CommandLine to get options from
   * @param ioHelper IOHelper to get IRIs
   * @param ontology OWLOntology to get objects from
   * @param selectGroups List of select groups (lists of select options)
   * @return set of selected objects from the ontology
   * @throws Exception on issue getting terms or processing selects
   */
  private Set<OWLClass> getPhenotypes(
      CommandLine line, OWLOntology ontology)
      throws Exception {
    Set<OWLObject> objects = new HashSet<>();
    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    if (line.hasOption("term") || line.hasOption("term-file")) {
      Set<IRI> entityIRIs = CommandLineHelper.getTerms(ioHelper, line, "term", "term-file");
      if (!entityIRIs.isEmpty()) {
        objects.addAll(OntologyHelper.getEntities(ontology, entityIRIs));
      }
    }
    Set<OWLClass> classes = objects.stream().filter(x -> x instanceof OWLClass) 
    .map(object -> (OWLClass) object) 
    .collect(Collectors.toSet());
    return classes;
}

    @Override
    public void performOperation(CommandState state, CommandLine line) {
        OWLOntology ontology = state.getOntology();

        //OWLDataFactory fac = ontology.getOWLOntologyManager().getOWLDataFactory();
        List<String> relationships = CommandLineHelper.getOptionalValues(line, "relation");
        Set<OWLAxiom> axioms = new HashSet<>();
        try {
            Set<OWLClass> phenotypes = getPhenotypes(line, ontology);
            axioms = UphenoUtils.augment_relations(ontology, relationships, phenotypes);
        } catch (Exception e) {
            throw new IllegalArgumentException(
            "Augmenting phenotype relations failed!",
            e);
        }
        ontology.getOWLOntologyManager().addAxioms(ontology, axioms);
    }
}
