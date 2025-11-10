package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.Result;
import qengine.util.SearchTree;
import qengine.util.TermEncoder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet,
 * Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat)
 * et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {

    private HashMap<String, Integer> convertedTerms;
    private TermEncoder termEncoder;

    private SearchTree<Integer> S_O_P;
    private SearchTree<Integer> S_P_O;
    private SearchTree<Integer> P_S_O;
    private SearchTree<Integer> P_O_S;
    private SearchTree<Integer> O_P_S;
    private SearchTree<Integer> O_S_P;

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
        int subject = convertedTerms.get(atom.getTripleSubject().label());
        int predicate = convertedTerms.get(atom.getTriplePredicate().label());
        int object = convertedTerms.get(atom.getTripleObject().label());

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

    public Result<SearchTree<Integer>> selectOptimalSearchTree(int state) {

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
        Result<SearchTree<Integer>> treeResult = selectOptimalSearchTree(state);
        if (treeResult.failed()) {
            Logger.getLogger("system").warning("Failed to select optimal search tree");
            return Collections.emptyIterator();
        }

        SearchTree<Integer> optimalTree = treeResult.value();

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

    private Iterator<Substitution> matchWithSubject(RDFAtom atom, int state, SearchTree<Integer> tree) {
        int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & OBJECT_IS_PRESENT) != 0) {
            int convertedObject = convertedTerms.get(atom.getTripleObject().label());
            addPredicateSubstitutions(tree.get(convertedSubject).get(convertedObject), atom.getTriplePredicate(), res);
        } else if ((state & PREDICAT_IS_PRESENT) != 0) {
            int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
            addObjectSubstitutions(tree.get(convertedSubject).get(convertedPredicate), atom.getTripleObject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedSubject), atom.getTriplePredicate(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }

    private Iterator<Substitution> matchWithPredicate(RDFAtom atom, int state, SearchTree<Integer> tree) {
        int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & SUBJECT_IS_PRESENT) != 0) {
            int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
            addObjectSubstitutions(tree.get(convertedPredicate).get(convertedSubject), atom.getTripleObject(), res);
        } else if ((state & OBJECT_IS_PRESENT) != 0) {
            int convertedObject = convertedTerms.get(atom.getTripleObject().label());
            addSubjectSubstitutions(tree.get(convertedPredicate).get(convertedObject), atom.getTripleSubject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedPredicate), atom.getTripleSubject(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }

    private Iterator<Substitution> matchWithObject(RDFAtom atom, int state, SearchTree<Integer> tree) {
        int convertedObject = convertedTerms.get(atom.getTripleObject().label());
        List<Substitution> res = new ArrayList<>();

        if ((state & SUBJECT_IS_PRESENT) != 0) {
            int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
            addPredicateSubstitutions(tree.get(convertedObject).get(convertedSubject), atom.getTriplePredicate(), res);
        } else if ((state & PREDICAT_IS_PRESENT) != 0) {
            int convertedPredicate = convertedTerms.get(atom.getTriplePredicate().label());
            addSubjectSubstitutions(tree.get(convertedObject).get(convertedPredicate), atom.getTripleSubject(), res);
        } else {
            addAllSubstitutions(tree.get(convertedObject), atom.getTriplePredicate(), atom.getTripleObject(), res);
        }

        return res.iterator();
    }

    private void addPredicateSubstitutions(List<Integer> encodedValues, Term variable, List<Substitution> res) {
        for (Integer encoded : encodedValues) {
            String decoded = termEncoder.decode(encoded);
            res.add(new Substitution(new Term(decoded), variable));
        }
    }

    private void addObjectSubstitutions(List<Integer> encodedValues, Term variable, List<Substitution> res) {
        for (Integer encoded : encodedValues) {
            String decoded = termEncoder.decode(encoded);
            res.add(new Substitution(new Term(decoded), variable));
        }
    }

    private void addSubjectSubstitutions(List<Integer> encodedValues, Term variable, List<Substitution> res) {
        for (Integer encoded : encodedValues) {
            String decoded = termEncoder.decode(encoded);
            res.add(new Substitution(new Term(decoded), variable));
        }
    }

    private void addAllSubstitutions(Map<Integer, List<Integer>> entries, Term firstVariable, Term secondVariable,
            List<Substitution> res) {
        for (Map.Entry<Integer, List<Integer>> entry : entries.entrySet()) {
            String decodedFirst = termEncoder.decode(entry.getKey());
            res.add(new Substitution(new Term(decodedFirst), firstVariable));
            for (Integer secondEncoded : entry.getValue()) {
                String decodedSecond = termEncoder.decode(secondEncoded);
                res.add(new Substitution(new Term(decodedSecond), secondVariable));
            }
        }
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
    }

    @Override
    public Collection<Atom> getAtoms() {
        throw new NotImplementedException();
    }
}
