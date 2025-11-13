package qengine.storage;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;

import fr.boreal.model.logicalElements.api.Atom;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.HexaStoreSearchTree;
import qengine.util.Result;
import qengine.util.TermEncoder;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet,
 * Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat)
 * et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {

    private final HashMap<String, Integer> convertedTerms = new HashMap<>();
    private final TermEncoder termEncoder = new TermEncoder();

    private final HexaStoreSearchTree<Integer> S_O_P = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> S_P_O = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> P_S_O = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> P_O_S = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> O_P_S = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> O_S_P = new HexaStoreSearchTree<>();

    private static final int SUBJECT_IS_PRESENT = 4;
    private static final int PREDICAT_IS_PRESENT = 2;
    private static final int OBJECT_IS_PRESENT = 1;

    private void loadTerm(Set<String> rawTerms) {
        for (String rawTerm : rawTerms) {
            convertedTerms.put(rawTerm, termEncoder.encode(rawTerm));
        }
    }

    public void loadPersistentData(String path) throws FileNotFoundException {
        FileReader rdfFile = new FileReader(path);
        Set<String> rawTerms = new HashSet<>();
        List<RDFAtom> atoms = new ArrayList<>();

        try (RDFAtomParser rdfAtomParser = new RDFAtomParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (rdfAtomParser.hasNext()) {
                RDFAtom atom = rdfAtomParser.next();
                rawTerms.add(atom.getTripleSubject().label());
                rawTerms.add(atom.getTriplePredicate().label());
                rawTerms.add(atom.getTripleObject().label());
                atoms.add(atom);
            }
            loadTerm(rawTerms);

            for (RDFAtom atom : atoms) {
                add(atom);
            }
        }
    }

    @Override
    public boolean add(RDFAtom atom) {
        var subjectLabel = atom.getTripleSubject().label();
        var predicateLabel = atom.getTriplePredicate().label();
        var objectLabel = atom.getTripleObject().label();

        if (!convertedTerms.containsKey(subjectLabel)) {
            convertedTerms.put(subjectLabel, termEncoder.encode(subjectLabel));
        }
        if (!convertedTerms.containsKey(predicateLabel)) {
            convertedTerms.put(predicateLabel, termEncoder.encode(predicateLabel));
        }
        if (!convertedTerms.containsKey(objectLabel)) {
            convertedTerms.put(objectLabel, termEncoder.encode(objectLabel));
        }

        int subject = convertedTerms.get(subjectLabel);
        int predicate = convertedTerms.get(predicateLabel);
        int object = convertedTerms.get(objectLabel);

        S_O_P.computeIfAbsent(subject, k -> new HashMap<>())
                .computeIfAbsent(object, k -> new ArrayList<>())
                .add(predicate);

        S_P_O.computeIfAbsent(subject, k -> new HashMap<>())
                .computeIfAbsent(predicate, k -> new ArrayList<>())
                .add(object);

        P_S_O.computeIfAbsent(predicate, k -> new HashMap<>())
                .computeIfAbsent(subject, k -> new ArrayList<>())
                .add(object);

        P_O_S.computeIfAbsent(predicate, k -> new HashMap<>())
                .computeIfAbsent(object, k -> new ArrayList<>())
                .add(subject);

        O_P_S.computeIfAbsent(object, k -> new HashMap<>())
                .computeIfAbsent(predicate, k -> new ArrayList<>())
                .add(subject);

        O_S_P.computeIfAbsent(object, k -> new HashMap<>())
                .computeIfAbsent(subject, k -> new ArrayList<>())
                .add(predicate);

        return true;
    }

    public int getAvailableTerms(RDFAtom atom) {
        Term subject = atom.getTripleSubject();
        Term predicate = atom.getTriplePredicate();
        Term object = atom.getTripleObject();
        int res = 0;
        if (!subject.isVariable()) {
            res += SUBJECT_IS_PRESENT;
        }
        if (!predicate.isVariable()) {
            res += PREDICAT_IS_PRESENT;
        }
        if (!object.isVariable()) {
            res += OBJECT_IS_PRESENT;
        }
        return res;
    }

    public Result<HexaStoreSearchTree<Integer>> selectOptimalSearchTree(int state) {

        if ((state | SUBJECT_IS_PRESENT) > 0) {
            if ((state | OBJECT_IS_PRESENT) > 0) {
                return Result.success(S_O_P);
            } else {
                return Result.success(S_P_O);
            }
        }
        if ((state | PREDICAT_IS_PRESENT) > 0) {
            if ((state | OBJECT_IS_PRESENT) > 0) {
                return Result.success(P_O_S);
            } else {
                return Result.success(P_S_O);
            }
        }
        if ((state | OBJECT_IS_PRESENT) > 0) {
            if ((state | SUBJECT_IS_PRESENT) > 0) {
                return Result.success(O_S_P);
            } else {
                return Result.success(O_P_S);
            }
        }
        return Result.failure();
    }

    @Override
    public long size() {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<Substitution> match(RDFAtom atom) {
        int state = getAvailableTerms(atom);

        if (state == 0) {
            Logger.getLogger("system").warning("At least one term must be specified for matching.");
            return Collections.emptyIterator();
        }

        if (state == (SUBJECT_IS_PRESENT | PREDICAT_IS_PRESENT | OBJECT_IS_PRESENT)) {
            Logger.getLogger("system").warning("All terms are specified; no variables to substitute.");
            return Collections.emptyIterator();
        }

        Result<HexaStoreSearchTree<Integer>> treeResult = selectOptimalSearchTree(state);
        if (treeResult.failed()) {
            Logger.getLogger("system").warning("Failed to select optimal search tree");
            return Collections.emptyIterator();
        }

        HexaStoreSearchTree<Integer> optimalTree = treeResult.value();

        if ((state & SUBJECT_IS_PRESENT) != 0) {
            return matchWithSubject(atom, state, optimalTree);
        }
        if ((state & PREDICAT_IS_PRESENT) != 0) {
            return matchWithPredicate(atom, state, optimalTree);
        }
        if ((state & OBJECT_IS_PRESENT) != 0) {
            return matchWithObject(atom, state, optimalTree);
        }

        return Collections.emptyIterator();
    }

    private Iterator<Substitution> matchWithSubject(RDFAtom atom, int state, HexaStoreSearchTree<Integer> tree) {
        int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & OBJECT_IS_PRESENT) != 0) {
            int convertedObject = convertedTerms.get(atom.getTripleObject().label());
            addSubstitutions(tree.get(convertedSubject).get(convertedObject), atom.getTriplePredicate(), res);
        } else if ((state & PREDICAT_IS_PRESENT) != 0) {
            int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
            addSubstitutions(tree.get(convertedSubject).get(convertedPredicate), atom.getTripleObject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedSubject), atom.getTriplePredicate(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }

    private Iterator<Substitution> matchWithPredicate(RDFAtom atom, int state, HexaStoreSearchTree<Integer> tree) {
        int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & SUBJECT_IS_PRESENT) != 0) {
            int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
            addSubstitutions(tree.get(convertedPredicate).get(convertedSubject), atom.getTripleObject(), res);
        } else if ((state & OBJECT_IS_PRESENT) != 0) {
            int convertedObject = convertedTerms.get(atom.getTripleObject().label());
            addSubstitutions(tree.get(convertedPredicate).get(convertedObject), atom.getTripleSubject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedPredicate), atom.getTripleSubject(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }

    private Iterator<Substitution> matchWithObject(RDFAtom atom, int state, HexaStoreSearchTree<Integer> tree) {
        int convertedObject = convertedTerms.get(atom.getTripleObject().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & SUBJECT_IS_PRESENT) != 0) {
            int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
            addSubstitutions(tree.get(convertedObject).get(convertedSubject), atom.getTriplePredicate(), res);
        } else if ((state & PREDICAT_IS_PRESENT) != 0) {
            int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
            addSubstitutions(tree.get(convertedObject).get(convertedPredicate), atom.getTripleSubject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedObject), atom.getTriplePredicate(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }


    private void addSubstitutions(List<Integer> encodedValues, Term variable, List<Substitution> res) {
        assert variable.isVariable() : "Term must be a variable";
        Variable castedVariable = (Variable) variable;
        
        for (Integer encoded : encodedValues) {
            String decoded = termEncoder.decode(encoded);
            var substitution = new SubstitutionImpl();
            Term substitutionTerm = new TermImpl(decoded);
            substitution.add(castedVariable, substitutionTerm);
            res.add(substitution);
        }
    }


    private void addAllSubstitutions(Map<Integer, List<Integer>> entries, Term firstVariable, Term secondVariable,
            List<Substitution> res) {
        assert firstVariable.isVariable() : "First term must be a variable";
        assert secondVariable.isVariable() : "Second term must be a variable";
        
        Variable castedFirstVariable = (Variable) firstVariable;
        Variable castedSecondVariable = (Variable) secondVariable;
        
        for (Map.Entry<Integer, List<Integer>> entry : entries.entrySet()) {
            String decodedFirst = termEncoder.decode(entry.getKey());
            Term firstTerm = new TermImpl(decodedFirst);
            
            for (Integer secondEncoded : entry.getValue()) {
                String decodedSecond = termEncoder.decode(secondEncoded);
                Term secondTerm = new TermImpl(decodedSecond);
                
                // Create a single substitution with both variable mappings
                var substitution = new SubstitutionImpl();
                substitution.add(castedFirstVariable, firstTerm);
                substitution.add(castedSecondVariable, secondTerm);
                res.add(substitution);
            }
        }
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
    }

    @Override
    public Collection<Atom> getAtoms() {
        Collection<Atom> res = new ArrayList<>();
        for (var entry : S_P_O.entrySet()) {
            var subject = termEncoder.decode(entry.getKey());
            for (var predicateEntry : entry.getValue().entrySet()) {
                var predicate = termEncoder.decode(predicateEntry.getKey());
                for (var encodedObject : predicateEntry.getValue()) {
                    var object = termEncoder.decode(encodedObject);
                    Term subjectTerm = new TermImpl(subject);
                    Term predicateTerm = new TermImpl(predicate);
                    Term objectTerm = new TermImpl(object);
                    res.add(new RDFAtom(subjectTerm, predicateTerm, objectTerm));
                }
            }
        }
        return res;
    }

    private static class TermImpl implements Term {

        private final String label;

        public TermImpl(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }

    }
}
