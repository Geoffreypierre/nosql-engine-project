
package qengine.util;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFAtom;

public class BigTableMatchIterator implements Iterator<Substitution> {
    private final RDFAtom target;
    private final int availableTerms;
    private final List<RDFAtom> atoms;
    private int lastMatchedAtomIndex = -1;


    public BigTableMatchIterator(RDFAtom target, List<RDFAtom> atoms, int availableTerms) {
        this.target = target;
        this.atoms = atoms;
        this.availableTerms = availableTerms;
    }

    @Override
    public boolean hasNext() {
        return lastMatchedAtomIndex + 1 < atoms.size();
    }

    private boolean matchesAtom(RDFAtom next, SubstitutionImpl res) {
        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) > 0) {
            if (!target.getTripleSubject().equals(next.getTripleSubject())) {
                return false;
            }
        } else {
            Variable subjectVar = (Variable) target.getTripleSubject();
            var nextSubjectVar = next.getTripleSubject();
            res.add(subjectVar, nextSubjectVar);
        }

        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) > 0) {
            if (!target.getTriplePredicate().equals(next.getTriplePredicate())) {
                return false;
            }
        } else {
            Variable predicateVar = (Variable) target.getTriplePredicate();
            var nextPredicateVar = next.getTriplePredicate();
            res.add(predicateVar, nextPredicateVar);
        }

        if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
            if (!target.getTripleObject().equals(next.getTripleObject())) {
                return false;
            }
        } else {
            Variable objectVar = (Variable) target.getTripleObject();
            var nextObjectVar = next.getTripleObject();
            res.add(objectVar, nextObjectVar);
        }
        return true;
    }

    @Override
    public Substitution next() {
        while (hasNext()) {
            var res = new SubstitutionImpl();
            RDFAtom next = atoms.get(++lastMatchedAtomIndex);

            if (matchesAtom(next, res)) {
                return res;
            }
        }
        throw new NoSuchElementException();
    }
}
