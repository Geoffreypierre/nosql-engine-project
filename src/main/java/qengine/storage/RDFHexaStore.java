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

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.Globals;
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

    private final TermEncoder termEncoder = new TermEncoder();

    private final HexaStoreSearchTree<Integer> S_O_P = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> S_P_O = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> P_S_O = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> P_O_S = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> O_P_S = new HexaStoreSearchTree<>();
    private final HexaStoreSearchTree<Integer> O_S_P = new HexaStoreSearchTree<>();

    private void loadTerm(Set<Term> rawTerms) {
        for (Term rawTerm : rawTerms) {
            termEncoder.encode(rawTerm);
        }
    }

    public void loadPersistentData(String path) throws FileNotFoundException {
        FileReader rdfFile = new FileReader(path);
        Set<Term> rawTerms = new HashSet<>();
        List<RDFAtom> atoms = new ArrayList<>();

        try (RDFAtomParser rdfAtomParser = new RDFAtomParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (rdfAtomParser.hasNext()) {
                RDFAtom atom = rdfAtomParser.next();
                rawTerms.add(atom.getTripleSubject());
                rawTerms.add(atom.getTriplePredicate());
                rawTerms.add(atom.getTripleObject());
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
        int subject = termEncoder.encode(atom.getTripleSubject());
        int predicate = termEncoder.encode(atom.getTriplePredicate());
        int object = termEncoder.encode(atom.getTripleObject());

        addToIndex(S_O_P, subject, object, predicate);
        addToIndex(S_P_O, subject, predicate, object);
        addToIndex(P_S_O, predicate, subject, object);
        addToIndex(P_O_S, predicate, object, subject);
        addToIndex(O_P_S, object, predicate, subject);
        addToIndex(O_S_P, object, subject, predicate);

        return true;
    }

    private void addToIndex(HexaStoreSearchTree<Integer> index, int key1, int key2, int value) {
        if (!index.containsKey(key1)) {
            index.put(key1, new HashMap<>());
        }
        if (!index.get(key1).containsKey(key2)) {
            index.get(key1).put(key2, new HashSet<>());
        }
        index.get(key1).get(key2).add(value);
    }

    public int getAvailableTerms(RDFAtom atom) {
        Term subject = atom.getTripleSubject();
        Term predicate = atom.getTriplePredicate();
        Term object = atom.getTripleObject();
        int res = 0;
        if (!subject.isVariable()) {
            res += Globals.SUBJECT_IS_PRESENT;
        }
        if (!predicate.isVariable()) {
            res += Globals.PREDICAT_IS_PRESENT;
        }
        if (!object.isVariable()) {
            res += Globals.OBJECT_IS_PRESENT;
        }
        return res;
    }

    public Result<HexaStoreSearchTree<Integer>> selectOptimalSearchTree(int availableTerms) {

        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) > 0) {
            if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
                return Result.success(S_O_P);
            } else {
                return Result.success(S_P_O);
            }
        }
        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) > 0) {
            if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
                return Result.success(P_O_S);
            } else {
                return Result.success(P_S_O);
            }
        }
        if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
            if ((availableTerms & Globals.SUBJECT_IS_PRESENT) > 0) {
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
        int availableTerms = getAvailableTerms(atom);

        if (availableTerms == 0) {
            Logger.getLogger(Globals.SYSTEM_LOGGER).warning("At least one term must be specified for matching.");
            return Collections.emptyIterator();
        }

        if (availableTerms == (Globals.SUBJECT_IS_PRESENT | Globals.PREDICAT_IS_PRESENT | Globals.OBJECT_IS_PRESENT)) {
            Logger.getLogger(Globals.SYSTEM_LOGGER).warning("All terms are specified; no variables to substitute.");
            return Collections.emptyIterator();
        }

        Result<HexaStoreSearchTree<Integer>> treeResult = selectOptimalSearchTree(availableTerms);
        if (treeResult.failed()) {
            Logger.getLogger(Globals.SYSTEM_LOGGER).warning("Failed to select optimal search tree");
            return Collections.emptyIterator();
        }

        HexaStoreSearchTree<Integer> optimalTree = treeResult.value();

        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) != 0) {
            return matchWithSubject(atom, availableTerms, optimalTree);
        }
        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) != 0) {
            return matchWithPredicate(atom, availableTerms, optimalTree);
        }
        if ((availableTerms & Globals.OBJECT_IS_PRESENT) != 0) {
            return matchWithObject(atom, availableTerms, optimalTree);
        }

        return Collections.emptyIterator();
    }

    private Iterator<Substitution> matchWithKnownTerm(
            int knownTerm,
            Term secondTerm,
            Term thirdTerm,
            int availableTerms,
            int secondFlag,
            int thirdFlag,
            HexaStoreSearchTree<Integer> tree) {

        List<Substitution> res = new ArrayList<>();

        if ((availableTerms & secondFlag) != 0) {

            int convertedSecondTerm = termEncoder.encode(secondTerm);
            addSubstitutions(tree.get(knownTerm).get(convertedSecondTerm), thirdTerm, res);
        } else if ((availableTerms & thirdFlag) != 0) {
            int convertedThirdTerm = termEncoder.encode(thirdTerm);
            addSubstitutions(tree.get(knownTerm).get(convertedThirdTerm), secondTerm, res);
        } else {
            addAllSubstitutions(tree.get(knownTerm), secondTerm, thirdTerm, res);
        }

        return res.iterator();
    }

    private Iterator<Substitution> matchWithSubject(RDFAtom atom, int availableTerms,
            HexaStoreSearchTree<Integer> tree) {
        int convertedSubject = termEncoder.encode(atom.getTripleSubject());
        return matchWithKnownTerm(
                convertedSubject,
                atom.getTripleObject(),
                atom.getTriplePredicate(),
                availableTerms,
                Globals.OBJECT_IS_PRESENT,
                Globals.PREDICAT_IS_PRESENT,
                tree);
    }

    private Iterator<Substitution> matchWithPredicate(RDFAtom atom, int availableTerms,
            HexaStoreSearchTree<Integer> tree) {
        int convertedPredicate = termEncoder.encode(atom.getTriplePredicate());
        return matchWithKnownTerm(
                convertedPredicate,
                atom.getTripleSubject(),
                atom.getTripleObject(),
                availableTerms,
                Globals.SUBJECT_IS_PRESENT,
                Globals.OBJECT_IS_PRESENT,
                tree);
    }

    private Iterator<Substitution> matchWithObject(RDFAtom atom, int availableTerms,
            HexaStoreSearchTree<Integer> tree) {
        int convertedObject = termEncoder.encode(atom.getTripleObject());
        return matchWithKnownTerm(
                convertedObject,
                atom.getTripleSubject(),
                atom.getTriplePredicate(),
                availableTerms,
                Globals.SUBJECT_IS_PRESENT,
                Globals.PREDICAT_IS_PRESENT,
                tree);
    }

    private void addSubstitutions(Set<Integer> encodedValues, Term variable, List<Substitution> res) {
        assert variable.isVariable() : "Term must be a variable";
        Variable castedVariable = (Variable) variable;

        for (Integer encoded : encodedValues) {
            Term decoded = termEncoder.decode(encoded);
            var substitution = new SubstitutionImpl();
            substitution.add(castedVariable, decoded);
            res.add(substitution);
        }
    }

    private void addAllSubstitutions(Map<Integer, Set<Integer>> entries, Term firstVariable, Term secondVariable,
            List<Substitution> res) {
        assert firstVariable.isVariable() : "First term must be a variable";
        assert secondVariable.isVariable() : "Second term must be a variable";

        Variable castedFirstVariable = (Variable) firstVariable;
        Variable castedSecondVariable = (Variable) secondVariable;

        for (Map.Entry<Integer, Set<Integer>> entry : entries.entrySet()) {
            Term decodedFirst = termEncoder.decode(entry.getKey());

            for (Integer secondEncoded : entry.getValue()) {
                Term decodedSecond = termEncoder.decode(secondEncoded);

                // Create a single substitution with both variable mappings
                var substitution = new SubstitutionImpl();
                substitution.add(castedFirstVariable, decodedFirst);
                substitution.add(castedSecondVariable, decodedSecond);
                res.add(substitution);
            }
        }
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
    }

    @Override
    public Collection<Integer> getAtoms() {
        Collection<Integer> res = new ArrayList<>();
        for (var entry : S_P_O.entrySet()) {
            var subject = entry.getKey();
            for (var predicateEntry : entry.getValue().entrySet()) {
                var predicate = predicateEntry.getKey();
                for (var encodedObject : predicateEntry.getValue()) {
                    res.add(subject);
                    res.add(predicate);
                    res.add(encodedObject);
                }
            }
        }
        return res;
    }

}
