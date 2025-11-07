package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import org.apache.commons.collections.set.ListOrderedSet;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.TermEncoder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet, Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat) et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {

    private HashMap<String, Integer> convertedTerms;
    private TermEncoder termEncoder;

    private HashMap<Integer, List<Integer>> S_O_P;
    private HashMap<Integer, List<Integer>> S_P_O;
    private HashMap<Integer, List<Integer>> P_S_O;
    private HashMap<Integer, List<Integer>> P_O_S;
    private HashMap<Integer, List<Integer>> O_P_S;
    private HashMap<Integer, List<Integer>> O_S_P;

    private void loadTerm(Set<String> rawTerms) {
        for (String rawTerm : rawTerms) {
            convertedTerms.put(rawTerm, termEncoder.encode(rawTerm));
        }
    }

    public void loadPersistentData(String path) throws FileNotFoundException {
        FileReader rdfFile = new FileReader(path);
        Set<String> terms = new HashSet<>();

        try (RDFAtomParser rdfAtomParser = new RDFAtomParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (rdfAtomParser.hasNext()) {
                RDFAtom atom = rdfAtomParser.next();
                terms.add(atom.getTripleSubject().label());
                terms.add(atom.getTriplePredicate().label());
                terms.add(atom.getTripleObject().label());
            }
            loadTerm(terms);
        }
    }

    private void searchTrees(Set<RDFAtom> atoms) {

    }

    @Override
    public boolean add(RDFAtom atom) {
        throw new NotImplementedException();
    }

    @Override
    public long size() {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<Substitution> match(RDFAtom atom) {
        throw new NotImplementedException();
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
