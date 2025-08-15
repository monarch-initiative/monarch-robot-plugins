package org.monarchinitiative.robot;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
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
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/**
 * The uPheno OBA Bridge command creates phenotype-to-trait relationships
 * by combining the logic of pheno_trait.ru and pheno_trait_materialise.ru
 */
public class uPhenoOBABridgeCommand extends BasePlugin {

    OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();

    public uPhenoOBABridgeCommand() {
        super("upheno-oba-bridge", "Create phenotype-to-trait relationships based on OBA trait components.",
                "robot upheno-oba-bridge --input <FILE> --output <FILE>");
        options.addOption("t", "term", true, "term to extract");
        options.addOption("T", "term-file", true, "load terms from a file");
        options.addOption("p", "root-phenotype", true, "select a single root phenotype to extract");
        options.addOption("P", "root-phenotype-file", true, "a file with root phenotype terms to extract");
    }

    /**
     * Get phenotypes based on command line options
     */
    private Set<OWLClass> getPhenotypes(CommandLine line, OWLOntology ontology) throws Exception {
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

    /**
     * Create phenotype-to-trait relationships equivalent to pheno_trait.ru and pheno_trait_materialise.ru
     */
    private Set<OWLAxiom> createPhenotypeTraitBridge(OWLOntology ontology, Set<OWLClass> targetPhenotypes) {
        Set<OWLAxiom> axioms = new HashSet<>();
        
        // IRIs for the relevant classes and properties
        IRI phenotypeRootIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_0001001");
        IRI obaTraitRootIRI = IRI.create("http://purl.obolibrary.org/obo/OBA_0000001");
        IRI hasPartIRI = IRI.create("http://purl.obolibrary.org/obo/BFO_0000051");
        IRI phenotypeToTraitIRI = IRI.create("http://purl.obolibrary.org/obo/UPHENO_phenotypeToTrait");
        
        OWLClass phenotypeRoot = dataFactory.getOWLClass(phenotypeRootIRI);
        OWLClass obaTraitRoot = dataFactory.getOWLClass(obaTraitRootIRI);
        OWLObjectProperty hasPart = dataFactory.getOWLObjectProperty(hasPartIRI);
        OWLObjectProperty phenotypeToTrait = dataFactory.getOWLObjectProperty(phenotypeToTraitIRI);
        
        // Declare the phenotypeToTrait property
        axioms.add(dataFactory.getOWLDeclarationAxiom(phenotypeToTrait));
        
        StructuralReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        // Get all phenotype classes (descendants of UPHENO_0001001)
        Set<OWLClass> phenotypes = reasoner.getSubClasses(phenotypeRoot, false).getFlattened();
        phenotypes.add(phenotypeRoot);
        
        // Get all OBA trait classes (descendants of OBA_0000001)  
        Set<OWLClass> obaTraits = reasoner.getSubClasses(obaTraitRoot, false).getFlattened();
        obaTraits.add(obaTraitRoot);
        
        // Filter to target phenotypes if specified
        if (targetPhenotypes != null && !targetPhenotypes.isEmpty()) {
            phenotypes.retainAll(targetPhenotypes);
        }
        
        // Step 1: Implement pheno_trait.ru logic
        // Find phenotypes that have parts that are OBA traits
        for (OWLClass phenotype : phenotypes) {
            for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSubClass(phenotype)) {
                if (axiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) axiom.getSuperClass();
                    if (restriction.getProperty().equals(hasPart) && 
                        restriction.getFiller() instanceof OWLClass) {
                        OWLClass filler = (OWLClass) restriction.getFiller();
                        
                        // Check if filler is an OBA trait
                        if (obaTraits.contains(filler) || reasoner.getSubClasses(obaTraitRoot, false).containsEntity(filler)) {
                            // Create phenotypeToTrait relationship
                            OWLObjectSomeValuesFrom phenotypeToTraitRestriction = 
                                dataFactory.getOWLObjectSomeValuesFrom(phenotypeToTrait, filler);
                            OWLSubClassOfAxiom newAxiom = 
                                dataFactory.getOWLSubClassOfAxiom(phenotype, phenotypeToTraitRestriction);
                            axioms.add(newAxiom);
                        }
                    }
                }
            }
        }
        
