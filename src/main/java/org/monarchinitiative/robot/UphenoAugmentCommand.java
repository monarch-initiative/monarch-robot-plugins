package org.monarchinitiative.robot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.monarchinitiative.utils.UphenoUtils;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/**
 * The uPheno relationship toolkit enables adding relationships 
 * to an ontology based on logical EQ definition
 */
public class UphenoAugmentCommand extends BasePlugin {


    OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();

    public UphenoAugmentCommand() {
        super("extract-upheno-relations", "Adding content into phenotype ontologies based on EQ logical definitions.",
                "robot upheno-augment --input <FILE> --relation <TARGET> --output <FILE>");
        options.addOption("r", "relation", true, "what relationship should be augmented. Currently supported: UPHENO:0000001 (has phenotype), UPHENO:0000003 (phenotype is associated with entity), UPHENO:0000002 (has phenotypic analogue)");
        options.addOption("t", "term", true, "term to extract");
        options.addOption("T", "term-file", true, "load terms from a file");
        options.addOption("p", "root-phenotype", true, "select a single root phenotype to extract");
        options.addOption("P", "root-phenotype-file", true, "a file with root phenotype terms to extract");
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
        Set<OWLEntity> entities = entityIRIs.stream()
                        .map(iri -> dataFactory.getOWLClass(iri))
                        .collect(Collectors.toSet());
        objects.addAll(entities);
      }
    }
    if (line.hasOption("root-phenotype") || line.hasOption("root-phenotype-file")) {
        Set<IRI> entityIRIs = CommandLineHelper.getTerms(ioHelper, line, "root-phenotype", "root-phenotype-file");
        if (!entityIRIs.isEmpty()) {
            Set<OWLEntity> roots = entityIRIs.stream()
                        .map(iri -> dataFactory.getOWLClass(iri))
                        .collect(Collectors.toSet());
            Set<OWLClass> root_classes = roots.stream()
                                .filter(entity -> entity instanceof OWLClass)
                                .map(entity -> (OWLClass) entity)
                                .collect(Collectors.toSet());
            objects.addAll(root_classes);
            Set<OWLClassExpression> descendants = new HashSet<>();
            StructuralReasonerFactory reasonerFactory = new StructuralReasonerFactory();
            OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
            for (OWLClass root : root_classes) {
                descendants.addAll(reasoner.getSubClasses(root, false).getFlattened());
                //The below is too slow with huge ontologies.
                //descendants.addAll(UphenoUtils.getDescendants(ontology, root, visited));
            }
            Set<OWLClass> descendant_classes = descendants.stream()
                .filter(entity -> entity instanceof OWLClass)
                .map(entity -> (OWLClass) entity)
                .collect(Collectors.toSet());
            objects.addAll(descendant_classes);
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
