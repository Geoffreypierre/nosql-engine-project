package qengine.storage;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.rio.RDFFormat;

import fr.boreal.model.logicalElements.api.Atom;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import qengine.model.RDFAtom;
import qengine.model.StarQuery;
import qengine.parser.RDFAtomParser;
import qengine.util.BigTableMatchIterator;
import qengine.util.Globals;

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
        return new BigTableMatchIterator(atom, rdfAtoms, getAvailableTerms(atom));
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