        // Step 2: Implement pheno_trait_materialise.ru logic
        // First, collect all phenotypeToTrait relationships we created in step 1
        Set<OWLSubClassOfAxiom> traitAxioms = new HashSet<>();
        for (OWLAxiom axiom : axioms) {
            if (axiom instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom subClassAxiom = (OWLSubClassOfAxiom) axiom;
                if (subClassAxiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) subClassAxiom.getSuperClass();
                    if (restriction.getProperty().equals(phenotypeToTrait)) {
                        traitAxioms.add(subClassAxiom);
                    }
                }
            }
        }
        
        // Propagate phenotypeToTrait relationships to descendants
        for (OWLSubClassOfAxiom traitAxiom : traitAxioms) {
            OWLClass phenotypeWithTrait = (OWLClass) traitAxiom.getSubClass();
            OWLObjectSomeValuesFrom restriction = (OWLObjectSomeValuesFrom) traitAxiom.getSuperClass();
            OWLClass trait = (OWLClass) restriction.getFiller();
            
            // Propagate to descendants
            Set<OWLClass> descendants = reasoner.getSubClasses(phenotypeWithTrait, false).getFlattened();
            for (OWLClass descendant : descendants) {
                // Check if descendant doesn't already have a more specific trait relationship
                boolean hasMoreSpecificTrait = false;
                
                // Check in existing ontology axioms
                for (OWLSubClassOfAxiom descendantAxiom : ontology.getSubClassAxiomsForSubClass(descendant)) {
                    if (descendantAxiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                        OWLObjectSomeValuesFrom descendantRestriction = (OWLObjectSomeValuesFrom) descendantAxiom.getSuperClass();
                        if (descendantRestriction.getProperty().equals(phenotypeToTrait) && 
                            descendantRestriction.getFiller() instanceof OWLClass) {
                            OWLClass descendantTrait = (OWLClass) descendantRestriction.getFiller();
                            if (reasoner.getSubClasses(trait, false).containsEntity(descendantTrait)) {
                                hasMoreSpecificTrait = true;
                                break;
                            }
                        }
                    }
                }
                
                // Also check in newly created axioms
                if (!hasMoreSpecificTrait) {
                    for (OWLAxiom newAxiom : axioms) {
                        if (newAxiom instanceof OWLSubClassOfAxiom) {
                            OWLSubClassOfAxiom newSubClassAxiom = (OWLSubClassOfAxiom) newAxiom;
                            if (newSubClassAxiom.getSubClass().equals(descendant) &&
                                newSubClassAxiom.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                                OWLObjectSomeValuesFrom newRestriction = (OWLObjectSomeValuesFrom) newSubClassAxiom.getSuperClass();
                                if (newRestriction.getProperty().equals(phenotypeToTrait) && 
                                    newRestriction.getFiller() instanceof OWLClass) {
                                    OWLClass newTrait = (OWLClass) newRestriction.getFiller();
                                    if (reasoner.getSubClasses(trait, false).containsEntity(newTrait)) {
                                        hasMoreSpecificTrait = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (!hasMoreSpecificTrait) {
                    OWLObjectSomeValuesFrom descendantRestriction = 
                        dataFactory.getOWLObjectSomeValuesFrom(phenotypeToTrait, trait);
                    OWLSubClassOfAxiom descendantAxiom = 
                        dataFactory.getOWLSubClassOfAxiom(descendant, descendantRestriction);
                    axioms.add(descendantAxiom);
                }
            }
        }
        
        return axioms;
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) {
        OWLOntology ontology = state.getOntology();
        
        try {
            Set<OWLClass> phenotypes = null;
            if (line.hasOption("term") || line.hasOption("term-file") || 
                line.hasOption("root-phenotype") || line.hasOption("root-phenotype-file")) {
                phenotypes = getPhenotypes(line, ontology);
            }
            
            Set<OWLAxiom> axioms = createPhenotypeTraitBridge(ontology, phenotypes);
            ontology.getOWLOntologyManager().addAxioms(ontology, axioms);
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Creating phenotype-trait bridge failed!", e);
        }
    }
}