package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.TermEncoder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet, Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat) et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {

    private HashMap<String, Integer> convertedTerms;
    private TermEncoder termEncoder;

    private HashMap<Integer, HashMap<Integer, List<Integer>>> S_O_P;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> S_P_O;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> P_S_O;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> P_O_S;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> O_P_S;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> O_S_P;

    private final int SUBJECT_IS_PRESENT = 4;
    private final int PREDICAT_IS_PRESENT = 2;
    private final int OBJECT_IS_PRESENT = 1;

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
            res+= SUBJECT_IS_PRESENT;
        }
        if (!predicate.isVariable()) {
            res+= PREDICAT_IS_PRESENT;
        }
        if (!object.isVariable()) {
            res+= OBJECT_IS_PRESENT;
        }
        return res;
    }

    public HashMap<Integer, HashMap<Integer, List<Integer>>> selectOptimalSearchTree(int state) {

        if ((state | SUBJECT_IS_PRESENT) > 0) {
            if ((state | OBJECT_IS_PRESENT) > 0) {
                return S_O_P;
            } else {
                return S_P_O;
            }
        }
        if ((state | PREDICAT_IS_PRESENT) > 0) {
            if ((state | OBJECT_IS_PRESENT) > 0) {
                return P_O_S;
            } else {
                return P_S_O;
            }
        }
        if ((state | OBJECT_IS_PRESENT) > 0) {
            if ((state | SUBJECT_IS_PRESENT) > 0) {
                return O_S_P;
            } else {
                return O_P_S;
            }
        }
        return null;
    }

    @Override
    public long size() {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<Substitution> match(RDFAtom atom) {

        int state = getAvailableTerms(atom);
        var optimalTree = selectOptimalSearchTree(state);

        List<Substitution> res = new ArrayList<>();

        // S_P_O ou S_O_P //
        if ((state | SUBJECT_IS_PRESENT) > 0) {
            int convertedSubject = convertedTerms.get(atom.getTripleSubject().label());
            if ((state | OBJECT_IS_PRESENT) > 0) {
                int convertedObject = convertedTerms.get(atom.getTripleObject().label());
                for (Integer encodedPredicat : optimalTree.get(convertedSubject).get(convertedObject)) {}
                return res.iterator();
            }
            if ((state | PREDICAT_IS_PRESENT) > 0) {
                int convertedPredicat = convertedTerms.get(atom.getTriplePredicate().label());
                for (Integer encodedObject : optimalTree.get(convertedSubject).get(convertedPredicat)) {}
                return res.iterator();
            }

            var predicats = optimalTree.get(convertedSubject);
            for (var predicat : predicats.entrySet()) {
                for (Integer object : predicat.getValue()) {}
            }
            return res.iterator();
        }
        return null;
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
