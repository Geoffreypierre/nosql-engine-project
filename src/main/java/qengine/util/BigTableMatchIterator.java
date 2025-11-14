
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

    private final int encodedObjectTarget;
    private final int encodedSubjectTarget;
    private final int encodedPredicateTarget;

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

        if ((availableTerms & Globals.OBJECT_IS_PRESENT) > 0) {
            this.encodedObjectTarget = termEncoder.encode(target.getTripleObject());
        } else {
            this.encodedObjectTarget = -1;
        }
        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) > 0) {
            this.encodedSubjectTarget = termEncoder.encode(target.getTripleSubject());
        } else {
            this.encodedSubjectTarget = -1;
        }
        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) > 0) {
            this.encodedPredicateTarget = termEncoder.encode(target.getTriplePredicate());
        } else {
            this.encodedPredicateTarget = -1;
        }

    }

    private boolean matchesAtom(int next) {
        return matchesTerm(Globals.SUBJECT_IS_PRESENT, encodedSubjectTarget,
                rdfAtomsSubject.get(next))
                && matchesTerm(Globals.PREDICAT_IS_PRESENT, encodedPredicateTarget,
                        rdfAtomsPredicate.get(next))
                && matchesTerm(Globals.OBJECT_IS_PRESENT, encodedObjectTarget,
                        rdfAtomsObject.get(next));
    }

    private boolean matchesTerm(int termFlag, int encodedTarget, int encodedActual) {
        if ((availableTerms & termFlag) > 0) {
            return encodedTarget == encodedActual;
        }
        return true;
    }

    private void fillSubstitution(int encodedActual,
            Term targetTerm, SubstitutionImpl res) {

        Variable variable = (Variable) targetTerm;
        Term decodedTerm = termEncoder.decode(encodedActual);
        res.add(variable, decodedTerm);

    }

    private Substitution fillSubtitutions(int next) {
        var res = new SubstitutionImpl();
        if ((availableTerms & Globals.SUBJECT_IS_PRESENT) <= 0) {
            fillSubstitution(rdfAtomsSubject.get(next),
                    target.getTripleSubject(), res);
        }
        if ((availableTerms & Globals.PREDICAT_IS_PRESENT) <= 0) {
            fillSubstitution(rdfAtomsPredicate.get(next),
                    target.getTriplePredicate(), res);
        }
        if ((availableTerms & Globals.OBJECT_IS_PRESENT) <= 0) {
            fillSubstitution(rdfAtomsObject.get(next),
                    target.getTripleObject(), res);
        }
        return res;
    }

    @Override
    public boolean hasNext() {
        return lastMatchedAtomIndex + 1 < rdfAtomsSubject.size();
    }

    @Override
    public Substitution next() {

        while (hasNext()) {
            int next = ++lastMatchedAtomIndex;
            if (matchesAtom(next)) {
                return fillSubtitutions(next);
            }
        }
        throw new NoSuchElementException();
    }
}
