package com.loja.useraccount.adapter.out.persistence.strategy;

import com.loja.useraccount.application.dto.UserSearchCriteria;
import jakarta.persistence.TypedQuery;

public interface CriteriaStrategy {
    boolean supports(UserSearchCriteria criteria);
    String conditionFragment();
    <T> void applyParameter(TypedQuery<T> query, UserSearchCriteria criteria);
}
