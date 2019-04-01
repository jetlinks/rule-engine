package org.jetlinks.rule.engine.singleton;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class MultiException extends RuntimeException {

    private List<Throwable> errors;

    public MultiException(List<Throwable> errors) {
        super(errors.get(0));
        this.errors = errors;
    }

    @Override
    public void printStackTrace() {
        errors.forEach(Throwable::printStackTrace);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        errors.forEach(err -> err.printStackTrace(s));
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        errors.forEach(err -> err.printStackTrace(s));
    }
}
