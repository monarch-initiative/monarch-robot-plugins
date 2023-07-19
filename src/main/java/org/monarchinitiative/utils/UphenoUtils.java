package org.monarchinitiative.utils;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Hello world!
 */
public class UphenoUtils {
    private final static OWLDataFactory df = OWLManager.getOWLDataFactory();

    /*
    public UphenoUtils(File ontology_file, File ontology_dir_out, File phenotype_list_file) throws IOException, OWLOntologyCreationException {
        this.ontology_file = ontology_file;
        this.ontology_dir_out = ontology_dir_out;
        FileUtils.readLines(phenotype_list_file,"utf-8").forEach(e->phenotypes.add(df.getOWLClass(IRI.create(e))));
        run();
    }
     */

    public static Set<OWLAxiom> augment_relations(OWLOntology o, Collection<String> relations, Set<OWLClass> phenotypes) throws IOException, OWLOntologyCreationException {
        Set<OWLAxiom> generated_axioms = new HashSet<>();
        for(String relation:relations) {
            log("Computing "+relation+" relations");
            switch (relation) {
                case "has_phenotype_affecting":
                    generated_axioms.addAll(adding_has_phenotype_affecting(o, phenotypes));
                    break;
                case "has_phenotypic_orthologue":
                    generated_axioms.addAll(adding_has_phenotypic_orthologue(o, phenotypes));
                    break;
                default:
                    break;
            }
        }
       return generated_axioms;
    }

    private static Set<OWLAxiom> adding_has_phenotypic_orthologue(OWLOntology o, Set<OWLClass> phenotypes) {

        Set<OWLAxiom> add = new HashSet<>();
        OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
        Set<OWLClass> unsat = new HashSet<>(r.getUnsatisfiableClasses().getEntities());
        for(OWLClass phenotype:phenotypes) {
            if(unsat.contains(phenotype)) {
                continue;
            }
            for(OWLClass equivalents:r.getEquivalentClasses(phenotype)) {
                if(!unsat.contains(phenotype)&&!equivalents.equals(phenotype)) {
                    OWLAnnotationAssertionAxiom ax_new = df.getOWLAnnotationAssertionAxiom(Entities.has_phenotypic_analogue,phenotype.getIRI(),equivalents.getIRI());
                    add.add(ax_new);
                }
            }
        }
        return add;
    }

    private static Set<OWLAxiom> adding_label_match(OWLOntology o, Set<OWLClass> phenotypes) {

        Set<OWLAxiom> add = new HashSet<>();
        OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
        Set<OWLClass> unsat = new HashSet<>(r.getUnsatisfiableClasses().getEntities());
        for(OWLClass phenotype:phenotypes) {
            if(unsat.contains(phenotype)) {
                continue;
            }
            for(OWLClass equivalents:r.getEquivalentClasses(phenotype)) {
                if(!unsat.contains(phenotype)&&!equivalents.equals(phenotype)) {
                    OWLAnnotationAssertionAxiom ax_new = df.getOWLAnnotationAssertionAxiom(Entities.has_phenotypic_analogue,phenotype.getIRI(),equivalents.getIRI());
                    add.add(ax_new);
                }
            }
        }
        return add;
    }

    private static Set<OWLAxiom> adding_has_phenotype_affecting(OWLOntology o, Set<OWLClass> phenotypes) {
        Set<OWLAxiom> add = new HashSet<>();
        for(OWLAxiom ax:o.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
            OWLEquivalentClassesAxiom eq = (OWLEquivalentClassesAxiom)ax;
            Set<OWLClass> named_cls = eq.getNamedClasses();
            OWLClass named = getOnlyNamedClass(named_cls);
            if (named_cls.size() == 1 && phenotypes.contains(named)) {
                for (OWLClassExpression cein : eq.getClassExpressions()) {
                    if (cein instanceof OWLObjectSomeValuesFrom) {
                        OWLObjectSomeValuesFrom ce = (OWLObjectSomeValuesFrom) cein;
                        if (!ce.getProperty().isAnonymous()) {
                            if (ce.getProperty().asOWLObjectProperty().equals(Entities.haspart)) {
                                if (processHasPart(add, named, ce)) break;
                            }
                        }
                    }
                }
            }
        }
        return add;
    }

    private static boolean processHasPart(Set<OWLAxiom> add, OWLClass named, OWLObjectSomeValuesFrom ce) {
        if (ce.getFiller() instanceof OWLObjectIntersectionOf) {
            OWLObjectIntersectionOf has_part_intersection = (OWLObjectIntersectionOf)ce.getFiller();
            for(OWLClassExpression has_part_intersection_operand:has_part_intersection.getOperands()) {
                if (has_part_intersection_operand instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom bearer_expression = (OWLObjectSomeValuesFrom) has_part_intersection_operand;
                    if (!bearer_expression.getProperty().isAnonymous()) {
                        OWLObjectProperty ii_po = bearer_expression.getProperty().asOWLObjectProperty();
                        if (ii_po.equals(Entities.inheres_in)||ii_po.equals(Entities.inheres_in_part_of)) {
                            OWLClassExpression bearer_filler = bearer_expression.getFiller();
                            if (!bearer_filler.equals(df.getOWLThing()) && !bearer_filler.equals(df.getOWLNothing())) {
                                OWLObjectSomeValuesFrom relation = df.getOWLObjectSomeValuesFrom(Entities.has_phenotype_affecting, bearer_filler);
                                OWLSubClassOfAxiom ax_new = df.getOWLSubClassOfAxiom(named, relation);
                                add.add(ax_new);
                            }
                            if (bearer_expression.isAnonymous()) {
                                for (OWLClass cbearer : bearer_expression.getClassesInSignature()) {
                                    OWLObjectSomeValuesFrom relation = df.getOWLObjectSomeValuesFrom(Entities.has_associated_entity, cbearer);
                                    OWLSubClassOfAxiom ax_new = df.getOWLSubClassOfAxiom(named, relation);
                                    add.add(ax_new);
                                }
                            } else {
                                OWLObjectSomeValuesFrom relation = df.getOWLObjectSomeValuesFrom(Entities.has_associated_entity, bearer_expression.asOWLClass());
                                OWLSubClassOfAxiom ax_new = df.getOWLSubClassOfAxiom(named, relation);
                                add.add(ax_new);
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static OWLClass getOnlyNamedClass(Set<OWLClass> named_cls) {
        OWLClass named = null;
        for(OWLClass n:named_cls) {
            named = n;
        }
        return named;
    }

    private static void log(Object o) {
        System.out.println(o.toString());
    }


    /*
    public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		String ontology_path = args[0];
        String ontology_path_out = args[1];
        String phenotype_list = args[2];

        //String ontology_path = "/data/hp.owl";
        //String ontology_path_out = "/data/hp-taxon.owl";
        //String phenotype_list = "";

        File ontology_file = new File(ontology_path);
        File ontology_dir_out = new File(ontology_path_out);
        File phenotype_list_file = new File(phenotype_list);

        new UphenoUtils(ontology_file, ontology_dir_out, phenotype_list_file);
    }
     */

}
