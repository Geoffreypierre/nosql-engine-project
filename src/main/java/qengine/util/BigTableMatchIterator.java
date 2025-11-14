
package qengine.util;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFAtom;

public class BigTableMatchIterator implements Iterator<Substitution> {
    private final RDFAtom target;
    private final int availableTerms;
    private final List<Integer> rdfAtomsSubject;
    private final List<Integer> rdfAtomsPredicate;
    private final List<Integer> rdfAtomsObject;
    private TermEncoder termEncoder;
    private int lastMatchedAtomIndex = -1;


    public BigTableMatchIterator(RDFAtom target, int availableTerms,
                                 TermEncoder termEncoder,
                                 List<Integer> rdfAtomsSubject,
                                 List<Integer> rdfAtomsPredicate,
                                 List<Integer> rdfAtomsObject) {
        this.target = target;
        this.availableTerms = availableTerms;
        this.termEncoder = termEncoder;
        this.rdfAtomsSubject = rdfAtomsSubject;
        this.rdfAtomsPredicate = rdfAtomsPredicate;
        this.rdfAtomsObject = rdfAtomsObject;
    }

    @Override
    public boolean hasNext() {
        return lastMatchedAtomIndex + 1 < rdfAtomsSubject.size();
    }

    private boolean matchesAtom(int next, SubstitutionImpl res) {
        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) > 0) {
            if (!target.getTripleSubject().equals(rdfAtomsSubject.get(next))) {
                return false;
            }
        } else {
            Variable subjectVar = (Variable) target.getTripleSubject();
            var nextSubjectVar = termEncoder.decode(rdfAtomsSubject.get(next));
            res.add(subjectVar, nextSubjectVar);
        }

        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) > 0) {
            if (!target.getTriplePredicate().equals(rdfAtomsPredicate.get(next))) {
                return false;
            }
        } else {
            Variable predicateVar = (Variable) target.getTriplePredicate();
            var nextPredicateVar = termEncoder.decode(rdfAtomsPredicate.get(next));
            res.add(predicateVar, nextPredicateVar);
        }

        if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
            if (!target.getTripleObject().equals(rdfAtomsObject.get(next))) {
                return false;
            }
        } else {
            Variable objectVar = (Variable) target.getTripleObject();
            var nextObjectVar = termEncoder.decode(rdfAtomsObject.get(next));
            res.add(objectVar, nextObjectVar);
        }
        return true;
    }

    @Override
    public Substitution next() {
        while (hasNext()) {
            var res = new SubstitutionImpl();
            int next = ++lastMatchedAtomIndex;
            if (matchesAtom(next, res)) {
                return res;
            }
        }
        throw new NoSuchElementException();
    }
}
