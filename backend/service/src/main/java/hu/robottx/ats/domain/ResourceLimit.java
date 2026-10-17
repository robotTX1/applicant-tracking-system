package hu.robottx.ats.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResourceLimit {
    MAX_COMPANIES(3);

    private final int limit;

}
