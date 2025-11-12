package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.impl.AtomImpl;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;

import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.Result;
import qengine.util.HexaStoreSearchTree;
import qengine.util.TermEncoder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Array;
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
public class RDFBigTableStore implements RDFStorage {

    private final List<RDFAtom> rdfAtoms = new ArrayList<>();

    private static final int SUBJECT_IS_PRESENT = 4;
    private static final int PREDICAT_IS_PRESENT = 2;
    private static final int OBJECT_IS_PRESENT = 1;

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

    public void loadPersistentData(String path) throws FileNotFoundException {
        FileReader rdfFile = new FileReader(path);

        try (RDFAtomParser rdfAtomParser = new RDFAtomParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (rdfAtomParser.hasNext()) {
                RDFAtom atom = rdfAtomParser.next();
                add(atom);
            }

        }
    }

    @Override
    public boolean add(RDFAtom atom) {
        rdfAtoms.add(atom);
        return true;
    }

    @Override
    public long size() {
        return rdfAtoms.size();
    }

    @Override
    public Iterator<Substitution> match(RDFAtom atom) {
        return new Iterator<Substitution>() {
            private final RDFAtom target = atom;
            private final int availableTerms = getAvailableTerms(atom);
            private final List<RDFAtom> atoms = rdfAtoms;
            private int lastMatchedAtomIndex = -1;

            @Override
            public boolean hasNext() {
                return lastMatchedAtomIndex + 1 < atoms.size();
            }

            @Override
            public Substitution next() {
                while (hasNext()) {
                    var res = new SubstitutionImpl();
                    RDFAtom next = atoms.get(++lastMatchedAtomIndex);

                    if ((availableTerms | SUBJECT_IS_PRESENT) > 0) {
                        if (!target.getTripleSubject().equals(next.getTripleSubject())) {
                            continue;
                        }
                    } else {
                        Variable subjectVar = (Variable) target.getTripleSubject();
                        var nextSubjectVar = next.getTripleSubject();
                        res.add(subjectVar, nextSubjectVar);
                    }

                    if ((availableTerms | PREDICAT_IS_PRESENT) > 0) {
                        if (!target.getTriplePredicate().equals(next.getTriplePredicate())) {
                            continue;
                        }
                    } else {
                        Variable predicateVar = (Variable) target.getTriplePredicate();
                        var nextPredicateVar = next.getTriplePredicate();
                        res.add(predicateVar, nextPredicateVar);
                    }

                    if ((availableTerms | OBJECT_IS_PRESENT) > 0) {
                        if (!target.getTripleObject().equals(next.getTripleObject())) {
                            continue;
                        }
                    } else {
                        Variable objectVar = (Variable) target.getTripleObject();
                        var nextObjectVar = next.getTripleObject();
                        res.add(objectVar, nextObjectVar);
                    }
                    return res;
                }
                throw new NoSuchElementException();
            }

        };
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
    }

    @Override
    public Collection<Atom> getAtoms() {
        return Collections.unmodifiableCollection(rdfAtoms);
    }

}
