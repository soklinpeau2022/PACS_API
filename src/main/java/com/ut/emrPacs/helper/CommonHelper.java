package com.ut.emrPacs.helper;

import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared helper for service-layer relation maintenance.
 *
 * <p>String, number and date utilities live in their dedicated helpers
 * ({@link FunctionHelper}, {@link com.ut.emrPacs.helper.date.FormatDateHelper})
 * to avoid duplicate implementations.</p>
 */
@Service
public class CommonHelper {

    /**
     * Replaces a set of relation rows: deletes the existing rows, then inserts every
     * item that passes {@code isValidItem}.
     *
     * <p>No-ops when any required argument is {@code null}. {@code null} items and items
     * rejected by {@code isValidItem} are skipped.</p>
     *
     * @param deleteAction removes the current relation rows
     * @param items        candidate items to insert
     * @param isValidItem  filter applied to each item; {@code null} means accept all
     * @param insertAction inserts a single accepted item
     */
    public <T> void replaceRelations(Runnable deleteAction, Iterable<T> items,
                                     Predicate<T> isValidItem, Consumer<T> insertAction) {
        if (deleteAction == null || insertAction == null || items == null) {
            return;
        }
        deleteAction.run();

        for (T item : items) {
            if (item == null) {
                continue;
            }
            if (isValidItem != null && !isValidItem.test(item)) {
                continue;
            }
            insertAction.accept(item);
        }
    }
}
