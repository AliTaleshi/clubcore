package ir.clubcore.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Paging {

    private Paging() {
    }

    /** Clamps client-supplied paging so bad values never reach Spring Data (which throws on them). */
    public static PageRequest of(int page, int size, int maxSize, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, maxSize)), sort);
    }
}
