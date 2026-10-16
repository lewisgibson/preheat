package com.lewisgibson.preheat.ui;

import java.util.function.Consumer;

/** Hands on the first answer only: a button pressed after Esc, or a second press, changes nothing. */
final class OneAnswer<T> implements Consumer<T> {
    private final Consumer<T> answer;
    private boolean answered;

    OneAnswer(Consumer<T> answer) {
        this.answer = answer;
    }

    @Override
    public void accept(T choice) {
        if (!answered) {
            answered = true;
            answer.accept(choice);
        }
    }
}
