package com.ut.emrPacs.helper.pagination;

import java.util.Collections;
import java.util.List;

import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.filter.FilterBase;

/**
 * Shared pagination helper for MyBatis queries.
 *
 * <p>Mapper XML files bind {@code page} as SQL <b>offset</b>. This helper keeps
 * the API contract as 1-based page numbers, then converts the filter's
 * {@code page} to 0-based offset for the query.</p>
 */
public final class PaginationHelper {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_ROWS_PER_PAGE = 10;
    /** Maximum page number accepted before falling back to default. */
    private static final int MAX_PAGE = 100000;
    /** Maximum page size accepted before falling back to default. */
    private static final int MAX_ROWS_PER_PAGE = 100;
    /**
     * Hard ceiling on the SQL OFFSET an offset-paged query may use. Realistic UI
     * paging never approaches this; it only bounds the worst-case sequential scan
     * on very large tables. Legitimate deep paging must use the keyset cursors
     * (lastWorklistId / lastStudyId / lastPatientId / lastActivityId /
     * lastUserLogId) which avoid OFFSET entirely.
     */
    private static final int MAX_OFFSET = 200000;

    private PaginationHelper() {
    }

    /**
     * Like {@link #buildAndApplyOffset(FilterBase)} but applies default page/rows when missing or invalid.
     */
    public static Pagination buildAndApplyOffsetOrDefault(FilterBase filter) {
        return buildAndApplyOffsetOrDefault(filter, null, DEFAULT_PAGE, DEFAULT_ROWS_PER_PAGE);
    }

    /**
     * Like {@link #buildAndApplyOffset(FilterBase, Long)} but applies default page/rows when missing or invalid.
     */
    public static Pagination buildAndApplyOffsetOrDefault(FilterBase filter, Long total) {
        return buildAndApplyOffsetOrDefault(filter, total, DEFAULT_PAGE, DEFAULT_ROWS_PER_PAGE);
    }

    /**
     * Like {@link #buildAndApplyOffset(FilterBase, Long)} but applies default page/rows when missing or invalid.
     */
    public static Pagination buildAndApplyOffsetOrDefault(FilterBase filter, Long total, int defaultPage, int defaultRowsPerPage) {
        if (filter == null) {
            Pagination pagination = new Pagination();
            if (total != null) pagination.setTotal(total);
            return pagination;
        }

        Integer requestedPage = filter.getPage();
        Integer requestedRowsPerPage = filter.getRowsPerPage();
        filter.setPage(normalizePage(requestedPage, defaultPage));
        filter.setRowsPerPage(normalizeRowsPerPage(requestedRowsPerPage, defaultRowsPerPage));

        return buildAndApplyOffset(filter, total);
    }

    /**
     * Builds pagination metadata without applying SQL offsets.
     */
    public static Pagination buildMetadata(FilterBase filter, Long total) {
        Pagination pagination = new Pagination();

        if (filter == null) {
            if (total != null) pagination.setTotal(total);
            return pagination;
        }

        pagination.setPage(filter.getPage());
        pagination.setRowsPerPage(filter.getRowsPerPage());
        if (total != null) pagination.setTotal(total);

        return pagination;
    }

    /**
     * Builds pagination metadata (in-memory pagination) applying default page/rows when missing or invalid.
     */
    public static Pagination buildMetadataOrDefault(FilterBase filter, long total) {
        return buildMetadataOrDefault(filter, total, DEFAULT_PAGE, DEFAULT_ROWS_PER_PAGE);
    }

    /**
     * Builds pagination metadata (in-memory pagination) applying default page/rows when missing or invalid.
     */
    public static Pagination buildMetadataOrDefault(FilterBase filter, long total, int defaultPage, int defaultRowsPerPage) {
        int page = defaultPage;
        int rowsPerPage = defaultRowsPerPage;

        if (filter != null) {
            Integer requestedPage = filter.getPage();
            Integer requestedRowsPerPage = filter.getRowsPerPage();

            page = normalizePage(requestedPage, defaultPage);
            rowsPerPage = normalizeRowsPerPage(requestedRowsPerPage, defaultRowsPerPage);
        }

        Pagination pagination = new Pagination();
        pagination.setPage(page);
        pagination.setRowsPerPage(rowsPerPage);
        pagination.setTotal(total);

        return pagination;
    }

    /**
     * Returns a paginated slice of {@code items} (in-memory) based on {@link Pagination}.
     */
    public static <T> List<T> slice(List<T> items, Pagination pagination) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        if (pagination == null || pagination.getPage() == null || pagination.getRowsPerPage() == null) return items;

        int page = pagination.getPage();
        int rowsPerPage = pagination.getRowsPerPage();

        if (page <= 0 || rowsPerPage <= 0) return items;

        int fromIndex = Math.max(0, (page - 1) * rowsPerPage);
        if (fromIndex >= items.size()) return Collections.emptyList();

        int toIndex = Math.min(fromIndex + rowsPerPage, items.size());
        return items.subList(fromIndex, toIndex);
    }

    public static Pagination buildAndApplyOffset(FilterBase filter) {
        return buildAndApplyOffset(filter, null);
    }

    public static Pagination buildAndApplyOffset(FilterBase filter, Long total) {
        Pagination pagination = new Pagination();

        if (filter == null) {
            if (total != null) pagination.setTotal(total);
            return pagination;
        }

        Integer requestedPage = filter.getPage();
        Integer requestedRowsPerPage = filter.getRowsPerPage();

        // Keep original values when paging isn't provided (so XML can skip LIMIT).
        pagination.setPage(requestedPage);
        pagination.setRowsPerPage(requestedRowsPerPage);
        if (total != null) pagination.setTotal(total);

        int page = normalizePage(requestedPage, DEFAULT_PAGE);
        int rowsPerPage = normalizeRowsPerPage(requestedRowsPerPage, DEFAULT_ROWS_PER_PAGE);

        pagination.setPage(page);
        pagination.setRowsPerPage(rowsPerPage);

        int page0 = Math.max(0, page - 1);
        long rawOffset = (long) page0 * rowsPerPage;
        int offset = rawOffset > MAX_OFFSET ? MAX_OFFSET : (int) rawOffset; // bound worst-case scan; deep paging should use keyset cursors
        filter.setPage(offset);
        filter.setRowsPerPage(rowsPerPage);

        return pagination;
    }

    private static int normalizePage(Integer requestedPage, int defaultPage) {
        if (requestedPage == null || requestedPage <= 0 || requestedPage > MAX_PAGE) {
            return defaultPage;
        }
        return requestedPage;
    }

    private static int normalizeRowsPerPage(Integer requestedRowsPerPage, int defaultRowsPerPage) {
        if (requestedRowsPerPage == null || requestedRowsPerPage <= 0 || requestedRowsPerPage > MAX_ROWS_PER_PAGE) {
            return defaultRowsPerPage;
        }
        return requestedRowsPerPage;
    }
}
